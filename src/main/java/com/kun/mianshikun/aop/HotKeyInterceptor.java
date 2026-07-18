package com.kun.mianshikun.aop;

import com.kun.mianshikun.annotation.HotKeyCache;
import com.kun.mianshikun.util.SpelUtils;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Slf4j
@Aspect
@Component
public class HotKeyInterceptor {

    @Around("@annotation(hotKeyCache)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, HotKeyCache hotKeyCache)
            throws Throwable {
        String keyExpression = hotKeyCache.key();
        if (keyExpression != null && !keyExpression.isEmpty()) {
            String resolvedKey = SpelUtils.parseKey(joinPoint, keyExpression);
            log.info("HotKeyInterceptor resolvedKey={}", resolvedKey);
        }
        return joinPoint.proceed();
    }
}
