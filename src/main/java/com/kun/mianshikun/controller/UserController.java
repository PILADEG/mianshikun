package com.kun.mianshikun.controller;

import com.alibaba.csp.sentinel.Entry;
import com.alibaba.csp.sentinel.EntryType;
import com.alibaba.csp.sentinel.SphU;
import com.alibaba.csp.sentinel.Tracer;
import com.alibaba.csp.sentinel.slots.block.BlockException;
import com.alibaba.csp.sentinel.slots.block.degrade.DegradeException;
import com.alibaba.csp.sentinel.slots.block.flow.param.ParamFlowException;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.DeleteRequest;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.config.WxOpenConfig;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.model.dto.user.UserAddRequest;
import com.kun.mianshikun.model.dto.user.UserLoginRequest;
import com.kun.mianshikun.model.dto.user.UserQueryRequest;
import com.kun.mianshikun.model.dto.user.UserRegisterRequest;
import com.kun.mianshikun.model.dto.user.UserUpdateMyRequest;
import com.kun.mianshikun.model.dto.user.UserLoginResponse;
import com.kun.mianshikun.model.dto.user.UserUpdateRequest;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.sentinel.SentinelConstant;
import com.kun.mianshikun.util.UserContext;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.UserService;

import java.util.List;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.swing.text.BadLocationException;

import com.kun.mianshikun.utils.NetUtils;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import me.chanjar.weixin.common.bean.oauth2.WxOAuth2AccessToken;
import me.chanjar.weixin.mp.api.WxMpService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.util.DigestUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.kun.mianshikun.service.impl.UserServiceImpl.SALT;

/**
 * 用户接口
 *
 */
@RestController
@RequestMapping("/user")
@Slf4j
public class UserController {

    @Resource
    private UserService userService;

    @Resource
    private WxOpenConfig wxOpenConfig;

    // region 登录相关
    @GetMapping("/signIn")
    public BaseResponse<Boolean> userSignIn(@RequestParam String userId){
        if (userId == null || StringUtils.isBlank(userId)){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(userService.userSignIn(userId));
    }
    @GetMapping("/signIn/get")
    public BaseResponse<List> userSignInGet(@RequestParam String userId,
                                            @RequestParam(required = false) Integer year){
        if (userId == null || StringUtils.isBlank(userId)){
            return ResultUtils.error(ErrorCode.PARAMS_ERROR);
        }
        return ResultUtils.success(userService.getUserSignInDays(userId, year));
    }
    /**
     * 用户注册
     *
     * @param userRegisterRequest
     * @return
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest,
    HttpServletRequest  request) {
        String address = NetUtils.getIpAddress( request);
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConstant.USER_REGISTER,
                    EntryType.IN, 1, address);
            // Your logic here.
            if (userRegisterRequest == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            String userAccount = userRegisterRequest.getUserAccount();
            String userPassword = userRegisterRequest.getUserPassword();
            String checkPassword = userRegisterRequest.getCheckPassword();
            if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
                return null;
            }
            long result = userService.userRegister(userAccount, userPassword, checkPassword);
            return ResultUtils.success(result);
        } catch (Throwable ex) {
            // Handle request rejection.
            ex.printStackTrace();
            if (!BlockException.isBlockException( ex)){
                if (!(ex instanceof BusinessException)){
                    Tracer.trace(ex);
                    log.info("--------已上报--------");
                }
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, ex.getMessage());
            }
            if (ex instanceof ParamFlowException){
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "注册失败，请求流量超出限制");
            }
            if (ex instanceof DegradeException){
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误,请稍后再试");
            }
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "注册失败，系统错误");
        } finally {
            if (entry != null) {
                entry.exit(1, address);
            }
        }

    }

    /**
     * 用户登录
     *
     * @param userLoginRequest
     * 
     * @return
     */
    @PostMapping("/login")
    public BaseResponse<UserLoginResponse> userLogin(@RequestBody UserLoginRequest userLoginRequest,
                                                      HttpServletRequest request) {
        String address = NetUtils.getIpAddress( request);
        Entry entry = null;
        try {
            entry = SphU.entry(SentinelConstant.USER_LOGIN,
                    EntryType.IN, 1, address);
            // Your logic here.
            if (userLoginRequest == null) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            String userAccount = userLoginRequest.getUserAccount();
            String userPassword = userLoginRequest.getUserPassword();
            if (StringUtils.isAnyBlank(userAccount, userPassword)) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR);
            }
            String userAgent = request.getHeader("User-Agent");
            return ResultUtils.success(userService.userLogin(userAccount, userPassword, userAgent));
        } catch (Throwable ex) {
            // Handle request rejection.
            ex.printStackTrace();
            if (!BlockException.isBlockException( ex)){
                if (!(ex instanceof BusinessException)){
                    Tracer.trace(ex);
                    log.info("--------已上报--------");
                }
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, ex.getMessage());
            }
            if (ex instanceof ParamFlowException){
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "登录失败，请求流量超出限制");
            }
            if (ex instanceof DegradeException){
                return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "系统错误,请稍后再试");
            }
            return ResultUtils.error(ErrorCode.SYSTEM_ERROR, "登录失败，系统错误");
        } finally {
            if (entry != null) {
                entry.exit(1, address);
            }
        }

    }

    /**
     * 用户登录（微信开放平台）
     */
    @GetMapping("/login/wx_open")
    public BaseResponse<UserLoginResponse> userLoginByWxOpen(@RequestParam("code") String code,
                                                              HttpServletRequest request) {
        try {
            WxMpService wxService = wxOpenConfig.getWxMpService();
            WxOAuth2AccessToken accessToken = wxService.getOAuth2Service().getAccessToken(code);
            WxOAuth2UserInfo userInfo = wxService.getOAuth2Service().getUserInfo(accessToken, code);
            String unionId = userInfo.getUnionId();
            String mpOpenId = userInfo.getOpenid();
            if (StringUtils.isAnyBlank(unionId, mpOpenId)) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，系统错误");
            }
            String userAgent = request.getHeader("User-Agent");
            return ResultUtils.success(userService.userLoginByMpOpen(userInfo, userAgent));
        } catch (Exception e) {
            log.error("userLoginByWxOpen error", e);
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，系统错误");
        }
    }

    /**
     * 用户注销
     *
     * 
     * @return
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        String refreshToken = request.getHeader("X-Refresh-Token");
        boolean result = userService.userLogout(refreshToken);
        return ResultUtils.success(result);
    }

    /**
     * 获取当前登录用户
     *
     * 
     * @return
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser() {
        LoginUserVO loginUserVO = UserContext.getLoginUser();
        if (loginUserVO == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        return ResultUtils.success(loginUserVO);
    }

    // endregion

    // region 增删改查

    /**
     * 创建用户
     *
     * @param userAddRequest
     * 
     * @return
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if(userAddRequest.getUserAccount() == null ||
        userAddRequest.getUserAccount().equals("")){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号不能为空");
        }
        User user = new User();
        BeanUtils.copyProperties(userAddRequest, user);
        // 默认密码 12345678
        String defaultPassword = "12345678";
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + defaultPassword).getBytes());
        user.setUserPassword(encryptPassword);
        boolean result = userService.save(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(user.getId());
    }

    /**
     * 删除用户
     *
     * @param deleteRequest
     * 
     * @return
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) {
        if (deleteRequest == null || deleteRequest.getId() <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        if(deleteRequest.getId().equals(UserContext.getUserId())){
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "不能删除自己");
        }
        boolean b = userService.removeById(deleteRequest.getId());
        return ResultUtils.success(b);
    }

    /**
     * 更新用户
     *
     * @param userUpdateRequest
     * 
     * @return
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateRequest, user);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

    /**
     * 根据 id 获取用户（仅管理员）
     *
     * @param id
     * 
     * @return
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<User> getUserById(long id) {
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR);
        return ResultUtils.success(user);
    }

    /**
     * 根据 id 获取包装类
     *
     * @param id
     * 
     * @return
     */
    @GetMapping("/get/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<UserVO> getUserVOById(long id) {
        BaseResponse<User> response = getUserById(id);
        User user = response.getData();
        return ResultUtils.success(userService.getUserVO(user));
    }

    /**
     * 分页获取用户列表（仅管理员）
     *
     * @param userQueryRequest
     * 
     * @return
     */
    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest) {
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        return ResultUtils.success(userPage);
    }

    /**
     * 分页获取用户封装列表
     *
     * @param userQueryRequest
     * 
     * @return
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
    public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        long current = userQueryRequest.getCurrent();
        long size = userQueryRequest.getPageSize();
        // 限制爬虫
        ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
        Page<User> userPage = userService.page(new Page<>(current, size),
                userService.getQueryWrapper(userQueryRequest));
        Page<UserVO> userVOPage = new Page<>(current, size, userPage.getTotal());
        List<UserVO> userVO = userService.getUserVO(userPage.getRecords());
        userVOPage.setRecords(userVO);
        return ResultUtils.success(userVOPage);
    }

    // endregion

    /**
     * 更新个人信息
     *
     * @param userUpdateMyRequest
     * 
     * @return
     */
    @PostMapping("/update/my")
    public BaseResponse<Boolean> updateMyUser(@RequestBody UserUpdateMyRequest userUpdateMyRequest) {
        if (userUpdateMyRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR);
        }
        Long userId = UserContext.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        User user = new User();
        BeanUtils.copyProperties(userUpdateMyRequest, user);
        user.setId(userId);
        boolean result = userService.updateById(user);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR);
        return ResultUtils.success(true);
    }

}
