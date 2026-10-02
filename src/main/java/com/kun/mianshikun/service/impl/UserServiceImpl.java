package com.kun.mianshikun.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.CommonConstant;
import com.kun.mianshikun.constant.RedisConstant;
import com.kun.mianshikun.constant.UserConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.manager.UserSessionManager;
import com.kun.mianshikun.mapper.UserMapper;
import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.dto.user.SessionInfo;
import com.kun.mianshikun.model.dto.user.UserLoginResponse;
import com.kun.mianshikun.model.dto.user.UserQueryRequest;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.enums.UserRoleEnum;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.DeviceUtil;
import com.kun.mianshikun.util.JwtUtil;
import com.kun.mianshikun.util.UserContext;
import com.kun.mianshikun.utils.SqlUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;

import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RBitSet;
import org.redisson.api.RedissonClient;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.DigestUtils;

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    public static final String SALT = "kun";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private UserSessionManager userSessionManager;

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Autowired
    private RedissonClient redissonClient;

    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    @Override
    public boolean userSignIn(String userId){
        String userRole = UserContext.getUserRole();
        if(StringUtils.isBlank(userRole) || userRole.equals(UserConstant.GUEST_ROLE)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        log.info("用户{}签到", userId);
        LocalDateTime now = LocalDateTime.now();
        Integer day = now.getDayOfYear();
        Integer year = now.getYear();
        RBitSet bitSet = redissonClient.getBitSet(RedisConstant.getUserSignInKey(userId, year));
        if (bitSet.get(day)){
            return true;
        }
        bitSet.set(day, true);
        return true;
    }
    @Override
    @AuthCheck(mustRole = UserConstant.DEFAULT_ROLE)
    public List<Integer> getUserSignInDays(String userId, Integer year){
        String userRole = UserContext.getUserRole();
        if(StringUtils.isBlank(userRole) || userRole.equals(UserConstant.GUEST_ROLE)){
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        LocalDateTime now = LocalDateTime.now();
        if (year == null){
            year = now.getYear();
        }
        RBitSet bitSet = redissonClient.getBitSet(RedisConstant.getUserSignInKey(userId, year));
        BitSet bitSet1 = bitSet.asBitSet();
        List<Integer> list = new ArrayList<>();
        int current = 0;
        while ((current = bitSet1.nextSetBit(current)) != -1){
            list.add(current);
            current++;
        }
        log.info("签到天数{}",  list);
        return list;
    }
    @Transactional(rollbackFor = Exception.class)
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 4 || checkPassword.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        // 密码和校验密码相同
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            // 账户不能重复
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            // 2. 加密
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
            // 3. 插入数据
            User user = new User();
            user.setUserAccount(userAccount);
            user.setUserPassword(encryptPassword);
            boolean saveResult = this.save(user);
            if (!saveResult) {
                throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
            }
            return user.getId();
        }
    }

    @Override
    public UserLoginResponse userLogin(String userAccount, String userPassword, String userAgent) {
        // 1. 校验
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        // 2. 加密
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        // 查询用户是否存在
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        // 用户不存在
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        // 封号校验：被封禁账号禁止登录
        if (UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
            throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封号，禁止登录");
        }
        String deviceType = DeviceUtil.detectDeviceType(userAgent);
        return buildLoginResponse(user, deviceType);
    }

    @Override
    public boolean userLogout(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        try {
            Claims claims = jwtUtil.parseRefreshToken(refreshToken);
            Long userId = jwtUtil.getUserId(claims);
            String tokenId = jwtUtil.getTokenId(claims);
            String deviceType = jwtUtil.getDeviceType(claims);
            if (userId == null || StringUtils.isBlank(deviceType)) {
                return true;
            }
            // 仅当此 refresh token 是当前活跃会话时才删除会话
            // 已被踢下线的 token 调用 logout 则不做任何操作
            SessionInfo session = userSessionManager.getSession(userId, deviceType, true);
            if (session != null && tokenId.equals(session.getTokenId())) {
                userSessionManager.deleteSession(userId, deviceType);
            }
        } catch (Exception e) {
            log.info("logout with invalid refresh token: {}", e.getMessage());
        }
        return true;
    }

    @Override
    public LoginUserVO getLoginUserVO(User user) {
        if (user == null) {
            return null;
        }
        LoginUserVO loginUserVO = new LoginUserVO();
        BeanUtils.copyProperties(user, loginUserVO);
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        BeanUtils.copyProperties(user, userVO);
        return userVO;
    }

    @Override
    public List<UserVO> getUserVO(List<User> userList) {
        if (CollUtil.isEmpty(userList)) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(java.util.stream.Collectors.toList());
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    private UserLoginResponse buildLoginResponse(User user, String deviceType) {
        String sessionTokenId = UUID.randomUUID().toString();

        // 同端互斥：为旧会话打 kicked 标记，使旧令牌请求能收到 40102
        userSessionManager.kickOldSession(user.getId(), deviceType);
        // 写入会话对象（含角色与用户资料，供拦截器一次读取）
        userSessionManager.createSession(user, deviceType, sessionTokenId);

        // 生成令牌（claims 仅含 userId/tokenId/deviceType）
        String accessToken = jwtUtil.generateAccessToken(user.getId(), sessionTokenId, deviceType);
        RefreshTokenResult refreshResult = jwtUtil.generateRefreshToken(user.getId(), deviceType, sessionTokenId);

        UserLoginResponse response = new UserLoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshResult.getToken());
        response.setLoginUserVO(getLoginUserVO(user));
        return response;
    }
}
