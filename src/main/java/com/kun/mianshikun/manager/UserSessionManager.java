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
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * 用户会话管理器（有状态 JWT 的会话层）
 * <p>
 * 三层读取结构：
 * <ol>
 *   <li>request attribute —— 请求级缓存（由 JwtAuthInterceptor 注入，业务层通过 UserContext 读取）</li>
 *   <li>Caffeine 本地缓存 —— 跨请求复用，TTL 作为「失效广播丢失」时的兜底</li>
 *   <li>Redis 会话对象 —— 跨实例权威源</li>
 * </ol>
 * 会话变更统一走 {@link #saveSession}：写 Redis → 清本地 → 广播其他实例。
 */
@Slf4j
@Component
public class UserSessionManager {

    private static final String SESSION_KEY_PREFIX = "session:";
    private static final String KICKED_KEY_PREFIX = "kicked:";
    private static final String INVALIDATE_CHANNEL = "session:invalidate";
    private static final long SESSION_TTL_DAYS = 7L;
    /** 本地缓存兜底 TTL（秒）：广播正常时失效延迟为毫秒级 */
    private static final long LOCAL_CACHE_TTL_SECONDS = 8L;

    private final Cache<String, SessionInfo> localCache = Caffeine.newBuilder()
            .expireAfterWrite(LOCAL_CACHE_TTL_SECONDS, TimeUnit.SECONDS)
            .maximumSize(100_000)
            .build();

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    // ==================== key 构造 ====================

    public static String sessionKey(Long userId, String deviceType) {
        return SESSION_KEY_PREFIX + userId + ":" + deviceType;
    }

    private static String kickedKey(Long userId, String deviceType, String tokenId) {
        return KICKED_KEY_PREFIX + userId + ":" + deviceType + ":" + tokenId;
    }

    // ==================== 会话读写 ====================

    /** 登录成功后写入会话（覆盖同端旧会话） */
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

    /**
     * 读取会话
     *
     * @param forceRefresh true 时跳过本地缓存直接回源 Redis（用于刷新令牌、注销等敏感路径）
     */
    public SessionInfo getSession(Long userId, String deviceType, boolean forceRefresh) {
        if (userId == null || StringUtils.isBlank(deviceType)) {
            return null;
        }
        return getSessionByKey(sessionKey(userId, deviceType), forceRefresh);
    }

    /** 令牌轮换：仅更新会话中的 tokenId（保留用户资料） */
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

    /** 注销：删除会话（该设备令牌立即失效） */
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

    /** 同端新登录：为旧会话打 kicked 标记，让旧令牌能识别出「已被踢下线」 */
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

    /** 消费 kicked 标记：返回 true 表示该令牌确实是被踢下线的 */
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

    /** 用户资料变更后同步会话（昵称/头像/角色），使变更立即生效 */
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

    /** 封号等场景：同步该用户所有设备会话的角色，使后续请求立即被拒绝 */
    public void updateRoleForAllDevices(Long userId, String role) {
        updateAllSessions(userId, role, null, null);
    }

    public void evictLocal(String key) {
        localCache.invalidate(key);
    }

    // ==================== 内部实现 ====================

    private void updateAllSessions(Long userId, String role, String userName, String userAvatar) {
        Set<String> keys = new HashSet<>();
        try {
            keys.addAll(stringRedisTemplate.keys(SESSION_KEY_PREFIX + userId + ":*"));
        } catch (Exception e) {
            log.warn("list session keys failed, userId={}", userId, e);
        }
        for (String key : keys) {
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

    /** 订阅会话失效广播：多实例部署时保证各节点本地缓存一致 */
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
