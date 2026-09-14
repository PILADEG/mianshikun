package com.kun.mianshikun.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.DeviceUtil;
import com.kun.mianshikun.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.io.IOException;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String ATTR_USER_ID = "JWT_USER_ID";
    private static final String ATTR_USER_ROLE = "JWT_USER_ROLE";
    private static final String ATTR_USER_NAME = "JWT_USER_NAME";
    private static final String ATTR_USER_AVATAR = "JWT_USER_AVATAR";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        // 无 token → 匿名访问，放行
        log.info("JWT interceptor action:{}",request.getRequestURI());
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true;
        }
        String accessToken = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.parseAccessToken(accessToken);
            log.info("JWT parse success: {}", claims);

            // 活跃会话校验：access token 有效期内，检查 session 是否仍是最新的
            String tokenId = jwtUtil.getTokenId(claims);
            String deviceType = jwtUtil.getDeviceType(claims);
            if (tokenId != null && deviceType != null) {
                boolean isActive = checkActiveSession(
                        claims.get("userId", Long.class), deviceType, tokenId, response);
                if (!isActive) {
                    return false;
                }
            }

            User user = userService.getOne(
                    new QueryWrapper<User>()
                            .eq("id", claims.get("userId", Long.class)));
            log.info("JWT parse success: {}", user);
            claims.put("userRole", user.getUserRole());
            claims.put("userName", user.getUserName());
            setRequestAttributes(request, claims);
            return true;
        } catch (ExpiredJwtException e) {
            Claims expiredClaims = e.getClaims();
            return tryRefresh(request, response, expiredClaims);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    /**
     * 校验当前 access token 是否仍是活跃会话
     */
    private boolean checkActiveSession(Long userId, String deviceType, String tokenId,
            HttpServletResponse response) throws IOException {
        String sessionKey = "session:" + userId + ":" + deviceType;
        String activeTokenId = stringRedisTemplate.opsForValue().get(sessionKey);

        if (activeTokenId == null) {
            // session 不存在 → 检查是否曾被打过 kicked 标记
            String kickedKey = "kicked:" + userId + ":" + deviceType + ":" + tokenId;
            String isKicked = stringRedisTemplate.opsForValue().get(kickedKey);
            if (isKicked != null) {
                stringRedisTemplate.delete(kickedKey);
                writeKickedOffline(response);
                return false;
            }
            writeUnauthorized(response, "未登录");
            return false;
        }

        if (!activeTokenId.equals(tokenId)) {
            // tokenId 不匹配 → 被踢下线
            String kickedKey = "kicked:" + userId + ":" + deviceType + ":" + tokenId;
            stringRedisTemplate.delete(kickedKey);
            writeKickedOffline(response);
            return false;
        }

        return true;
    }

    private boolean tryRefresh(HttpServletRequest request, HttpServletResponse response,
            Claims expiredClaims) throws IOException {
        String refreshTokenStr = request.getHeader("X-Refresh-Token");
        log.info("JWT refresh interceptor: {}", refreshTokenStr);
        if (StringUtils.isBlank(refreshTokenStr)) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        try {
            Claims refreshClaims = jwtUtil.parseRefreshToken(refreshTokenStr);
            Long userId = jwtUtil.getUserId(refreshClaims);
            String tokenId = jwtUtil.getTokenId(refreshClaims);
            String deviceType = jwtUtil.getDeviceType(refreshClaims);

            // 兼容旧 token：deviceType 为 null 时走旧 Redis key 逻辑
            if (deviceType == null) {
                return tryRefreshLegacy(request, response, expiredClaims, refreshClaims, userId, tokenId);
            }

            String sessionKey = "session:" + userId + ":" + deviceType;
            String activeTokenId = stringRedisTemplate.opsForValue().get(sessionKey);

            // session key 不存在 → 可能是已注销或被踢后活跃方也注销了
            if (activeTokenId == null) {
                String kickedKey = "kicked:" + userId + ":" + deviceType + ":" + tokenId;
                String isKicked = stringRedisTemplate.opsForValue().get(kickedKey);
                if (isKicked != null) {
                    stringRedisTemplate.delete(kickedKey);
                    writeKickedOffline(response);
                    return false;
                }
                log.info("Session not found, user may have logged out: {}", userId);
                writeUnauthorized(response, "未登录");
                return false;
            }

            // tokenId 不匹配 → 被踢下线
            if (!activeTokenId.equals(tokenId)) {
                log.info("Token kicked offline, userId: {}, deviceType: {}", userId, deviceType);
                String kickedKey = "kicked:" + userId + ":" + deviceType + ":" + tokenId;
                stringRedisTemplate.delete(kickedKey);
                writeKickedOffline(response);
                return false;
            }

            User user = userService.getById(userId);
            if (user == null) {
                log.info("User not found: {}", userId);
                writeUnauthorized(response, "未登录");
                return false;
            }

            String newTokenId = java.util.UUID.randomUUID().toString();
            String newAccessToken = jwtUtil.generateAccessToken(user, newTokenId, deviceType);
            RefreshTokenResult newRefresh = jwtUtil.generateRefreshToken(user, deviceType, newTokenId);

            stringRedisTemplate.opsForValue().set(
                    sessionKey,
                    newTokenId,
                    7, java.util.concurrent.TimeUnit.DAYS);

            response.setHeader("X-Access-Token", newAccessToken);
            response.setHeader("X-Refresh-Token", newRefresh.getToken());

            setRequestAttributes(request, expiredClaims);
            return true;
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    /**
     * 兼容旧版 refresh token（无 deviceType claim），走旧 Redis key refresh_token:<userId>
     */
    private boolean tryRefreshLegacy(HttpServletRequest request, HttpServletResponse response,
            Claims expiredClaims, Claims refreshClaims, Long userId, String tokenId) throws IOException {
        String redisTokenId = stringRedisTemplate.opsForValue().get("refresh_token:" + userId);
        if (redisTokenId == null || !redisTokenId.equals(tokenId)) {
            log.info("Refresh token not match: {}", userId);
            writeUnauthorized(response, "未登录");
            return false;
        }

        User user = userService.getById(userId);
        if (user == null) {
            log.info("User not found: {}", userId);
            writeUnauthorized(response, "未登录");
            return false;
        }

        // 生成新 token 时自动携带 deviceType，完成迁移
        String deviceType = DeviceUtil.detectDeviceType(request.getHeader("User-Agent"));
        String newTokenId = java.util.UUID.randomUUID().toString();
        String newAccessToken = jwtUtil.generateAccessToken(user, newTokenId, deviceType);
        RefreshTokenResult newRefresh = jwtUtil.generateRefreshToken(user, deviceType, newTokenId);

        stringRedisTemplate.opsForValue().set(
                "session:" + userId + ":" + deviceType,
                newTokenId,
                7, java.util.concurrent.TimeUnit.DAYS);
        stringRedisTemplate.delete("refresh_token:" + userId);

        response.setHeader("X-Access-Token", newAccessToken);
        response.setHeader("X-Refresh-Token", newRefresh.getToken());

        setRequestAttributes(request, expiredClaims);
        return true;
    }

    private void writeKickedOffline(HttpServletResponse response) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<?> resp = ResultUtils.error(ErrorCode.KICKED_OFFLINE, "您的账号已在其他设备登录，您已被踢下线");
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(resp));
    }

    private void setRequestAttributes(HttpServletRequest request, Claims claims) {
        request.setAttribute(ATTR_USER_ID, claims.get("userId", Long.class));
        request.setAttribute(ATTR_USER_ROLE, claims.get("userRole", String.class));
        request.setAttribute(ATTR_USER_NAME, claims.get("userName", String.class));
        request.setAttribute(ATTR_USER_AVATAR, claims.get("userAvatar", String.class));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<?> resp = ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, message);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(resp));
    }
}
