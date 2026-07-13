package com.kun.mianshikun.constant;

/**
 * 用户常量
 *
 */
public interface UserConstant {

    //  region 权限

    String GUEST_ROLE = "guest";
    String DEFAULT_ROLE = "user";
    String ADMIN_ROLE = "admin";
    String BAN_ROLE = "ban";


    @Deprecated
    String USER_LOGIN_STATE = "user_login";

    // endregion
}
