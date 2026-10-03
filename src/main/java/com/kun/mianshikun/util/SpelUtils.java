package com.kun.mianshikun.util;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SpelUtils {

    private static final ExpressionParser PARSER = new SpelExpressionParser();
    private static final ParameterNameDiscoverer NAME_DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Map<String, Expression> EXPRESSION_CACHE = new ConcurrentHashMap<>();

    public static String resolve(ProceedingJoinPoint joinPoint, String expression) {
        if (StringUtils.isBlank(expression)) {
            return null;
        }
        try {
            Expression parsed = EXPRESSION_CACHE.computeIfAbsent(expression, PARSER::parseExpression);
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            EvaluationContext context = new MethodBasedEvaluationContext(
                    joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs(), NAME_DISCOVERER);
            Object value = parsed.getValue(context);
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            log.warn("hotkey_cache|event=expression_fail|expr={}|msg={}", expression, e.getMessage());
            return null;
        }
    }
}
