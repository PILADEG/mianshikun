package com.kun.mianshikun.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AntSpiderCache {

    String module() default "";
    long threshold() default 20;
    long banThreshold() default 2;
    String message() default "请求过于频繁，请稍后再试";
}
