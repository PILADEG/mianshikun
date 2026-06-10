package com.kun.mianshikun.interceptor;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.service.UserService;
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
    private static final String ATTR_UNION_ID = "JWT_UNION_ID";
    private static final String ATTR_MP_OPEN_ID = "JWT_MP_OPEN_ID";

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
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            return true;
        }
        String accessToken = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.parseAccessToken(accessToken);
            log.info("JWT parse success: {}", claims);
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

            String newAccessToken = jwtUtil.generateAccessToken(user);
            RefreshTokenResult newRefresh = jwtUtil.generateRefreshToken(user);

            stringRedisTemplate.opsForValue().set(
                    "refresh_token:" + userId,
                    newRefresh.getTokenId(),
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

    private void setRequestAttributes(HttpServletRequest request, Claims claims) {
        request.setAttribute(ATTR_USER_ID, claims.get("userId", Long.class));
        request.setAttribute(ATTR_USER_ROLE, claims.get("userRole", String.class));
        request.setAttribute(ATTR_USER_NAME, claims.get("userName", String.class));
        request.setAttribute(ATTR_USER_AVATAR, claims.get("userAvatar", String.class));
        request.setAttribute(ATTR_UNION_ID, claims.get("unionId", String.class));
        request.setAttribute(ATTR_MP_OPEN_ID, claims.get("mpOpenId", String.class));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<?> resp = ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, message);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(resp));
    }
}
