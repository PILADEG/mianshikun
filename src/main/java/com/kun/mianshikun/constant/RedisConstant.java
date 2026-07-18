package com.kun.mianshikun.constant;

public interface RedisConstant {
    String USER_SIGN_IN_KEY = "user:signins:";
    String QUESTIONBANK_HOTKEY_KEY = "questionBank:hotkey:";
    static String getUserSignInKey(String userId,Integer year) {
        return USER_SIGN_IN_KEY + year+":"+userId;
    }
}
