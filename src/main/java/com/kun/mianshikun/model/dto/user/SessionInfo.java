package com.kun.mianshikun.model.dto.user;

import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo implements Serializable {

    private String tokenId;

    private String userRole;

    private String userName;

    private String userAvatar;

    private Long loginTime;

    private Long lastActiveTime;

    private static final long serialVersionUID = 1L;
}
