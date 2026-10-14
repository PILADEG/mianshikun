package com.kun.mianshikun.manager;

import com.alibaba.nacos.api.annotation.NacosInjected;
import com.alibaba.nacos.api.config.ConfigService;
import com.alibaba.nacos.api.config.ConfigType;
import com.kun.mianshikun.blackfilter.BlackListUtils;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class BlackListManager {

    private static final String SYNC_LOCK_KEY = "lock:blacklist:nacos:sync";
    private static final int PUBLISH_RETRY = 3;

    private final Set<String> localDynamic = ConcurrentHashMap.newKeySet();

    private final Set<String> pendingSync = ConcurrentHashMap.newKeySet();

    @NacosInjected
    private ConfigService configService;

    @Value("${nacos.config.data-id}")
    private String dataId;

    @Value("${nacos.config.group}")
    private String group;

    @Resource
    private RedissonClient redissonClient;

    public void banIp(String ip) {
        if (StringUtils.isBlank(ip)) {
            return;
        }
        String target = ip.trim();
        boolean added = localDynamic.add(target);
        pendingSync.add(target);
        if (added) {
            log.warn("IP 已加入本实例黑名单（待同步 Nacos）：{}，本地黑名单共 {} 条", target, localDynamic.size());
        }
    }

    public boolean isBlack(String ip) {
        if (StringUtils.isBlank(ip)) {
            return false;
        }
        return localDynamic.contains(ip) || BlackListUtils.isBlack(ip);
    }

    public int localCount() {
        return localDynamic.size();
    }

    public int pendingCount() {
        return pendingSync.size();
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduledFlush() {
        flushNow();
    }

    public boolean flushNow() {
        if (pendingSync.isEmpty()) {
            return true;
        }
        RLock lock = redissonClient.getLock(SYNC_LOCK_KEY);
        boolean locked = false;
        Set<String> batch = null;
        try {
            locked = lock.tryLock(3, 15, TimeUnit.SECONDS);
            if (!locked) {
                log.info("黑名单同步：未获取到分布式锁，本轮跳过");
                return false;
            }
            batch = new HashSet<>(pendingSync);
            pendingSync.removeAll(batch);
            if (batch.isEmpty()) {
                return true;
            }
            boolean ok = publishBatch(batch);
            if (!ok) {
                pendingSync.addAll(batch);
                log.error("黑名单同步 Nacos 失败，{} 条已回滚等待重试", batch.size());
            }
            return ok;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            if (batch != null) {
                pendingSync.addAll(batch);
            }
            return false;
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    private boolean publishBatch(Set<String> batch) {
        for (int attempt = 1; attempt <= PUBLISH_RETRY; attempt++) {
            try {
                String old = configService.getConfig(dataId, group, 3000);
                List<String> merged = new ArrayList<>(BlackListUtils.parseIpList(old));
                merged.addAll(batch);
                List<String> distinct = merged.stream()
                        .filter(StringUtils::isNotBlank)
                        .distinct()
                        .collect(Collectors.toList());
                String content = BlackListUtils.toYaml(distinct);
                boolean ok = configService.publishConfig(dataId, group, content,
                        ConfigType.YAML.getType());
                if (ok) {
                    log.info("黑名单同步 Nacos 成功：本次 {} 条，配置累计 {} 条", batch.size(), distinct.size());
                    return true;
                }
                log.warn("黑名单同步 Nacos 未成功（第 {} 次尝试）", attempt);
            } catch (Exception e) {
                log.warn("黑名单同步 Nacos 异常（第 {} 次尝试）：{}", attempt, e.getMessage());
            }
            sleepQuietly(300L * attempt);
        }
        return false;
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
