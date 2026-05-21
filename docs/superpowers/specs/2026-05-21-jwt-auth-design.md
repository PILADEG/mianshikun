# Session 到 JWT 认证改造设计

## 概述

将项目当前基于 `HttpSession` 的用户登录态方案，改造为基于 **JWT（Access Token + Refresh Token）+ Redis** 的无状态认证方案。前端通过 `Authorization: Bearer <token>` 头传递令牌，Access Token 过期时通过 Refresh Token 无感自动续期。

## 背景

当前认证方案：

| 操作 | 实现方式 |
|------|---------|
| 登录 | `request.getSession().setAttribute("user_login", user)` |
| 鉴权 | `request.getSession().getAttribute("user_login")` |
| 注销 | `request.getSession().removeAttribute("user_login")` |
| 权限校验 | AOP `AuthInterceptor` → `UserService.getLoginUser(request)` |

问题：
- Session 方案对前端不友好（需要 Cookie 支持）
- 不利于前后端分离和移动端接入
- 分布式部署需引入 Redis Session，增加复杂度
- 无状态 JWT 更适合 API 服务

## 影响范围

- **8 个 Controller**：UserController、PostController、PostFavourController、PostThumbController、FileController、QuestionController、QuestionBankController、QuestionBankQuestionController
- **1 个 AOP 切面**：AuthInterceptor
- **Service 层**：UserService / UserServiceImpl
- **依赖**：新增 jjwt、启用 Redis
- **配置**：application.yml、MainApplication.java

## Token 结构

### Access Token

有效期：**15 分钟**

载荷：

```json
{
  "userId": 12345,
  "userRole": "admin",
  "unionId": "o_xxxx",
  "mpOpenId": "gh_xxxx",
  "userName": "张三",
  "userAvatar": "https://...",
  "exp": 1747728000,
  "iat": 1747727100
}
```

签名算法：HMAC-SHA256，密钥配置在 `application.yml`

### Refresh Token

有效期：**7 天**

载荷：

```json
{
  "userId": 12345,
  "tokenId": "uuid-string",
  "unionId": "o_xxxx",
  "mpOpenId": "gh_xxxx",
  "exp": 1748332800,
  "iat": 1747727100
}
```

`tokenId` 为随机 UUID，用于 Redis 存储标识。

### Redis 存储

```
Key: refresh_token:{tokenId}
Value: userId (String)
TTL: 7 天
```

每次刷新时轮换 Refresh Token：生成新的 `tokenId` 写入 Redis，删除旧的。

## 请求处理流程

```
请求到达
  ├─ 在 exclude 列表中 → 直接放行
  └─ 不在 exclude 列表中
       ├─ 无 Authorization 头 → 返回 401（NOT_LOGIN_ERROR）
       └─ 有 Authorization 头 → JwtAuthInterceptor 处理
            ├─ Access Token 有效
            │   └─ 解析载荷，写入 request 属性（userId, userRole, userName 等）
            │   └─ 放行
            └─ Access Token 过期
                 ├─ 有 X-Refresh-Token 头
                 │   ├─ 校验 JWT 签名 + Redis 中存在
                 │   │   ├─ 生成新 Access Token + 新 Refresh Token
                 │   │   ├─ Redis 更新：删旧 refresh_token，写新 refresh_token
                 │   │   ├─ 响应头设置 X-Access-Token / X-Refresh-Token
                 │   │   └─ 用旧 Access Token 的用户信息继续处理本次请求
                 │   └─ 无效 → 返回 401（NOT_LOGIN_ERROR）
                 └─ 无 X-Refresh-Token 头 → 返回 401
```

### 公开接口（无需 token）

| 路径 | 方法 | 说明 |
|------|------|------|
| `/user/register` | POST | 用户注册 |
| `/user/login` | POST | 用户登录 |
| `/user/login/wx_open` | GET | 微信登录 |
| `/` | GET | 微信消息验证 |
| `/` | POST | 接收微信消息 |
| `/setMenu` | GET | 设置公众号菜单 |
| `/doc.html` | GET | Knife4j 接口文档 |
| `/swagger-resources/**` | GET | Swagger 资源 |
| `/webjars/**` | GET | WebJars |
| `/v2/**` | GET | Swagger API |
| `/favicon.ico` | GET | 图标 |
| `/error` | ANY | 错误响应 |

## 组件设计

### 新增组件

#### JwtUtil（工具类）

```java
public class JwtUtil {
    // 生成 Access Token（含 userId, userRole, unionId, mpOpenId, userName, userAvatar）
    String generateAccessToken(User user);

    // 从 Access Token 解析 Claims
    Claims parseAccessToken(String token);

    // 生成 Refresh Token（含随机 tokenId）
    RefreshTokenResult generateRefreshToken(User user);

    // 验证 Refresh Token 并返回 Claims
    Claims parseRefreshToken(String token);

    // 判断异常是否为 token 过期
    boolean isExpiredException(Exception e);

    // 从 Claims 提取 userId
    Long getUserId(Claims claims);
}
```

#### UserContext（当前用户上下文）

```java
public class UserContext {
    static Long getUserId();
    static String getUserRole();
    static String getUserName();
    static String getUserAvatar();
    static LoginUserVO getLoginUser();  // 组装 VO
}
```

基于 Spring `RequestContextHolder` + Request attributes 实现，不依赖 `HttpServletRequest` 方法参数。

#### JwtAuthInterceptor（HandlerInterceptor）

核心拦截逻辑：

```
preHandle()
  1. 检查是否 exclude 路径 → 返回 true
  2. 提取 Authorization 头 → 无则返回 401 JSON
  3. 解析 Access Token
     - 成功 → 设置 request attributes → 返回 true
     - 过期 → 尝试 Refresh Token
       - 成功 → 生成新 token 对，写 Redis，设响应头，用旧信息放行
       - 失败 → 返回 401 JSON
     - 其他异常 → 返回 401 JSON
```

注意：拦截器返回的 401 响应必须使用 `BaseResponse` 格式，与 Controller 保持一致。

响应的 JSON 格式示例：

```json
{
  "code": 40100,
  "data": null,
  "message": "未登录"
}
```

#### WebMvcConfig（注册拦截器）

```java
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {
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

#### UserLoginResponse（登录响应 DTO）

```java
@Data
public class UserLoginResponse implements Serializable {
    private String accessToken;
    private String refreshToken;
    private LoginUserVO loginUserVO;
    private static final long serialVersionUID = 1L;
}
```

### 修改组件

#### UserService 接口

| 方法 | 旧签名 | 新签名 |
|------|--------|--------|
| userRegister | `(String account, String pwd, String checkPwd)` | 不变 |
| userLogin | `(String account, String pwd, HttpServletRequest request)` | `(String account, String pwd)` |
| userLoginByMpOpen | `(WxOAuth2UserInfo info, HttpServletRequest request)` | `(WxOAuth2UserInfo info)` |
| getLoginUser | `(HttpServletRequest request)` | **移除** |
| getLoginUserPermitNull | `(HttpServletRequest request)` | **移除** |
| isAdmin | `(HttpServletRequest request)` | **移除**（保留 `(User user)` 重载） |
| userLogout | `(HttpServletRequest request)` | `(String refreshToken)` |

新增方法：

```java
UserLoginResponse userLoginWithToken(String userAccount, String userPassword);
UserLoginResponse userLoginByMpOpenWithToken(WxOAuth2UserInfo wxOAuth2UserInfo);
```

#### UserServiceImpl 关键变更

**登录（userLoginWithToken）：**
```
1. 校验账号密码（逻辑不变）
2. 生成 Access Token → JwtUtil.generateAccessToken(user)
3. 生成 Refresh Token → JwtUtil.generateRefreshToken(user)
4. 写入 Redis → redisTemplate.opsForValue().set(
       "refresh_token:" + refreshToken.getTokenId(),
       user.getId().toString(),
       7, TimeUnit.DAYS)
5. 返回 UserLoginResponse(accessToken, refreshToken, loginUserVO)
```

**注销（userLogout）：**
```
1. 解析 Refresh Token 获取 tokenId
2. 删除 Redis key: redisTemplate.delete("refresh_token:" + tokenId)
3. 返回 true
```

**getLoginUser / getLoginUserPermitNull 移除**，改由 `JwtAuthInterceptor` + `UserContext` 替代。

#### AuthInterceptor（AOP 切面）

```java
@Around("@annotation(authCheck)")
public Object doInterceptor(ProceedingJoinPoint joinPoint, AuthCheck authCheck) throws Throwable {
    String mustRole = authCheck.mustRole();
    // 从 UserContext 获取用户角色（由 JwtAuthInterceptor 注入 Request 属性）
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
```

不再注入 `UserService`，不再获取 `HttpServletRequest`。

#### Controller 层改造模式

全部 8 个 Controller 中，将所有 `HttpServletRequest request` 参数 + `userService.getLoginUser(request)` 替换为 `UserContext.getUserId()` / `UserContext.getUserRole()`。

**改造示例（QuestionController）：**

```java
// 改造前
@PostMapping("/add")
public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest,
        HttpServletRequest request) {
    ...
    User loginUser = userService.getLoginUser(request);
    question.setUserId(loginUser.getId());
    ...
}

// 改造后
@PostMapping("/add")
public BaseResponse<Long> addQuestion(@RequestBody QuestionAddRequest questionAddRequest) {
    ...
    question.setUserId(UserContext.getUserId());
    ...
}
```

**带权限判断的改造：**

```java
// 改造前
if (!oldPost.getUserId().equals(loginUser.getId()) && !userService.isAdmin(request))

// 改造后  
if (!oldPost.getUserId().equals(UserContext.getUserId()) 
    && !UserConstant.ADMIN_ROLE.equals(UserContext.getUserRole()))
```

**UserController 特殊处理：**

- 登录接口返回 `UserLoginResponse`（含 token）
- `/get/login` 从 Access Token 解析用户信息（不再查 DB）
- 注销接口接收 `X-Refresh-Token`（从参数或 Header 获取）

#### 配置变更

**pom.xml：**
- 添加 jjwt-api / jjwt-impl / jjwt-jackson (0.11.5)

**MainApplication.java：**
- 移除 `exclude = {RedisAutoConfiguration.class}`

**application.yml：**
```yaml
spring:
  redis:
    database: 0
    host: localhost
    port: 6379
    timeout: 5000

# session 配置移除
# server.session.cookie 移除

# JWT 配置（自定义）
jwt:
  secret: your-256-bit-secret-key-here-change-in-production
  access-token-expire: 900       # 15 分钟
  refresh-token-expire: 604800   # 7 天
```

**Redis 需要可用。** 当前配置指向 `localhost:6379`，可根据环境调整。

#### PostService 接口

`PostService.getPostVO()` 和 `getPostVOPage()` 当前接收 `HttpServletRequest` 以获取当前用户对帖子的点赞/收藏状态：

```java
// 旧
PostVO getPostVO(Post post, HttpServletRequest request);
Page<PostVO> getPostVOPage(Page<Post> postPage, HttpServletRequest request);

// 新
PostVO getPostVO(Post post, Long loginUserId);
Page<PostVO> getPostVOPage(Page<Post> postPage, Long loginUserId);
```

Controller 中调用时传入 `UserContext.getUserId()`，允许为 null（未登录时点赞/收藏状态均为 false）：

```java
// 改造后 Controller
postService.getPostVO(post, UserContext.getUserId());
```

`PostVO` 中的 `hasThumb` / `hasFavour` 字段在 `loginUserId` 为 null 时默认为 false。

#### UserConstant

移除 `USER_LOGIN_STATE` 常量（不再使用），保留 `DEFAULT_ROLE`、`ADMIN_ROLE`、`BAN_ROLE`。

## 前端适配

### 存储
- `accessToken` → localStorage
- `refreshToken` → localStorage

### 请求头
```javascript
// 每次请求
headers: {
    'Authorization': 'Bearer ' + localStorage.getItem('accessToken'),
    'X-Refresh-Token': localStorage.getItem('refreshToken')
}
```

### 响应拦截
```javascript
// 每次响应后检查
if (response.headers['X-Access-Token']) {
    localStorage.setItem('accessToken', response.headers['X-Access-Token']);
}
if (response.headers['X-Refresh-Token']) {
    localStorage.setItem('refreshToken', response.headers['X-Refresh-Token']);
}
```

### 登录处理
```javascript
const res = await fetch('/user/login', { method: 'POST', body: {...} });
const { accessToken, refreshToken, loginUserVO } = res.data;
localStorage.setItem('accessToken', accessToken);
localStorage.setItem('refreshToken', refreshToken);
```

### 401 处理
```javascript
if (res.code === 40100) {
    localStorage.removeItem('accessToken');
    localStorage.removeItem('refreshToken');
    // 重定向到登录页
}
```

## 文件变更清单

### 新增文件（4 个）
| 文件 | 路径 |
|------|------|
| JwtUtil | `src/main/java/com/kun/mianshikun/util/JwtUtil.java` |
| UserContext | `src/main/java/com/kun/mianshikun/util/UserContext.java` |
| JwtAuthInterceptor | `src/main/java/com/kun/mianshikun/interceptor/JwtAuthInterceptor.java` |
| WebMvcConfig | `src/main/java/com/kun/mianshikun/config/WebMvcConfig.java` |
| UserLoginResponse | `src/main/java/com/kun/mianshikun/model/dto/user/UserLoginResponse.java` |

### 修改文件（17 个）
| 文件 | 变更内容 |
|------|---------|
| pom.xml | 添加 jjwt 依赖 |
| application.yml | 添加 jwt 配置、启用 Redis、移除 session 配置 |
| MainApplication.java | 移除 Redis exclude |
| UserConstant.java | 移除 USER_LOGIN_STATE |
| UserService.java | 方法签名调整（移除 HttpServletRequest 参数） |
| UserServiceImpl.java | 替换 session 为 JWT/Redis |
| PostService.java | getPostVO / getPostVOPage 签名改为接受 loginUserId |
| PostServiceImpl.java | getPostVO / getPostVOPage 改为使用 loginUserId 查点赞/收藏 |
| AuthInterceptor.java | 改用 UserContext |
| UserController.java | 登录返回 token，删除 request 参数 |
| PostController.java | 删除 request 参数，UserContext 替换 |
| PostFavourController.java | 同上 |
| PostThumbController.java | 同上 |
| FileController.java | 同上 |
| QuestionController.java | 同上 |
| QuestionBankController.java | 同上 |
| QuestionBankQuestionController.java | 同上 |

## 错误处理

| 场景 | HTTP 状态码 | 响应 code | 响应 message |
|------|------------|-----------|-------------|
| 无 Authorization 头 | 200 | 40100 | 未登录 |
| Access Token 过期且无 Refresh Token | 200 | 40100 | 未登录 |
| Refresh Token 无效 | 200 | 40100 | 未登录 |
| Refresh Token 被篡改 | 200 | 40100 | 未登录 |
| 账号被封 | 200 | 40101 | 无权限 |
| 非管理员访问管理员接口 | 200 | 40101 | 无权限 |

**状态码统一使用 200，通过 `code` 字段区分**，与项目现有规范一致。

## 边界情况

1. **并发刷新**：同时间多个请求进来，Access Token 过期，多个线程都尝试刷新。后刷新的 Refresh Token 写入 Redis 后，先刷新的 Refresh Token 的 tokenId 会被删除，但先刷新的请求已经拿到旧 Refresh Token 的新 token 对，不影响。极端情况下，同一时刻两个请求都刷新成功，前端收到两次不同的新 token，最后一次写入 localStorage 的值生效。

2. **Refresh Token 失效场景**：用户注销 → Redis 中删除 → 后续任何使用该 Refresh Token 的请求都会失败。

3. **Redis 宕机**：Access Token 仍可解析（JWT 验证不依赖 Redis），但 Refresh Token 刷新会失败。此时已登录用户的现有请求在 Access Token 有效期内可正常处理，过期后将无法刷新，需要重新登录。

4. **token 被篡改**：JJWT 库验证签名失败，抛出异常，拦截器捕获后返回 401。
