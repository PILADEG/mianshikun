package com.kun.mianshikun.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.IService;
import com.kun.mianshikun.model.dto.user.UserLoginResponse;
import com.kun.mianshikun.model.dto.user.UserQueryRequest;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.UserVO;
import java.util.List;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;

public interface UserService extends IService<User> {

    long userRegister(String userAccount, String userPassword, String checkPassword);

    UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent);

    UserLoginResponse userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo, String userAgent);

    boolean userLogout(String refreshToken);

    LoginUserVO getLoginUserVO(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVO(List<User> userList);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
    boolean userSignIn(String userId);
    List<Integer> getUserSignInDays(String userId, Integer year);
}
