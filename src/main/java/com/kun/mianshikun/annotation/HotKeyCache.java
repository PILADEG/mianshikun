package com.kun.mianshikun.annotation;

import java.lang.annotation.*;

@Target({ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface HotKeyCache {
    String key() default "";
    String prefix() default "";

    // JD-HotKey 规则前缀，必须与 etcd 规则里的 key 一致（如 question_），否则判热永远为 false
    String hotkeyPrefix() default "";

    // 分布式锁的模块维度，锁 key 形如 hotkey:lock:{module}:{id}
    String module() default "";

    // 逻辑过期时间（秒）：超过后由抢到锁的线程同步重建，其余并发请求直接返回旧值
    long logicTtlSeconds() default 300;

    // 空值占位（查无此数据）的逻辑过期时间（秒），用于防穿透
    long nullTtlSeconds() default 60;

    // 重建失败后的退避时间（秒），避免每次读都重新触发重建
    long retryBackoffSeconds() default 10;

    // 冷启动抢锁的最长等待时间（毫秒），超时则自己回源兜底
    long lockWaitMillis() default 200;

    // 查无数据时是否缓存空值占位
    boolean cacheNull() default true;
}
