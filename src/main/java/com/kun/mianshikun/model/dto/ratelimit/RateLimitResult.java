package com.kun.mianshikun.model.dto.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;

/**
 * 限流检查结果
 */
@Data
@AllArgsConstructor
public class RateLimitResult {
    /** 状态: 0=放行 1=封禁中 2=触发封禁+违规 3=触发封号 */
    private int status;
    /** 当前分钟请求次数 */
    private int qpsCount;
    /** 当前违规累计次数 */
    private int violationCount;

    public boolean isAllowed() {
        return status == 0;
    }

    public boolean isBanned() {
        return status == 1;
    }

    public boolean isViolationTriggered() {
        return status >= 2;
    }

    public boolean isBanRequired() {
        return status == 3;
    }
}
