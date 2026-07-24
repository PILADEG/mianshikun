package com.kun.mianshikun.service;

import com.kun.mianshikun.constant.RedisConstant;
import com.kun.mianshikun.model.dto.ratelimit.RateLimitResult;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.util.StreamUtils;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 基于 Redis Lua 的 IP 限流器
 */
@Component
public class RateLimiter {

    @Resource(name = "scriptRedissonClient")
    private RedissonClient redissonClient;

    private String luaScript;

    @PostConstruct
    public void init() {
        try (InputStream is = new ClassPathResource("rate_limit.lua").getInputStream()) {
            luaScript = new String(StreamUtils.copyToByteArray(is),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new RuntimeException("Failed to load rate_limit.lua", e);
        }
    }

    /**
     * 执行限流检查
     *
     * @param module       限流模块名
     * @param ip           请求 IP
     * @param userId       用户 ID（可为 null）
     * @param threshold    分钟限流阈值
     * @param banThreshold 封号阈值（违规次数）
     * @return 限流结果
     */
    @SuppressWarnings("unchecked")
    public RateLimitResult check(String module, String ip, Long userId,
                                  long threshold, long banThreshold) {
        try {
            long minute = System.currentTimeMillis() / 60000;
            String minuteKey = RedisConstant.getRateKey(module, ip, minute);
            String blacklistKey = RedisConstant.getBlacklistKey(module, ip);
            String violationKey = RedisConstant.getViolationKey(userId);

            List<Object> result = (List<Object>) redissonClient.getScript().eval(
                    RScript.Mode.READ_WRITE,
                    luaScript,
                    RScript.ReturnType.LIST,
                    Arrays.asList(minuteKey, blacklistKey, violationKey),
                    String.valueOf(threshold), String.valueOf(banThreshold), "60", "600"
            );

            int status = ((Number) result.get(0)).intValue();
            int qpsCount = ((Number) result.get(1)).intValue();
            int violationCount = ((Number) result.get(2)).intValue();

            return new RateLimitResult(status, qpsCount, violationCount);
        } catch (Exception e) {
            e.printStackTrace();
            return new RateLimitResult(0, 0, 0);
        }
    }
}
