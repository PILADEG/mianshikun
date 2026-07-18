package com.kun.mianshikun.aop;

import com.jd.platform.hotkey.client.callback.JdHotKeyStore;
import com.kun.mianshikun.annotation.HotKeyCache;
import com.kun.mianshikun.util.SpelUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
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
        String key = SpelUtils.parseKey(joinPoint, hotKeyCache.key());
        String prefix = hotKeyCache.prefix();
        if (key == null) {
            return joinPoint.proceed();
        }
        String fullKey = prefix + key;

        // 1. 热 key → 从 Redis 取缓存，命中则跳过方法执行
        if (JdHotKeyStore.isHotKey(key)) {
            RBucket<Object> bucket = redissonClient.getBucket(fullKey);
            Object cached = bucket.get();
            log.info("cached:{}", cached);
            if (cached != null) {
                log.info("HotKey redis hit, key={}", fullKey);
                return cached;
            }
        }

        // 2. 执行原方法
        Object result = joinPoint.proceed();

        // 3. 若已是热 key，结果存入 Redis
        if (JdHotKeyStore.isHotKey(key) && result != null) {
            redissonClient.getBucket(fullKey).set(result,10, TimeUnit.MINUTES);
        }

        return result;
    }
}
