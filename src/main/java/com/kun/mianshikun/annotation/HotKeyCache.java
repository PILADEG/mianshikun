package com.kun.mianshikun.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotKeyCache {
    String key() default "";
    String prefix() default "";
}
