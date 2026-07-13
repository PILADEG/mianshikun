package com.kun.mianshikun.constant;

public interface RedisConstant {
    String USER_SIGN_IN_KEY = "user:signins:";
    static String getUserSignInKey(String userId,Integer year) {
        return USER_SIGN_IN_KEY + year+":"+userId;
    }
}
