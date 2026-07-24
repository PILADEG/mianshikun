package com.kun.mianshikun.constant;

public interface RedisConstant {
    String USER_SIGN_IN_KEY = "user:signins:";
    String QUESTIONBANK_HOTKEY_KEY = "questionBank:hotkey:";

    // 违规计数（共享，按用户维度）
    String VIOLATION_KEY_PREFIX = "violation:user:";

    String RATE_PREFIX = "rate";

    String BLACKLIST_KEY_PREFIX = "blacklist";

    static String getRateKey(String module, String ip, long minute) {
        return RATE_PREFIX + ":" + module + ":" + ip + ":" + minute;
    }
    static String getBlacklistKey(String module, String ip) {
        return BLACKLIST_KEY_PREFIX + ":" + module + ":" + ip;
    }
    static String getUserSignInKey(String userId, Integer year) {
        return USER_SIGN_IN_KEY + year + ":" + userId;
    }

    static String getViolationKey(Long userId) {
        return VIOLATION_KEY_PREFIX + (userId != null ? userId : 0);
    }
}
