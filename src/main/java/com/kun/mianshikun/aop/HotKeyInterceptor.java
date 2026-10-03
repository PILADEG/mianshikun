package com.kun.mianshikun.aop;

import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.kun.mianshikun.annotation.HotKeyCache;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.RedisConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.model.entity.Hotkey;
import com.kun.mianshikun.util.SpelUtils;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Duration;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Slf4j
@Aspect
@Component
public class HotKeyInterceptor {
    @Resource
    private RedissonClient redissonClient;

    @Around("@annotation(hotKeyCache)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, HotKeyCache hotKeyCache)
            throws Throwable {
        String key = SpelUtils.resolve(joinPoint, hotKeyCache.key());
        if (key == null) {
            return joinPoint.proceed();
        }
        String fullKey = hotKeyCache.prefix() + key;
        String hotKey = hotKeyCache.hotkeyPrefix() + key;
        // 1. 热 key → 走逻辑过期缓存
        if (JdHotKeyStore.isHotKey(hotKey)) {
            return readThrough(joinPoint, hotKeyCache, key);
        }

        // 2. 执行原方法
        Object result = joinPoint.proceed();

        // 3. 请求期间刚被判为热 key，顺带写入缓存
        if (JdHotKeyStore.isHotKey(hotKey)) {
            writeCache(redissonClient.getBucket(fullKey), hotKeyCache, result);
        }

        return result;
    }

    private Object readThrough(ProceedingJoinPoint joinPoint, HotKeyCache hotKeyCache,
                               String key) throws Throwable {
        String fullKey = hotKeyCache.prefix() + key;
        RBucket<Object> bucket = redissonClient.getBucket(fullKey);
        Hotkey<?> cached;
        try {
            cached = (Hotkey<?>) bucket.get();
        } catch (Exception e) {
            log.warn("hotkey_cache|event=read_fail|key={}|msg={}", fullKey, e.getMessage());
            return joinPoint.proceed();
        }
        if (cached == null) {
            return coldStart(joinPoint, bucket, hotKeyCache, key, fullKey);
        }
        Long logicExpire = cached.getLogicExpire();
        if (logicExpire != null && System.currentTimeMillis() >= logicExpire) {
            return rebuild(joinPoint, bucket, hotKeyCache, key, fullKey, cached);
        }
        log.info("hotkey_cache|event=hit|key={}", fullKey);
        return unwrap(cached.getContent());
    }

    private Object coldStart(ProceedingJoinPoint joinPoint, RBucket<Object> bucket,
                             HotKeyCache hotKeyCache, String key, String fullKey) throws Throwable {
        RLock lock = redissonClient.getLock(RedisConstant.getHotkeyLock(hotKeyCache.module(),key));
        boolean locked = false;
        try {
            locked = lock.tryLock(hotKeyCache.lockWaitMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("hotkey_cache|event=lock_fail|key={}|msg={}", fullKey, e.getMessage());
        }
        if (!locked) {
            Hotkey<?> again = readQuietly(bucket);
            if (again != null) {
                return unwrap(again.getContent());
            }
            log.info("hotkey_cache|event=cold_start_fallback|key={}", fullKey);
            return joinPoint.proceed();
        }
        try {
            Hotkey<?> again = readQuietly(bucket);
            if (again != null) {
                return unwrap(again.getContent());
            }
            log.info("hotkey_cache|event=cold_start|key={}", fullKey);
            return loadAndWrite(joinPoint, bucket, hotKeyCache, fullKey);
        } finally {
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    private Object rebuild(ProceedingJoinPoint joinPoint, RBucket<Object> bucket,
                           HotKeyCache hotKeyCache, String key, String fullKey, Hotkey<?> stale)
            throws Throwable {
        RLock lock = redissonClient.getLock(RedisConstant.getHotkeyLock(hotKeyCache.module(),key));
        boolean locked = false;
        try {
            locked = lock.tryLock();
        } catch (Exception e) {
            log.warn("hotkey_cache|event=lock_fail|key={}|msg={}", fullKey, e.getMessage());
        }
        if (!locked) {
            log.info("hotkey_cache|event=stale|key={}|reason=locked", fullKey);
            return unwrap(stale.getContent());
        }
        try {
            // 双重检查：可能刚被别人重建完并释放了锁
            Hotkey<?> again = readQuietly(bucket);
            if (again != null) {
                Long expire = again.getLogicExpire();
                if (expire != null && System.currentTimeMillis() < expire) {
                    return unwrap(again.getContent());
                }
            }
            Object fresh = joinPoint.proceed();
            writeCache(bucket, hotKeyCache, fresh);
            log.info("hotkey_cache|event=rebuilt|key={}", fullKey);
            return fresh;
        } catch (BusinessException e) {
            if (hotKeyCache.cacheNull() && e.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()) {
                writeCache(bucket, hotKeyCache, null);
            }
            throw e;
        } catch (Exception e) {
            // 重建失败：保留旧值并退避，不能把错误抛给用户，也不能让每次读都重试
            backoff(bucket, hotKeyCache, stale, fullKey, e);
            if (stale.getContent() == null) {
                throw e;
            }
            return unwrap(stale.getContent());
        } finally {
            if (lock.isHeldByCurrentThread()){
                lock.unlock();
            }
        }
    }

    private Object loadAndWrite(ProceedingJoinPoint joinPoint, RBucket<Object> bucket,
                                HotKeyCache hotKeyCache, String fullKey) throws Throwable {
        Object result;
        try {
            result = joinPoint.proceed();
        } catch (BusinessException e) {
            if (hotKeyCache.cacheNull() && e.getCode() == ErrorCode.NOT_FOUND_ERROR.getCode()) {
                writeCache(bucket, hotKeyCache, null);
            }
            throw e;
        }
        writeCache(bucket, hotKeyCache, result);
        return result;
    }

    private void writeCache(RBucket<Object> bucket, HotKeyCache hotKeyCache, Object content) {
        if (content == null && !hotKeyCache.cacheNull()) {
            return;
        }
        long logicTtlSeconds = content == null
                ? hotKeyCache.nullTtlSeconds()
                : hotKeyCache.logicTtlSeconds();
        Hotkey<Object> entry = new Hotkey<>();
        entry.setLogicExpire(System.currentTimeMillis() + logicTtlSeconds * 1000);
        entry.setContent(content);
        long physicalTtl = logicTtlSeconds * 3 + ThreadLocalRandom.current().nextLong(logicTtlSeconds);
        try {
            bucket.set(entry, Duration.ofSeconds(physicalTtl));
        } catch (Exception e) {
            log.warn("hotkey_cache|event=write_fail|key={}|msg={}", bucket.getName(), e.getMessage());
        }
    }

    private void backoff(RBucket<Object> bucket, HotKeyCache hotKeyCache, Hotkey<?> stale,
                         String fullKey, Exception e) {
        log.error("hotkey_cache|event=rebuild_fail|key={}|msg={}", fullKey, e.getMessage(), e);
        long backoffSeconds = hotKeyCache.retryBackoffSeconds();
        Hotkey<Object> fallback = new Hotkey<>();
        fallback.setLogicExpire(System.currentTimeMillis() + backoffSeconds * 1000);
        fallback.setContent(stale.getContent());
        long physicalTtl = backoffSeconds * 3 + ThreadLocalRandom.current().nextLong(backoffSeconds);
        try {
            bucket.set(fallback, Duration.ofSeconds(physicalTtl));
        } catch (Exception ignored) {
        }
    }

    private Hotkey<?> readQuietly(RBucket<Object> bucket) {
        try {
            return (Hotkey<?>) bucket.get();
        } catch (Exception e) {
            log.warn("hotkey_cache|event=read_fail|key={}|msg={}", bucket.getName(), e.getMessage());
            return null;
        }
    }

    private Object unwrap(Object content) {
        if (content == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR);
        }
        return content;
    }
}
