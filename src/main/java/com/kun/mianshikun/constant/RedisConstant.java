package com.kun.mianshikun.constant;

public interface RedisConstant {
    String USER_SIGN_IN_KEY = "user:signins:";
    String QUESTIONBANK_HOTKEY_KEY = "questionBank:hotkey:";
    String QUESTION_KEY_PREFIX = "question:hotkey:";
    // 违规计数（共享，按用户维度）
    String VIOLATION_KEY_PREFIX = "violation:user:";
    String HOTKEY_LOCK = "hotkey:lock:";
    String RATE_PREFIX = "rate";

    String BLACKLIST_KEY_PREFIX = "blacklist";
    static String getHotkeyLock(String module,String id){

        return HOTKEY_LOCK + module + ":" + id;
    }
    static String getRateKey(String module, String ip,String userId, long minute) {
        return RATE_PREFIX + ":" + module + ":" + userId + ":" + ip + ":" + minute;
    }
    static String getBlacklistKey(String module, String ip, String userId) {

        return BLACKLIST_KEY_PREFIX + ":" + module + ":" + userId + ":" + ip;
    }
    static String getUserSignInKey(String userId, Integer year) {

        return USER_SIGN_IN_KEY + year + ":" + userId;
    }

    static String getViolationKey(Long userId) {

        return VIOLATION_KEY_PREFIX + (userId != null ? userId : 0);
    }
}
