# Session → JWT Auth Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace HttpSession-based login with JWT (Access Token + Refresh Token) + Redis authentication.

**Architecture:** New `JwtAuthInterceptor` (HandlerInterceptor) intercepts all requests, parses `Authorization: Bearer <token>` header, and sets user info into request attributes. `UserContext` provides static accessors for the current user. `UserServiceImpl` generates tokens on login and invalidates them on logout via Redis. All 8 Controllers drop `HttpServletRequest` parameters and use `UserContext` instead.

**Tech Stack:** jjwt 0.11.5 (JWT), Redis (refresh token storage), Spring Boot 2.7.x

---

### Task 1: Dependencies, Config, and Constants

**Files:**
- Modify: `pom.xml` — add jjwt dependencies
- Modify: `src/main/resources/application.yml` — add JWT config, enable Redis, clean session
- Modify: `src/main/java/com/kun/mianshikun/MainApplication.java` — remove Redis exclude
- Modify: `src/main/java/com/kun/mianshikun/constant/UserConstant.java` — remove USER_LOGIN_STATE

- [ ] **Step 1: Add jjwt dependencies to pom.xml**

Insert these before the `spring-boot-starter-test` dependency:

```xml
<!-- jjwt -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.11.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.11.5</version>
    <scope>runtime</scope>
</dependency>
```

- [ ] **Step 2: Configure application.yml**

In `src/main/resources/application.yml`:

Under `spring:`, add/enable Redis config (remove any comment disabling it). The current Redis config is already present under `# Redis 配置` — just uncomment it or ensure it's active. Remove or comment out the session store-type config.

At the bottom of the file, add JWT custom config:

```yaml
# JWT 配置
jwt:
  secret: mianshikun-jwt-secret-key-2024-change-in-production-environment
  access-token-expire: 900
  refresh-token-expire: 604800
```

Remove session cookie config (`server.servlet.session.cookie.max-age` can stay but is no longer used for auth).

- [ ] **Step 3: Fix MainApplication.java**

Remove the exclude parameter:

```java
// Before
@SpringBootApplication(exclude = {RedisAutoConfiguration.class})

// After
@SpringBootApplication
```

- [ ] **Step 4: Update UserConstant.java**

Remove `USER_LOGIN_STATE`:

```java
// Remove this line:
String USER_LOGIN_STATE = "user_login";
```

Keep `DEFAULT_ROLE`, `ADMIN_ROLE`, `BAN_ROLE`.

- [ ] **Step 5: Commit**

```bash
git add pom.xml src/main/resources/application.yml src/main/java/com/kun/mianshikun/MainApplication.java src/main/java/com/kun/mianshikun/constant/UserConstant.java
git commit -m "feat: add jjwt deps, enable Redis, configure JWT settings"
```

---

### Task 2: Create UserLoginResponse and RefreshTokenResult DTOs

**Files:**
- Create: `src/main/java/com/kun/mianshikun/model/dto/user/UserLoginResponse.java`
- Create: `src/main/java/com/kun/mianshikun/model/dto/user/RefreshTokenResult.java`

- [ ] **Step 1: Create UserLoginResponse.java**

```java
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
```

- [ ] **Step 2: Create RefreshTokenResult.java**

```java
package com.kun.mianshikun.model.dto.user;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class RefreshTokenResult {
    private String token;
    private String tokenId;
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/kun/mianshikun/model/dto/user/UserLoginResponse.java src/main/java/com/kun/mianshikun/model/dto/user/RefreshTokenResult.java
git commit -m "feat: add UserLoginResponse and RefreshTokenResult DTOs"
```

---

### Task 3: Create JwtUtil

**Files:**
- Create: `src/main/java/com/kun/mianshikun/util/JwtUtil.java`

- [ ] **Step 1: Write JwtUtil**

```java
package com.kun.mianshikun.util;

import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class JwtUtil {

    private final SecretKey secretKey;
    private final long accessTokenExpire;
    private final long refreshTokenExpire;

    public JwtUtil(@Value("${jwt.secret}") String secret,
            @Value("${jwt.access-token-expire}") long accessTokenExpire,
            @Value("${jwt.refresh-token-expire}") long refreshTokenExpire) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTokenExpire = accessTokenExpire;
        this.refreshTokenExpire = refreshTokenExpire;
    }

    public String generateAccessToken(User user) {
        Date now = new Date();
        return Jwts.builder()
                .claim("userId", user.getId())
                .claim("userRole", user.getUserRole())
                .claim("unionId", user.getUnionId())
                .claim("mpOpenId", user.getMpOpenId())
                .claim("userName", user.getUserName())
                .claim("userAvatar", user.getUserAvatar())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + accessTokenExpire * 1000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
    }

    public RefreshTokenResult generateRefreshToken(User user) {
        Date now = new Date();
        String tokenId = UUID.randomUUID().toString();
        String token = Jwts.builder()
                .claim("userId", user.getId())
                .claim("tokenId", tokenId)
                .claim("unionId", user.getUnionId())
                .claim("mpOpenId", user.getMpOpenId())
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + refreshTokenExpire * 1000))
                .signWith(secretKey, SignatureAlgorithm.HS256)
                .compact();
        return new RefreshTokenResult(token, tokenId);
    }

    public Claims parseAccessToken(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody();
    }

    public Claims parseRefreshToken(String token) {
        return Jwts.parserBuilder().setSigningKey(secretKey).build()
                .parseClaimsJws(token).getBody();
    }

    public boolean isExpiredException(Exception e) {
        return e instanceof ExpiredJwtException;
    }

    public Long getUserId(Claims claims) {
        return claims.get("userId", Long.class);
    }

    public String getTokenId(Claims claims) {
        return claims.get("tokenId", String.class);
    }

    public String getUserRole(Claims claims) {
        return claims.get("userRole", String.class);
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/util/JwtUtil.java
git commit -m "feat: add JwtUtil for token generation and parsing"
```

---

### Task 4: Create UserContext

**Files:**
- Create: `src/main/java/com/kun/mianshikun/util/UserContext.java`

- [ ] **Step 1: Write UserContext**

```java
package com.kun.mianshikun.util;

import com.kun.mianshikun.model.vo.LoginUserVO;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import javax.servlet.http.HttpServletRequest;

public class UserContext {

    private static final String ATTR_USER_ID = "JWT_USER_ID";
    private static final String ATTR_USER_ROLE = "JWT_USER_ROLE";
    private static final String ATTR_USER_NAME = "JWT_USER_NAME";
    private static final String ATTR_USER_AVATAR = "JWT_USER_AVATAR";
    private static final String ATTR_UNION_ID = "JWT_UNION_ID";
    private static final String ATTR_MP_OPEN_ID = "JWT_MP_OPEN_ID";

    private UserContext() {}

    private static HttpServletRequest getRequest() {
        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();
        if (attrs instanceof ServletRequestAttributes) {
            return ((ServletRequestAttributes) attrs).getRequest();
        }
        return null;
    }

    public static Long getUserId() {
        HttpServletRequest request = getRequest();
        return request != null ? (Long) request.getAttribute(ATTR_USER_ID) : null;
    }

    public static String getUserRole() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_ROLE) : null;
    }

    public static String getUserName() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_NAME) : null;
    }

    public static String getUserAvatar() {
        HttpServletRequest request = getRequest();
        return request != null ? (String) request.getAttribute(ATTR_USER_AVATAR) : null;
    }

    public static LoginUserVO getLoginUser() {
        HttpServletRequest request = getRequest();
        if (request == null) {
            return null;
        }
        Long userId = (Long) request.getAttribute(ATTR_USER_ID);
        if (userId == null) {
            return null;
        }
        LoginUserVO vo = new LoginUserVO();
        vo.setId(userId);
        vo.setUserRole((String) request.getAttribute(ATTR_USER_ROLE));
        vo.setUserName((String) request.getAttribute(ATTR_USER_NAME));
        vo.setUserAvatar((String) request.getAttribute(ATTR_USER_AVATAR));
        return vo;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/util/UserContext.java
git commit -m "feat: add UserContext for static current-user access"
```

---

### Task 5: Create JwtAuthInterceptor and WebMvcConfig

**Files:**
- Create: `src/main/java/com/kun/mianshikun/interceptor/JwtAuthInterceptor.java`
- Create: `src/main/java/com/kun/mianshikun/config/WebMvcConfig.java`

- [ ] **Step 1: Write JwtAuthInterceptor**

```java
package com.kun.mianshikun.interceptor;

import com.kun.mianshikun.common.BaseResponse;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.common.ResultUtils;
import com.kun.mianshikun.model.dto.user.RefreshTokenResult;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.JwtUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import java.io.IOException;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
@Slf4j
public class JwtAuthInterceptor implements HandlerInterceptor {

    private static final String ATTR_USER_ID = "JWT_USER_ID";
    private static final String ATTR_USER_ROLE = "JWT_USER_ROLE";
    private static final String ATTR_USER_NAME = "JWT_USER_NAME";
    private static final String ATTR_USER_AVATAR = "JWT_USER_AVATAR";
    private static final String ATTR_UNION_ID = "JWT_UNION_ID";
    private static final String ATTR_MP_OPEN_ID = "JWT_MP_OPEN_ID";

    @Resource
    private JwtUtil jwtUtil;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private UserService userService;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response,
            Object handler) throws Exception {
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.isBlank(authHeader) || !authHeader.startsWith("Bearer ")) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        String accessToken = authHeader.substring(7);

        try {
            Claims claims = jwtUtil.parseAccessToken(accessToken);
            setRequestAttributes(request, claims);
            return true;
        } catch (ExpiredJwtException e) {
            // Access Token expired, try refresh
            Claims expiredClaims = e.getClaims();
            return tryRefresh(request, response, expiredClaims);
        } catch (Exception e) {
            log.warn("JWT parse failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    private boolean tryRefresh(HttpServletRequest request, HttpServletResponse response,
            Claims expiredClaims) throws IOException {
        String refreshTokenStr = request.getHeader("X-Refresh-Token");
        if (StringUtils.isBlank(refreshTokenStr)) {
            writeUnauthorized(response, "未登录");
            return false;
        }

        try {
            Claims refreshClaims = jwtUtil.parseRefreshToken(refreshTokenStr);
            Long userId = jwtUtil.getUserId(refreshClaims);
            String tokenId = jwtUtil.getTokenId(refreshClaims);

            // Check Redis
            String redisTokenId = stringRedisTemplate.opsForValue().get("refresh_token:" + userId);
            if (redisTokenId == null || !redisTokenId.equals(tokenId)) {
                writeUnauthorized(response, "未登录");
                return false;
            }

            // Generate new token pair
            User user = userService.getById(userId);
            if (user == null) {
                writeUnauthorized(response, "未登录");
                return false;
            }

            String newAccessToken = jwtUtil.generateAccessToken(user);
            RefreshTokenResult newRefresh = jwtUtil.generateRefreshToken(user);

            // Update Redis: new tokenId, same userId key
            stringRedisTemplate.opsForValue().set(
                    "refresh_token:" + userId,
                    newRefresh.getTokenId(),
                    7, java.util.concurrent.TimeUnit.DAYS);

            // Set new tokens in response headers
            response.setHeader("X-Access-Token", newAccessToken);
            response.setHeader("X-Refresh-Token", newRefresh.getToken());

            // Use expired claims user info to proceed current request
            setRequestAttributes(request, expiredClaims);
            return true;
        } catch (Exception e) {
            log.warn("Refresh failed: {}", e.getMessage());
            writeUnauthorized(response, "未登录");
            return false;
        }
    }

    private void setRequestAttributes(HttpServletRequest request, Claims claims) {
        request.setAttribute(ATTR_USER_ID, claims.get("userId", Long.class));
        request.setAttribute(ATTR_USER_ROLE, claims.get("userRole", String.class));
        request.setAttribute(ATTR_USER_NAME, claims.get("userName", String.class));
        request.setAttribute(ATTR_USER_AVATAR, claims.get("userAvatar", String.class));
        request.setAttribute(ATTR_UNION_ID, claims.get("unionId", String.class));
        request.setAttribute(ATTR_MP_OPEN_ID, claims.get("mpOpenId", String.class));
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setContentType("application/json;charset=UTF-8");
        BaseResponse<?> resp = ResultUtils.error(ErrorCode.NOT_LOGIN_ERROR, message);
        ObjectMapper mapper = new ObjectMapper();
        response.getWriter().write(mapper.writeValueAsString(resp));
    }
}
```

- [ ] **Step 2: Write WebMvcConfig**

```java
package com.kun.mianshikun.config;

import com.kun.mianshikun.interceptor.JwtAuthInterceptor;
import javax.annotation.Resource;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    @Resource
    private JwtAuthInterceptor jwtAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(jwtAuthInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/user/register",
                        "/user/login",
                        "/user/login/wx_open",
                        "/",
                        "/setMenu",
                        "/doc.html",
                        "/swagger-resources/**",
                        "/webjars/**",
                        "/v2/**",
                        "/favicon.ico",
                        "/error"
                );
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/kun/mianshikun/interceptor/JwtAuthInterceptor.java src/main/java/com/kun/mianshikun/config/WebMvcConfig.java
git commit -m "feat: add JwtAuthInterceptor and WebMvcConfig"
```

---

### Task 6: Refactor UserService Interface

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/service/UserService.java`

- [ ] **Step 1: Rewrite UserService.java**

```java
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

    UserLoginResponse userLogin(String userAccount, String userPassword);

    UserLoginResponse userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo);

    boolean userLogout(String refreshToken);

    LoginUserVO getLoginUserVO(User user);

    UserVO getUserVO(User user);

    List<UserVO> getUserVO(List<User> userList);

    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);
}
```

Note: `getLoginUser()` and `getLoginUserPermitNull()` are removed. Callers use `UserContext` instead. `isAdmin(HttpServletRequest)` is removed — callers use `UserContext.getUserRole()` directly.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/service/UserService.java
git commit -m "refactor: update UserService interface — remove HttpServletRequest params, add token methods"
```

---

### Task 7: Refactor UserServiceImpl

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/service/impl/UserServiceImpl.java`

- [ ] **Step 1: Rewrite UserServiceImpl.java**

Replace the file content entirely:

```java
package com.kun.mianshikun.service.impl;

import cn.hutool.core.collection.CollUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.constant.CommonConstant;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.exception.ThrowUtils;
import com.kun.mianshikun.mapper.UserMapper;
import com.kun.mianshikun.model.dto.user.UserLoginResponse;
import com.kun.mianshikun.model.dto.user.UserQueryRequest;
import com.kun.mianshikun.model.entity.User;
import com.kun.mianshikun.model.enums.UserRoleEnum;
import com.kun.mianshikun.model.vo.LoginUserVO;
import com.kun.mianshikun.model.vo.UserVO;
import com.kun.mianshikun.service.UserService;
import com.kun.mianshikun.util.JwtUtil;
import com.kun.mianshikun.utils.SqlUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import me.chanjar.weixin.common.bean.WxOAuth2UserInfo;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.BeanUtils;
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
    private StringRedisTemplate stringRedisTemplate;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public long userRegister(String userAccount, String userPassword, String checkPassword) {
        if (StringUtils.isAnyBlank(userAccount, userPassword, checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户账号过短");
        }
        if (userPassword.length() < 8 || checkPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户密码过短");
        }
        if (!userPassword.equals(checkPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次输入的密码不一致");
        }
        synchronized (userAccount.intern()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("userAccount", userAccount);
            long count = this.baseMapper.selectCount(queryWrapper);
            if (count > 0) {
                throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号重复");
            }
            String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
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
    public UserLoginResponse userLogin(String userAccount, String userPassword) {
        if (StringUtils.isAnyBlank(userAccount, userPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号错误");
        }
        if (userPassword.length() < 8) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码错误");
        }
        String encryptPassword = DigestUtils.md5DigestAsHex((SALT + userPassword).getBytes());
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("userAccount", userAccount);
        queryWrapper.eq("userPassword", encryptPassword);
        User user = this.baseMapper.selectOne(queryWrapper);
        if (user == null) {
            log.info("user login failed, userAccount cannot match userPassword");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户不存在或密码错误");
        }
        return buildLoginResponse(user);
    }

    @Override
    public UserLoginResponse userLoginByMpOpen(WxOAuth2UserInfo wxOAuth2UserInfo) {
        String unionId = wxOAuth2UserInfo.getUnionId();
        String mpOpenId = wxOAuth2UserInfo.getOpenid();
        synchronized (unionId.intern()) {
            QueryWrapper<User> queryWrapper = new QueryWrapper<>();
            queryWrapper.eq("unionId", unionId);
            User user = this.getOne(queryWrapper);
            if (user != null && UserRoleEnum.BAN.getValue().equals(user.getUserRole())) {
                throw new BusinessException(ErrorCode.FORBIDDEN_ERROR, "该用户已被封，禁止登录");
            }
            if (user == null) {
                user = new User();
                user.setUnionId(unionId);
                user.setMpOpenId(mpOpenId);
                user.setUserAvatar(wxOAuth2UserInfo.getHeadImgUrl());
                user.setUserName(wxOAuth2UserInfo.getNickname());
                boolean result = this.save(user);
                if (!result) {
                    throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败");
                }
            }
            return buildLoginResponse(user);
        }
    }

    @Override
    public boolean userLogout(String refreshToken) {
        if (StringUtils.isBlank(refreshToken)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "未登录");
        }
        try {
            io.jsonwebtoken.Claims claims = jwtUtil.parseRefreshToken(refreshToken);
            Long userId = jwtUtil.getUserId(claims);
            stringRedisTemplate.delete("refresh_token:" + userId);
        } catch (Exception e) {
            // token invalid, still consider logged out
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
        String unionId = userQueryRequest.getUnionId();
        String mpOpenId = userQueryRequest.getMpOpenId();
        String userName = userQueryRequest.getUserName();
        String userProfile = userQueryRequest.getUserProfile();
        String userRole = userQueryRequest.getUserRole();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();
        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(StringUtils.isNotBlank(unionId), "unionId", unionId);
        queryWrapper.eq(StringUtils.isNotBlank(mpOpenId), "mpOpenId", mpOpenId);
        queryWrapper.eq(StringUtils.isNotBlank(userRole), "userRole", userRole);
        queryWrapper.like(StringUtils.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.like(StringUtils.isNotBlank(userName), "userName", userName);
        queryWrapper.orderBy(SqlUtils.validSortField(sortField), CommonConstant.SORT_ORDER_ASC.equals(sortOrder),
                sortField);
        return queryWrapper;
    }

    private UserLoginResponse buildLoginResponse(User user) {
        String accessToken = jwtUtil.generateAccessToken(user);
        com.kun.mianshikun.model.dto.user.RefreshTokenResult refreshResult = jwtUtil.generateRefreshToken(user);

        // Store refresh token tokenId in Redis
        stringRedisTemplate.opsForValue().set(
                "refresh_token:" + user.getId(),
                refreshResult.getTokenId(),
                7, TimeUnit.DAYS);

        UserLoginResponse response = new UserLoginResponse();
        response.setAccessToken(accessToken);
        response.setRefreshToken(refreshResult.getToken());
        response.setLoginUserVO(getLoginUserVO(user));
        return response;
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/service/impl/UserServiceImpl.java
git commit -m "refactor: replace session logic with JWT + Redis in UserServiceImpl"
```

---

### Task 8: Refactor AuthInterceptor to Use UserContext

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/aop/AuthInterceptor.java`

- [ ] **Step 1: Rewrite AuthInterceptor.java**

```java
package com.kun.mianshikun.aop;

import com.kun.mianshikun.annotation.AuthCheck;
import com.kun.mianshikun.common.ErrorCode;
import com.kun.mianshikun.exception.BusinessException;
import com.kun.mianshikun.model.enums.UserRoleEnum;
import com.kun.mianshikun.util.UserContext;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class AuthInterceptor {

    @Around("@annotation(authCheck)")
    public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
        String mustRole = authCheck.mustRole();
        String userRole = UserContext.getUserRole();
        if (userRole == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        UserRoleEnum mustRoleEnum = UserRoleEnum.getEnumByValue(mustRole);
        if (mustRoleEnum == null) {
            return joinPoint.proceed();
        }
        UserRoleEnum userRoleEnum = UserRoleEnum.getEnumByValue(userRole);
        if (userRoleEnum == null || UserRoleEnum.BAN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        if (UserRoleEnum.ADMIN.equals(mustRoleEnum) && !UserRoleEnum.ADMIN.equals(userRoleEnum)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        }
        return joinPoint.proceed();
    }
}
```

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/aop/AuthInterceptor.java
git commit -m "refactor: AuthInterceptor uses UserContext instead of HttpServletRequest"
```

---

### Task 9: Refactor PostService and PostServiceImpl

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/service/PostService.java`
- Modify: `src/main/java/com/kun/mianshikun/service/impl/PostServiceImpl.java`

- [ ] **Step 1: Update PostService interface**

Change `getPostVO` and `getPostVOPage` signatures:

```java
// Before
PostVO getPostVO(Post post, HttpServletRequest request);
Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request);

// After
PostVO getPostVO(Post post, Long loginUserId);
Page<PostVO> getPostVOPage(Page<Post> postPage, Long loginUserId);
```

Full method signature change in the file — replace the two method declarations:

```java
PostVO getPostVO(Post post, Long loginUserId);

Page<PostVO> getPostVOPage(Page<Post> postPage, Long loginUserId);
```

Remove the `import javax.servlet.http.HttpServletRequest;` line (no longer needed).

- [ ] **Step 2: Update PostServiceImpl.java**

Replace the `getPostVO` method:

```java
@Override
public PostVO getPostVO(Post post, Long loginUserId) {
    PostVO postVO = PostVO.objToVo(post);
    long postId = post.getId();
    // 1. 关联查询用户信息
    Long userId = post.getUserId();
    User user = null;
    if (userId != null && userId > 0) {
        user = userService.getById(userId);
    }
    UserVO userVO = userService.getUserVO(user);
    postVO.setUser(userVO);
    // 2. 已登录，获取用户点赞、收藏状态
    if (loginUserId != null) {
        QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
        postThumbQueryWrapper.in("postId", postId);
        postThumbQueryWrapper.eq("userId", loginUserId);
        PostThumb postThumb = postThumbMapper.selectOne(postThumbQueryWrapper);
        postVO.setHasThumb(postThumb != null);

        QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
        postFavourQueryWrapper.in("postId", postId);
        postFavourQueryWrapper.eq("userId", loginUserId);
        PostFavour postFavour = postFavourMapper.selectOne(postFavourQueryWrapper);
        postVO.setHasFavour(postFavour != null);
    }
    return postVO;
}
```

Replace the `getPostVOPage` method:

```java
@Override
public Page<PostVO> getPostVOPage(Page<Post> postPage, Long loginUserId) {
    List<Post> postList = postPage.getRecords();
    Page<PostVO> postVOPage = new Page<>(postPage.getCurrent(), postPage.getSize(), postPage.getTotal());
    if (CollUtil.isEmpty(postList)) {
        return postVOPage;
    }
    // 1. 关联查询用户信息
    Set<Long> userIdSet = postList.stream().map(Post::getUserId).collect(Collectors.toSet());
    Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
            .collect(Collectors.groupingBy(User::getId));
    // 2. 已登录，获取用户点赞、收藏状态
    Map<Long, Boolean> postIdHasThumbMap = new HashMap<>();
    Map<Long, Boolean> postIdHasFavourMap = new HashMap<>();
    if (loginUserId != null) {
        Set<Long> postIdSet = postList.stream().map(Post::getId).collect(Collectors.toSet());
        QueryWrapper<PostThumb> postThumbQueryWrapper = new QueryWrapper<>();
        postThumbQueryWrapper.in("postId", postIdSet);
        postThumbQueryWrapper.eq("userId", loginUserId);
        List<PostThumb> postPostThumbList = postThumbMapper.selectList(postThumbQueryWrapper);
        postPostThumbList.forEach(postPostThumb -> postIdHasThumbMap.put(postPostThumb.getPostId(), true));

        QueryWrapper<PostFavour> postFavourQueryWrapper = new QueryWrapper<>();
        postFavourQueryWrapper.in("postId", postIdSet);
        postFavourQueryWrapper.eq("userId", loginUserId);
        List<PostFavour> postFavourList = postFavourMapper.selectList(postFavourQueryWrapper);
        postFavourList.forEach(postFavour -> postIdHasFavourMap.put(postFavour.getPostId(), true));
    }
    // 填充信息
    List<PostVO> postVOList = postList.stream().map(post -> {
        PostVO postVO = PostVO.objToVo(post);
        Long postUserId = post.getUserId();
        User user = null;
        if (userIdUserListMap.containsKey(postUserId)) {
            user = userIdUserListMap.get(postUserId).get(0);
        }
        postVO.setUser(userService.getUserVO(user));
        postVO.setHasThumb(postIdHasThumbMap.getOrDefault(post.getId(), false));
        postVO.setHasFavour(postIdHasFavourMap.getOrDefault(post.getId(), false));
        return postVO;
    }).collect(Collectors.toList());
    postVOPage.setRecords(postVOList);
    return postVOPage;
}
```

Remove the `User loginUser = userService.getLoginUserPermitNull(request);` line and `loginUser` usage that's no longer needed. Also remove `import javax.servlet.http.HttpServletRequest;` from the imports.

- [ ] **Step 3: Commit**

```bash
git add src/main/java/com/kun/mianshikun/service/PostService.java src/main/java/com/kun/mianshikun/service/impl/PostServiceImpl.java
git commit -m "refactor: PostService uses loginUserId instead of HttpServletRequest"
```

---

### Task 10: Refactor UserController

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/controller/UserController.java`

- [ ] **Step 1: Update UserController.java**

Key changes:
1. Remove `HttpServletRequest request` and `HttpServletResponse response` parameters from all methods
2. `userLogin()` returns `UserLoginResponse` instead of `LoginUserVO`
3. `userLoginByWxOpen()` returns `UserLoginResponse`
4. `userLogout()` reads refresh token from header instead of request
5. `getLoginUser()` uses `UserContext` instead of service
6. Replace `userService.getLoginUser(request)` with `UserContext.getUserId()`
7. Replace `userService.isAdmin(request)` with role check via `UserContext`

Specific changes per method:

```java
// login:
@PostMapping("/login")
public BaseResponse<UserLoginResponse> userLogin(@RequestBody UserLoginRequest userLoginRequest) {
    if (userLoginRequest == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    String userAccount = userLoginRequest.getUserAccount();
    String userPassword = userLoginRequest.getUserPassword();
    if (StringUtils.isAnyBlank(userAccount, userPassword)) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    return ResultUtils.success(userService.userLogin(userAccount, userPassword));
}

// userLoginByWxOpen:
@GetMapping("/login/wx_open")
public BaseResponse<UserLoginResponse> userLoginByWxOpen(@RequestParam("code") String code) {
    try {
        WxMpService wxService = wxOpenConfig.getWxMpService();
        WxOAuth2AccessToken accessToken = wxService.getOAuth2Service().getAccessToken(code);
        WxOAuth2UserInfo userInfo = wxService.getOAuth2Service().getUserInfo(accessToken, code);
        String unionId = userInfo.getUnionId();
        String mpOpenId = userInfo.getOpenid();
        if (StringUtils.isAnyBlank(unionId, mpOpenId)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，系统错误");
        }
        return ResultUtils.success(userService.userLoginByMpOpen(userInfo));
    } catch (Exception e) {
        log.error("userLoginByWxOpen error", e);
        throw new BusinessException(ErrorCode.SYSTEM_ERROR, "登录失败，系统错误");
    }
}

// logout:
@PostMapping("/logout")
public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
    String refreshToken = request.getHeader("X-Refresh-Token");
    boolean result = userService.userLogout(refreshToken);
    return ResultUtils.success(result);
}

// getLoginUser:
@GetMapping("/get/login")
public BaseResponse<LoginUserVO> getLoginUser() {
    LoginUserVO loginUserVO = UserContext.getLoginUser();
    if (loginUserVO == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }
    return ResultUtils.success(loginUserVO);
}
```

For other methods (add, delete, update, listPage, updateMy etc.), remove `HttpServletRequest request` parameter and replace `userService.getLoginUser(request)` with `UserContext.getUserId()`. Also keep `HttpServletRequest request` in logout since we need to read the `X-Refresh-Token` header (that's the only place it's needed in the whole app).

Specifically:

```java
// addUser — remove request param
@PostMapping("/add")  
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) { ... }

// deleteUser — remove request param
@PostMapping("/delete")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE) 
public BaseResponse<Boolean> deleteUser(@RequestBody DeleteRequest deleteRequest) { ... }

// updateUser — remove request param
@PostMapping("/update")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest) { ... }

// getUserById — remove request param
@GetMapping("/get")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<User> getUserById(long id) { ... }

// getUserVOById — remove request param
@GetMapping("/get/vo")
public BaseResponse<UserVO> getUserVOById(long id) { ... }

// listUserByPage — remove request param
@PostMapping("/list/page")
@AuthCheck(mustRole = UserConstant.ADMIN_ROLE)
public BaseResponse<Page<User>> listUserByPage(@RequestBody UserQueryRequest userQueryRequest) { ... }

// listUserVOByPage — remove request param
@PostMapping("/list/page/vo")
public BaseResponse<Page<UserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) { ... }

// updateMyUser — keep request param for reading header? No, use UserContext
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
```

Clean up unused imports: remove `javax.servlet.http.HttpServletResponse` if no longer referenced.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/controller/UserController.java
git commit -m "refactor: UserController returns tokens, uses UserContext"
```

---

### Task 11: Refactor PostController, PostFavourController, PostThumbController

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/controller/PostController.java`
- Modify: `src/main/java/com/kun/mianshikun/controller/PostFavourController.java`
- Modify: `src/main/java/com/kun/mianshikun/controller/PostThumbController.java`

- [ ] **Step 1: Refactor PostController.java**

For every method that takes `HttpServletRequest request`:
1. Remove the parameter
2. Replace `userService.getLoginUser(request)` → `UserContext.getUserId()`
3. Where user object is needed for `getUserId()`, use `UserContext.getUserId()`
4. Where `userService.isAdmin(request)` is called, use `UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())`

For `getPostVO` callers, pass `UserContext.getUserId()` instead of `request`.

Specific changes:

```java
// addPost — remove request param
@PostMapping("/add")
public BaseResponse<Long> addPost(@RequestBody PostAddRequest postAddRequest) {
    ...
    Long userId = UserContext.getUserId();
    post.setUserId(userId);
    ...
}

// deletePost — remove request param
@PostMapping("/delete")
public BaseResponse<Boolean> deletePost(@RequestBody DeleteRequest deleteRequest) {
    ...
    Long userId = UserContext.getUserId();
    // ... check ownership
    if (!oldPost.getUserId().equals(userId) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }
    ...
}

// getPostVOById — remove request param, use UserContext
@GetMapping("/get/vo")
public BaseResponse<PostVO> getPostVOById(long id) {
    ...
    return ResultUtils.success(postService.getPostVO(post, UserContext.getUserId()));
}

// listPostVOByPage — remove request param
@PostMapping("/list/page/vo")
public BaseResponse<Page<PostVO>> listPostVOByPage(@RequestBody PostQueryRequest postQueryRequest) {
    ...
    return ResultUtils.success(postService.getPostVOPage(postPage, UserContext.getUserId()));
}

// listMyPostVOByPage — remove request param
@PostMapping("/my/list/page/vo")
public BaseResponse<Page<PostVO>> listMyPostVOByPage(@RequestBody PostQueryRequest postQueryRequest) {
    ...
    Long userId = UserContext.getUserId();
    postQueryRequest.setUserId(userId);
    ...
}

// searchPostVOByPage — remove request param
@PostMapping("/search/page/vo")
public BaseResponse<Page<PostVO>> searchPostVOByPage(@RequestBody PostQueryRequest postQueryRequest) {
    ...
    return ResultUtils.success(postService.getPostVOPage(postPage, UserContext.getUserId()));
}

// editPost — remove request param
@PostMapping("/edit")
public BaseResponse<Boolean> editPost(@RequestBody PostEditRequest postEditRequest) {
    ...
    Long userId = UserContext.getUserId();
    if (!oldPost.getUserId().equals(userId) && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())) {
        throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
    }
    ...
}
```

Remove all `import javax.servlet.http.HttpServletRequest;` from PostController. Also remove `import com.kun.mianshikun.model.entity.User;` if no longer used.

- [ ] **Step 2: Refactor PostFavourController.java**

Remove `HttpServletRequest request` from all methods. Replace `userService.getLoginUser(request)` with `UserContext.getUserId()`.

```java
@PostMapping("/")
public BaseResponse<Integer> doPostFavour(@RequestBody PostFavourAddRequest postFavourAddRequest) {
    if (postFavourAddRequest == null || postFavourAddRequest.getPostId() <= 0) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    Long userId = UserContext.getUserId();
    if (userId == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }
    long postId = postFavourAddRequest.getPostId();
    // doPostFavour expects a User object; create minimal one with just the ID
    User loginUser = new User();
    loginUser.setId(userId);
    int result = postFavourService.doPostFavour(postId, loginUser);
    return ResultUtils.success(result);
}

@PostMapping("/my/list/page")
public BaseResponse<Page<PostVO>> listMyFavourPostByPage(@RequestBody PostQueryRequest postQueryRequest) {
    if (postQueryRequest == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    Long userId = UserContext.getUserId();
    long current = postQueryRequest.getCurrent();
    long size = postQueryRequest.getPageSize();
    ThrowUtils.throwIf(size > 20, ErrorCode.PARAMS_ERROR);
    Page<Post> postPage = postFavourService.listFavourPostByPage(new Page<>(current, size),
            postService.getQueryWrapper(postQueryRequest), userId);
    return ResultUtils.success(postService.getPostVOPage(postPage, userId));
}

@PostMapping("/list/page")
public BaseResponse<Page<PostVO>> listFavourPostByPage(@RequestBody PostFavourQueryRequest postFavourQueryRequest) {
    if (postFavourQueryRequest == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    long current = postFavourQueryRequest.getCurrent();
    long size = postFavourQueryRequest.getPageSize();
    Long userIdParam = postFavourQueryRequest.getUserId();
    ThrowUtils.throwIf(size > 20 || userIdParam == null, ErrorCode.PARAMS_ERROR);
    Page<Post> postPage = postFavourService.listFavourPostByPage(new Page<>(current, size),
            postService.getQueryWrapper(postFavourQueryRequest.getPostQueryRequest()), userIdParam);
    return ResultUtils.success(postService.getPostVOPage(postPage, UserContext.getUserId()));
}
```

Remove `HttpServletRequest` import. The `UserService` may no longer be needed in PostFavourController — remove `@Resource private UserService userService;` and its import.

Actually, keep `UserService` if it's used elsewhere in the class. From the current code, `UserService` is only used via `userService.getLoginUser(request)`, which we're replacing. So we can remove the `UserService` field and import.

- [ ] **Step 3: Refactor PostThumbController.java**

```java
@PostMapping("/")
public BaseResponse<Integer> doThumb(@RequestBody PostThumbAddRequest postThumbAddRequest) {
    if (postThumbAddRequest == null || postThumbAddRequest.getPostId() <= 0) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    Long userId = UserContext.getUserId();
    if (userId == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }
    long postId = postThumbAddRequest.getPostId();
    // doPostThumb expects a User object; create minimal one with just the ID
    User loginUser = new User();
    loginUser.setId(userId);
    int result = postThumbService.doPostThumb(postId, loginUser);
    return ResultUtils.success(result);
}
```

Remove `HttpServletRequest` and `UserService` imports.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/kun/mianshikun/controller/PostController.java src/main/java/com/kun/mianshikun/controller/PostFavourController.java src/main/java/com/kun/mianshikun/controller/PostThumbController.java
git commit -m "refactor: Post/PostFavour/PostThumb controllers use UserContext"
```

---

### Task 12: Refactor FileController

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/controller/FileController.java`

- [ ] **Step 1: Refactor FileController.java**

Remove `HttpServletRequest request` parameter from `uploadFile()`. Replace `userService.getLoginUser(request)` with `UserContext.getUserId()`:

```java
@PostMapping("/upload")
public BaseResponse<String> uploadFile(@RequestPart("file") MultipartFile multipartFile,
        UploadFileRequest uploadFileRequest) {
    String biz = uploadFileRequest.getBiz();
    FileUploadBizEnum fileUploadBizEnum = FileUploadBizEnum.getEnumByValue(biz);
    if (fileUploadBizEnum == null) {
        throw new BusinessException(ErrorCode.PARAMS_ERROR);
    }
    validFile(multipartFile, fileUploadBizEnum);
    Long userId = UserContext.getUserId();
    if (userId == null) {
        throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
    }
    // 文件目录：根据业务、用户来划分
    String uuid = RandomStringUtils.randomAlphanumeric(8);
    String filename = uuid + "-" + multipartFile.getOriginalFilename();
    String filepath = String.format("/%s/%s/%s", fileUploadBizEnum.getValue(), userId, filename);
    ...
}
```

Remove `HttpServletRequest` import. `UserService` may still be needed — check. Actually `UserService` is no longer used after the change, so remove that field and import too.

- [ ] **Step 2: Commit**

```bash
git add src/main/java/com/kun/mianshikun/controller/FileController.java
git commit -m "refactor: FileController uses UserContext"
```

---

### Task 13: Refactor QuestionController, QuestionBankController, QuestionBankQuestionController

**Files:**
- Modify: `src/main/java/com/kun/mianshikun/controller/QuestionController.java`
- Modify: `src/main/java/com/kun/mianshikun/controller/QuestionBankController.java`
- Modify: `src/main/java/com/kun/mianshikun/controller/QuestionBankQuestionController.java`

- [ ] **Step 1: Refactor QuestionController.java**

For every method that takes `HttpServletRequest request`:
1. Remove the parameter
2. Replace `userService.getLoginUser(request)` with `UserContext.getUserId()`
3. Replace `userService.isAdmin(loginUser)` with `UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())`

Remove `HttpServletRequest` import. Remove `UserService` field and its import if no longer used. (Check: UserService might still be used if there's a `userService.getLoginUser()` call).

From the current QuestionController, `userService` is used in:
- `addQuestion`: `User loginUser = userService.getLoginUser(request)` → replace with `Long userId = UserContext.getUserId()`
- `deleteQuestion`: same pattern
- `editQuestion`: same pattern
- `listMyQuestionVOByPage`: same pattern
- `batchDeleteQuestions`: same pattern

After all replacements, `UserService` is no longer used. Remove:

```java
// Remove these:
import javax.servlet.http.HttpServletRequest;
@Resource
private UserService userService;
```

- [ ] **Step 2: Refactor QuestionBankController.java**

Same pattern as QuestionController. Remove `HttpServletRequest` and `UserService` after replacing all `getLoginUser` calls with `UserContext.getUserId()`.

- [ ] **Step 3: Refactor QuestionBankQuestionController.java**

Same pattern. Additionally, `batchAddQuestionsToBank` uses `UserService.getLoginUser()` — replace with UserContext.

Remove `HttpServletRequest` and `UserService` imports.

- [ ] **Step 4: Commit**

```bash
git add src/main/java/com/kun/mianshikun/controller/QuestionController.java src/main/java/com/kun/mianshikun/controller/QuestionBankController.java src/main/java/com/kun/mianshikun/controller/QuestionBankQuestionController.java
git commit -m "refactor: Question/QuestionBank/QuestionBankQuestion controllers use UserContext"
```

---

### Task 14: Full Build Verification

**Files:** (no changes)

- [ ] **Step 1: Clean compile**

Run:
```bash
cd /d D:\Java_project\mianshikun\mianshikun && .\mvnw clean compile
```

Expected: `BUILD SUCCESS` (0 errors)

If there are errors in Controllers that still reference the old `UserService` login methods or `HttpServletRequest`, check each one and replace with `UserContext`.

- [ ] **Step 2: Handle compile errors**

If compile errors occur, they will likely be one of:
1. `cannot find symbol method getLoginUser(HttpServletRequest)` — replace with `UserContext.getUserId()`
2. `cannot find symbol method isAdmin(HttpServletRequest)` — replace with `UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole())`
3. `cannot find symbol variable request` — remove the parameter declaration from method signature

Fix each error and recompile.

- [ ] **Step 3: Final commit**

```bash
git add -A
git commit -m "fix: resolve compilation errors from JWT migration"
```
