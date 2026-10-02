package com.kun.mianshikun.model.dto.ratelimit;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RateLimitResult {
    private int status;
    private int qpsCount;
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
