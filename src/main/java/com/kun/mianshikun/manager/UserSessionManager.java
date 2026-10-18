package com.kun.mianshikun.manager;

import cn.hutool.json.JSONUtil;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.kun.mianshikun.model.dto.user.SessionInfo;
import com.kun.mianshikun.model.entity.User;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class UserSessionManager {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String KICKED_KEY_PREFIX = "kicked:";
    private static final String INVALIDATE_CHANNEL = "session:invalidate";
    private static final long SESSION_TTL_DAYS = 7L;
    private static final long LOCAL_CACHE_TTL_SECONDS = 8L;

    private final Cache<String, SessionInfo> localCache = Caffeine.newBuilder()
            .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(100_000)
            .build();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    public static String sessionKey(Long userId, String deviceType) {
        return SESSION_KEY_PREFIX + userId + ":" + deviceType;
    }

    private static String kickedKey(Long userId, String deviceType, String tokenId) {
        return KICKED_KEY_PREFIX + userId + ":" + deviceType + ":" + tokenId;
    }

    public void createSession(User user, String deviceType, String tokenId) {
        long now = System.currentTimeMillis();
        SessionInfo info = SessionInfo.builder()
                .tokenId(tokenId)
                .userRole(user.getUserRole())
                .userName(user.getUserName())
                .userAvatar(user.getUserAvatar())
                .loginTime(now)
                .lastActiveTime(now)
                .build();
        saveSession(sessionKey(user.getId(), deviceType), info);
    }

    public SessionInfo getSession(Long userId, String deviceType) {
        return getSession(userId, deviceType, false);
    }

    public SessionInfo getSession(Long userId, String deviceType, boolean forceRefresh) {
        if (userId == null || StringUtils.isBlank(deviceType)) {
            return null;
        }
        return getSessionByKey(sessionKey(userId, deviceType), forceRefresh);
    }

    public void rotateToken(Long userId, String deviceType, String newTokenId) {
        String key = sessionKey(userId, deviceType);
        SessionInfo info = getSessionByKey(key, true);
        if (info == null) {
            return;
        }
        info.setTokenId(newTokenId);
        info.setLastActiveTime(System.currentTimeMillis());
        saveSession(key, info);
    }

    public void deleteSession(Long userId, String deviceType) {
        String key = sessionKey(userId, deviceType);
        try {
            stringRedisTemplate.delete(key);
        } catch (Exception e) {
            log.warn("delete session failed, key={}", key, e);
        }
        localCache.invalidate(key);
        publishInvalidate(key);
    }
    public void deleteAllSession(Long userId) {
        for (String key : scanSessionKeys(userId)) {
            try {
                stringRedisTemplate.delete(key);
            } catch (Exception e) {
                log.warn("delete session failed, key={}", key, e);
            }
            localCache.invalidate(key);
            publishInvalidate(key);
        }
    }
    public void kickOldSession(Long userId, String deviceType) {
        String key = sessionKey(userId, deviceType);
        try {
            String json = stringRedisTemplate.opsForValue().get(key);
            if (StringUtils.isBlank(json)) {
                return;
            }
            SessionInfo old = parse(json);
            String oldTokenId = old != null ? old.getTokenId() : json;
            if (StringUtils.isBlank(oldTokenId)) {
                return;
            }
            Long ttl = stringRedisTemplate.getExpire(key, TimeUnit.SECONDS);
            if (ttl != null && ttl > 0) {
                stringRedisTemplate.opsForValue().set(
                        kickedKey(userId, deviceType, oldTokenId), "true", ttl, TimeUnit.SECONDS);
            }
        } catch (Exception e) {
            log.warn("kick old session failed, userId={}, deviceType={}", userId, deviceType, e);
        }
    }

    public boolean consumeKicked(Long userId, String deviceType, String tokenId) {
        if (userId == null || StringUtils.isBlank(deviceType) || StringUtils.isBlank(tokenId)) {
            return false;
        }
        String key = kickedKey(userId, deviceType, tokenId);
        try {
            Boolean exists = stringRedisTemplate.hasKey(key);
            if (Boolean.TRUE.equals(exists)) {
                stringRedisTemplate.delete(key);
                return true;
            }
        } catch (Exception e) {
            log.warn("consume kicked flag failed, key={}", key, e);
        }
        return false;
    }

    public void syncUserInfo(Long userId, String deviceType, User user) {
        if (user == null || userId == null) {
            return;
        }
        if (StringUtils.isBlank(deviceType)) {
            updateAllSessions(userId, user.getUserRole(), user.getUserName(), user.getUserAvatar());
            return;
        }
        String key = sessionKey(userId, deviceType);
        SessionInfo info = getSessionByKey(key, true);
        if (info == null) {
            return;
        }
        if (user.getUserRole() != null) {
            info.setUserRole(user.getUserRole());
        }
        if (user.getUserName() != null) {
            info.setUserName(user.getUserName());
        }
        if (user.getUserAvatar() != null) {
            info.setUserAvatar(user.getUserAvatar());
        }
        saveSession(key, info);
    }

    public void updateRoleForAllDevices(Long userId, String role) {
        updateAllSessions(userId, role, null, null);
    }

    public void evictLocal(String key) {
        localCache.invalidate(key);
    }

    private void updateAllSessions(Long userId, String role, String userName, String userAvatar) {
        for (String key : scanSessionKeys(userId)) {
            SessionInfo info = getSessionByKey(key, true);
            if (info == null) {
                continue;
            }
            if (role != null) {
                info.setUserRole(role);
            }
            if (userName != null) {
                info.setUserName(userName);
            }
            if (userAvatar != null) {
                info.setUserAvatar(userAvatar);
            }
            saveSession(key, info);
        }
    }

    // 用 SCAN 代替 KEYS：KEYS 是 O(N) 阻塞命令，用户量大时会卡住 Redis
    private Set<String> scanSessionKeys(Long userId) {
        String pattern = SESSION_KEY_PREFIX + userId + ":*";
        Set<String> keys = new HashSet<>();
        try {
            stringRedisTemplate.execute((RedisCallback<Void>) connection -> {
                try (Cursor<byte[]> cursor = connection.scan(
                        ScanOptions.scanOptions().match(pattern).count(1000).build())) {
                    while (cursor.hasNext()) {
                        keys.add(new String(cursor.next(), StandardCharsets.UTF_8));
                    }
                }
                return null;
            });
        } catch (Exception e) {
            log.warn("scan session keys failed, userId={}", userId, e);
        }
        return keys;
    }

    private SessionInfo getSessionByKey(String key, boolean forceRefresh) {
        if (!forceRefresh) {
            SessionInfo cached = localCache.getIfPresent(key);
            if (cached != null) {
                return cached;
            }
        } else {
            localCache.invalidate(key);
        }
        String json;
        try {
            json = stringRedisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            // Redis 异常：本地缓存已在上面尝试过，这里直接返回 null（业务层会要求重新登录）
            log.warn("read session from redis failed, key={}", key, e);
            return null;
        }
        if (StringUtils.isBlank(json)) {
            return null;
        }
        SessionInfo info = parse(json);
        if (info == null) {
            // 旧格式（值为裸 tokenId 字符串）不再兼容，需重新登录
            log.warn("invalid session format, key={}", key);
            return null;
        }
        localCache.put(key, info);
        return info;
    }

    private SessionInfo parse(String json) {
        try {
            SessionInfo info = JSONUtil.toBean(json, SessionInfo.class);
            if (info == null || StringUtils.isBlank(info.getTokenId())) {
                return null;
            }
            return info;
        } catch (Exception e) {
            return null;
        }
    }

    private void saveSession(String key, SessionInfo info) {
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(info),
                SESSION_TTL_DAYS, TimeUnit.DAYS);
        localCache.invalidate(key);
        publishInvalidate(key);
    }

    private void publishInvalidate(String key) {
        try {
            stringRedisTemplate.convertAndSend(INVALIDATE_CHANNEL, key);
        } catch (Exception e) {
            log.warn("publish invalidate failed, key={}", key, e);
        }
    }

    private void onInvalidateMessage(Message message, byte[] pattern) {
        String key = new String(message.getBody(), StandardCharsets.UTF_8);
        localCache.invalidate(key);
        log.debug("session cache invalidated by broadcast: {}", key);
    }

    @Bean
    public RedisMessageListenerContainer sessionInvalidateListenerContainer(
            RedisConnectionFactory connectionFactory) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this::onInvalidateMessage,
                new ChannelTopic(INVALIDATE_CHANNEL));
        return container;
    }
}
