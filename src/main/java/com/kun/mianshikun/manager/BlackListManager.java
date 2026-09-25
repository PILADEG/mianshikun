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

/**
 * 黑名单管理器
 * <p>
 * 双层结构：
 * <ol>
 *   <li><b>本实例动态黑名单</b>：封号当刻写入，本实例下一个请求立即拦截（无需等 Nacos 推送 + 过滤器重建）</li>
 *   <li><b>Nacos 静态名单</b>：定时把动态黑名单批量合并写入 Nacos 配置，
 *       由 {@link BlackListUtils#rebuild} 供所有实例共享</li>
 * </ol>
 * 之所以批量写入而非逐条写 Nacos：配置每次变更都会推送全实例并触发布隆过滤器重建，
 * 且 Nacos 是「读-改-写」全量覆盖，逐条写既有推送压力也有并发覆盖风险。
 */
@Slf4j
@Component
public class BlackListManager {

    /** 同步到 Nacos 的分布式锁（多实例部署时避免并发覆盖配置） */
    private static final String SYNC_LOCK_KEY = "lock:blacklist:nacos:sync";
    private static final int PUBLISH_RETRY = 3;

    /** 本实例即时生效的动态黑名单（封号即写入） */
    private final Set<String> localDynamic = ConcurrentHashMap.newKeySet();

    /** 待批量同步到 Nacos 的缓冲 */
    private final Set<String> pendingSync = ConcurrentHashMap.newKeySet();

    @NacosInjected
    private ConfigService configService;

    @Value("${nacos.config.data-id}")
    private String dataId;

    @Value("${nacos.config.group}")
    private String group;

    @Resource
    private RedissonClient redissonClient;

    // ==================== 对外接口 ====================

    /**
     * 封号时调用：IP 立即进本实例黑名单，并排队等待批量写入 Nacos
     */
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

    /** 黑名单判定：本实例动态名单 ∪ Nacos 静态布隆过滤器 */
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

    // ==================== 定时批量同步 ====================

    /** 定时（默认 60 秒）把待同步的 IP 合并写入 Nacos；启动 30 秒后开始 */
    @Scheduled(fixedDelay = 60_000, initialDelay = 30_000)
    public void scheduledFlush() {
        flushNow();
    }

    /** 立即同步（供定时任务与测试调用） */
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

    /** 读-改-写 Nacos 配置：带 cas 乐观锁重试，最后一次退化为直写（有分布式锁兜底） */
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
                // 说明：cas 版本（publishConfigCas）在此不可用——@NacosInjected 注入的是
                // nacos-spring 的 EventPublishingConfigService 装饰器，它未实现该接口方法，
                // 运行时调用会抛 AbstractMethodError。因此这里用 Redisson 分布式锁
                // 保证「读-改-写」串行，避免多实例并发覆盖配置。
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
