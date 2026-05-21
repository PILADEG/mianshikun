package com.kun.mianshikun.model.dto.user;

import com.kun.mianshikun.model.vo.LoginUserVO;
import java.io.Serializable;
import lombok.Data;

@Data
public class UserLoginResponse implements Serializable {

    private String accessToken;

    private String refreshToken;

    private LoginUserVO loginUserVO;

    private static final long serialVersionUID = 1L;
}
