package com.kun.mianshikun.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshTokenResult {
    private String token;
    private String tokenId;
}
