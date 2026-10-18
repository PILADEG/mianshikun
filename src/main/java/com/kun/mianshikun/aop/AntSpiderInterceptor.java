package com.kun.mianshikun.aop;

import com.kun.mianshikun.annotation.AntSpiderCache;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.manager.BlackListManager;
import com.kun.mianshikun.manager.UserSessionManager;
import com.kun.mianshikun.model.dto.ratelimit.RateLimitResult;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.service.RateLimiter;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.UserContext;
import com.kun.mianshikun.utils.NetUtils;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Slf4j
@Aspect
@Component
@Order(AntSpiderInterceptor.ORDER)
public class AntSpiderInterceptor {

    public static final int ORDER = -100;

    private static final String BAN_ROLE = "ban";

    @Resource
    private RateLimiter rateLimiter;

    @Resource
    private UserService userService;

    @Resource
    private UserSessionManager userSessionManager;

    @Resource
    private BlackListManager blackListManager;

    @Around("@annotation(antSpiderCache)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AntSpiderCache antSpiderCache)
            throws Throwable {
        HttpServletRequest request = currentRequest();
        if (request == null) {
            // 非 Web 上下文（内部调用等）拿不到请求来源，跳过限流，不影响原业务
            return joinPoint.proceed();
        }
        String ip = NetUtils.getIpAddress(request);
        Long userId = UserContext.getUserId();
        String module = resolveModule(antSpiderCache, joinPoint);

        RateLimitResult rateResult = rateLimiter.check(module, ip, userId,
                antSpiderCache.threshold(), antSpiderCache.banThreshold());

        log.info("rate_limit|module={}|ip={}|status={}|qps={}|viol={}",
                module, ip, rateResult.getStatus(),
                rateResult.getQpsCount(), rateResult.getViolationCount());

        if (rateResult.isAllowed()) {
            return joinPoint.proceed();
        }

        if (rateResult.getViolationCount() == 1) {
            // 发短信警告，待完善
            log.warn("{}用户疑似爬虫", userId);
        }
        if (rateResult.isBanRequired() && userId != null) {
            banUser(userId, ip);
        }
        return ResultUtils.error(ErrorCode.SYSTEM_ERROR, antSpiderCache.message());
    }

    private void banUser(Long userId, String ip) {
        userService.lambdaUpdate()
                .eq(User::getId, userId)
                .set(User::getUserRole, BAN_ROLE)
                .update();
        // 先写角色再删会话：删除失败时残留会话里的 ban 角色仍会被拦截器拦下
        userSessionManager.updateRoleForAllDevices(userId, BAN_ROLE);
        userSessionManager.deleteAllSession(userId);
        // 把触发封号的 IP 加入黑名单：本实例立即拦截，并批量同步到 Nacos 配置
        blackListManager.banIp(ip);
        log.warn("user banned due to rate limit violation, userId={}, ip={}", userId, ip);
    }

    private String resolveModule(AntSpiderCache antSpiderCache, ProceedingJoinPoint joinPoint) {
        if (StringUtils.isNotBlank(antSpiderCache.module())) {
            return antSpiderCache.module();
        }
        return StringUtils.uncapitalize(joinPoint.getSignature().getDeclaringType().getSimpleName());
    }

    private HttpServletRequest currentRequest() {
        RequestAttributes attributes = RequestContextHolder.getRequestAttributes();
        if (attributes instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attributes).getRequest();
        }
        return null;
    }
}
