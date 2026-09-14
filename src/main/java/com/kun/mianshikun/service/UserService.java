package com.kun.mianshikun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.mianshikun.model.dto.user.UserLoginResponse;
import com.kun.mianshikun.model.dto.user.UserQueryRequest;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.UserVO;
import java.util.List;

public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent);

    boolean userLogout(String refreshToken);

    LoginUserVO getLoginUserVO(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVO(List<User> userList);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
    boolean userSignIn(String userId);
    List<Integer> getUserSignInDays(String userId, Integer year);
}
