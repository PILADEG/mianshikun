package com.kun.mianshikun.model.dto.user;

import com.kun.mianshikun.common.PageRequest;
import java.io.Serializable;
import lombok.Data;
import lombok.EqualsAndHashCode;

@EqualsAndHashCode(callSuper = true)
@Data
public class UserQueryRequest extends PageRequest implements Serializable {
    private Long id;

    private String userName;

    private String userProfile;

    private String userRole;

    private static final long serialVersionUID = 1L;
}