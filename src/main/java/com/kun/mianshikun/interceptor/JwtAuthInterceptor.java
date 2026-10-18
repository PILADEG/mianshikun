package com.kun.mianshikun.interceptor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.manager.UserSessionManager;
import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.dto.user.SessionInfo;
import com.kun.mianshikun.util.JwtUtil;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.io.IOException;
import java.util.UUID;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String ATTR_USER_ID = "JWT_USER_ID";
    private static final String ATTR_USER_ROLE = "JWT_USER_ROLE";
    private static final String ATTR_USER_NAME = "JWT_USER_NAME";
    private static final String ATTR_USER_AVATAR = "JWT_USER_AVATAR";
    private static final String ATTR_DEVICE_TYPE = "JWT_DEVICE_TYPE";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserSessionManager userSessionManager;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        // 无 token → 匿名访问放行，登录态由业务层（@AuthCheck / UserContext 判空）兜底
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true;
        }
        String accessToken = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.parseAccessToken(accessToken);
            Long userId = jwtUtil.getUserId(claims);
            String tokenId = jwtUtil.getTokenId(claims);
            String deviceType = jwtUtil.getDeviceType(claims);

            if (userId == null || StringUtils.isBlank(tokenId) || StringUtils.isBlank(deviceType)) {
                writeUnauthorized(response, "未登录");
                return false;
            }

            // 单次会话读取：活跃校验 + 用户上下文，全部来自会话对象（不再查询数据库）
            SessionInfo session = userSessionManager.getSession(userId, deviceType,
                    isSensitivePath(request));
            if (session == null) {
                if (userSessionManager.consumeKicked(userId, deviceType, tokenId)) {
                    writeKickedOffline(response);
                } else {
                    writeUnauthorized(response, "未登录");
                }
                return false;
            }
            if (!tokenId.equals(session.getTokenId())) {
                // tokenId 不匹配 → 该令牌已被同端新登录顶替
                userSessionManager.consumeKicked(userId, deviceType, tokenId);
                writeKickedOffline(response);
                return false;
            }
            if (UserConstant.BAN_ROLE.equals(session.getUserRole())
                    && !"/user/logout".equals(pathOf(request))) {
                writeJson(response, ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "您的账号已封禁"));
                return false;
            }
            setRequestAttributes(request, userId, deviceType, session);
            return true;
        } catch (ExpiredJwtException e) {
            return tryRefresh(request, response, e.getClaims());
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    private boolean isSensitivePath(HttpServletRequest request) {
        return pathOf(request).startsWith("/user/");
    }

    private String pathOf(HttpServletRequest request) {
        // Spring Boot 的 DispatcherServlet 默认映射为 "/"，getServletPath() 为空，
        // 真实路径在 pathInfo 中，因此用 requestURI 去掉 contextPath。
        String uri = request.getRequestURI();
        if (StringUtils.isBlank(uri)) {
            return "";
        }
        String contextPath = request.getContextPath();
        String path = uri;
        if (StringUtils.isNotBlank(contextPath) && uri.startsWith(contextPath)) {
            path = uri.substring(contextPath.length());
        }
        return path;
    }

    private boolean tryRefresh(HttpServletRequest request, HttpServletResponse response,
            Claims expiredClaims) throws IOException {
        String refreshTokenStr = request.getHeader("X-Refresh-Token");
        if (StringUtils.isBlank(refreshTokenStr)) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        try {
            Claims refreshClaims = jwtUtil.parseRefreshToken(refreshTokenStr);
            Long userId = jwtUtil.getUserId(refreshClaims);
            String tokenId = jwtUtil.getTokenId(refreshClaims);
            String deviceType = jwtUtil.getDeviceType(refreshClaims);
            if (userId == null || StringUtils.isBlank(tokenId) || StringUtils.isBlank(deviceType)) {
                writeUnauthorized(response, "未登录");
                return false;
            }

            // 刷新必须回源 Redis：这是会话真实性的权威校验
            SessionInfo session = userSessionManager.getSession(userId, deviceType, true);
            if (session == null) {
                if (userSessionManager.consumeKicked(userId, deviceType, tokenId)) {
                    writeKickedOffline(response);
                } else {
                    writeUnauthorized(response, "未登录");
                }
                return false;
            }
            if (!tokenId.equals(session.getTokenId())) {
                userSessionManager.consumeKicked(userId, deviceType, tokenId);
                writeKickedOffline(response);
                return false;
            }
            if (UserConstant.BAN_ROLE.equals(session.getUserRole())) {
                writeJson(response, ResultUtils.error(ErrorCode.NO_AUTH_ERROR, "您的账号已封禁"));
                return false;
            }

            // 令牌轮换：生成新令牌对并更新会话中的 tokenId（保留用户资料）
            String newTokenId = UUID.randomUUID().toString();
            String newAccessToken = jwtUtil.generateAccessToken(userId, newTokenId, deviceType);
            RefreshTokenResult newRefresh = jwtUtil.generateRefreshToken(userId, deviceType, newTokenId);
            userSessionManager.rotateToken(userId, deviceType, newTokenId);

            response.setHeader("X-Access-Token", newAccessToken);
            response.setHeader("X-Refresh-Token", newRefresh.getToken());

            // 本次请求沿用旧令牌的身份，用户上下文仍从会话对象构建
            setRequestAttributes(request, userId, deviceType, session);
            return true;
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    private void setRequestAttributes(HttpServletRequest request, Long userId,
            String deviceType, SessionInfo session) {
        request.setAttribute(ATTR_USER_ID, userId);
        request.setAttribute(ATTR_USER_ROLE, session.getUserRole());
        request.setAttribute(ATTR_USER_NAME, session.getUserName());
        request.setAttribute(ATTR_USER_AVATAR, session.getUserAvatar());
        request.setAttribute(ATTR_DEVICE_TYPE, deviceType);
    }

    private void writeKickedOffline(HttpServletResponse response) throws IOException {
        writeJson(response, ResultUtils.error(ErrorCode.KICKED_OFFLINE, "您的账号已在其他设备登录，您已被踢下线"));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        writeJson(response, ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, message));
    }

    private void writeJson(HttpServletResponse response, BaseResponse<?> resp) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(new ObjectMapper().writeValueAsString(resp));
    }
}
