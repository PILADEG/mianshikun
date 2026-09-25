package com.kun.mianshikun.model.dto.user;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Redis 会话对象
 * <p>
 * 会话是用户权限与资料的权威副本：拦截器只需一次读取即可同时完成
 * 「活跃校验（tokenId 比对）」与「用户上下文构建」，无需再查询数据库。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo implements Serializable {

    /** 当前活跃令牌标识，用于同端互斥与踢下线判定 */
    private String tokenId;

    /** 用户角色（实时值，封号/降权后立即生效） */
    private String userRole;

    /** 用户昵称 */
    private String userName;

    /** 用户头像 */
    private String userAvatar;

    /** 登录时间（毫秒） */
    private Long loginTime;

    /** 最近活跃时间（毫秒） */
    private Long lastActiveTime;

    private static final long serialVersionUID = 1L;
}
