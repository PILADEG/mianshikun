package com.kun.mianshikun.util;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class SpelUtils {

    private static final DefaultParameterNameDiscoverer DISCOVERER = new DefaultParameterNameDiscoverer();
    private static final Pattern PARAM_PATTERN = Pattern.compile("#(\\w+)");

    public static String parseKey(ProceedingJoinPoint joinPoint, String expression) {
        if (expression == null || expression.isEmpty()) {
            return expression;
        }
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNames = DISCOVERER.getParameterNames(signature.getMethod());
        Object[] args = joinPoint.getArgs();
        if (paramNames == null || args.length != paramNames.length) {
            return expression;
        }

        StringBuffer result = new StringBuffer();
        Matcher matcher = PARAM_PATTERN.matcher(expression);
        while (matcher.find()) {
            String paramName = matcher.group(1);
            String value = null;
            for (int i = 0; i < paramNames.length; i++) {
                if (paramNames[i].equals(paramName)) {
                    value = String.valueOf(args[i]);
                    break;
                }
            }
            if (value == null) {
                return null;
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(result);
        return result.toString();
    }
}
