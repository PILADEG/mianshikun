# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**面试鸭** (mianshikun) — 基于 Spring Boot 2.7.x 的面试刷题平台后端。核心业务：题目管理、题库管理、模拟面试（AI 对话）、帖子/点赞/收藏、微信公众平台集成。

**基础包**: `com.kun.mianshikun`

## Build & Run

```bash
# 构建（跳过测试）
./mvnw clean package -DskipTests

# 本地运行（默认 dev 环境）
./mvnw spring-boot:run

# 运行测试
./mvnw test

# 运行单个测试
./mvnw test -Dtest=UserServiceTest

# Docker 构建（默认激活 prod 环境）
docker build -t mianshikun .
```

## Architecture

### 分层结构 (Standard 4-layer)

```
controller → service(impl) → mapper → mysql
     ↕            ↕
    dto          entity/vo
```

- **controller** — REST 接口，接收 DTO 请求参数，返回统一 `BaseResponse<T>`
- **service/impl** — 业务逻辑层（已实现 User/Post/Question/QuestionBank 等 Service）
- **mapper** — MyBatis-Plus Mapper 接口，对应 resources/mapper/*.xml
- **model/entity** — 数据库实体，带 `@TableLogic` 逻辑删除
- **model/dto** — 请求参数（Add/Update/Delete/Query 等）
- **model/vo** — 返回视图对象（脱敏，如 LoginUserVO 不返回密码）

### 核心模块

| 模块 | 说明 | 关键表 |
|------|------|--------|
| User | 注册/登录(JWT)/微信开放平台登录/签到 | user |
| Question | 题目 CRUD + ES 搜索 + AI 生成(待接入) | question |
| QuestionBank | 题库 CRUD，与题目多对多关联 | question_bank, question_bank_question |
| Post | 帖子 CRUD + ES 搜索 | post |
| PostFavour | 帖子收藏 | post_favour |
| PostThumb | 帖子点赞 | post_thumb |
| File | 腾讯云 COS 文件上传 | — |
| WX MP | 微信公众号消息/菜单 | — |

### 公共组件

- **common/** — `BaseResponse`, `ErrorCode` (枚举), `ResultUtils` (快速响应), `PageRequest`, `DeleteRequest`
- **exception/** — `BusinessException` (自定义异常), `GlobalExceptionHandler` (全局处理 + JSON 解析错误增强)
- **annotation/AuthCheck** + **aop/AuthInterceptor** — 基于注解的权限校验 (user/admin/ban)
- **aop/LogInterceptor** — 全局请求日志（AOP 记录耗时+参数）
- **config/** — `CorsConfig` (跨域), `CosClientConfig` (对象存储), `MyBatisPlusConfig` (分页), `JsonConfig` (Long → String), `WebMvcConfig` (JWT 拦截器注册), `WxOpenConfig`
- **interceptor/JwtAuthInterceptor** — JWT 访问令牌校验 + 自动刷新拦截器
- **util/JwtUtil** — JWT 令牌生成/解析
- **util/UserContext** — 通过 `RequestContextHolder` 获取当前登录用户信息
- **constant/** — `UserConstant` (角色/状态), `CommonConstant` (排序), `FileConstant` (COS 域名)
- **job/** — `FullSyncPostToEs`, `IncSyncPostToEs` (ES 同步定时任务)
- **manager/** — `CosManager` (腾讯云 COS 封装)
- **esdao/** — `PostEsDao` (Elasticsearch Repository)
- **generate/CodeGenerator** — 代码生成器

### 数据存储

- **MySQL** — 主数据库，MyBatis + MyBatis-Plus (分页插件)
- **Redis** — 必须（存储 JWT refresh token），默认本地 `localhost:6379`
- **Elasticsearch** — Post/Question 全文搜索，默认关闭（需取消 `application.yml` 注释）
- **腾讯云 COS** — 文件/图片存储

### 多环境配置

- `application.yml` — 公共配置（默认 dev）
- `application-test.yml` — 测试环境
- `application-prod.yml` — 生产环境（Docker 默认激活）

---

## 前端对接规范

### 基础信息

| 项目 | 值 |
|------|-----|
| 基础 URL | `http://localhost:8101/api` |
| Content-Type | `application/json`（文件上传使用 `multipart/form-data`） |
| 接口文档（dev） | `http://localhost:8101/api/doc.html` (Knife4j/Swagger) |
| 字符编码 | UTF-8 |

### 统一响应格式

```typescript
interface BaseResponse<T> {
  code: number;    // 0 成功，其他见错误码
  data: T;         // 响应数据
  message: string; // 提示信息
}
```

### 错误码

| code | 说明 |
|------|------|
| 0 | ok |
| 40000 | 请求参数错误 |
| 40100 | 未登录（需跳转登录页） |
| 40101 | 无权限 |
| 40300 | 禁止访问 |
| 40400 | 请求数据不存在 |
| 50000 | 系统内部异常 |
| 50001 | 操作失败 |

### JWT 认证流程（核心）

本项目使用 **Access Token + Refresh Token** 双令牌机制，**不依赖 Cookie/Session**。

#### 登录

```
POST /user/login
Body: { "userAccount": "xxx", "userPassword": "xxx" }
Response: BaseResponse<UserLoginResponse>

interface UserLoginResponse {
  accessToken: string;   // 访问令牌（15 分钟过期）
  refreshToken: string;  // 刷新令牌（7 天过期）
  loginUserVO: LoginUserVO;
}
```

登录成功后，前端需**持久化存储** `accessToken` 和 `refreshToken`（如 localStorage）。

#### 请求认证

所有请求（除 `/user/register`、`/user/login`、`/user/login/wx_open`、`/`）必须在 HTTP 头中携带：

```
Authorization: Bearer {accessToken}
```

#### 令牌自动刷新机制

当 `accessToken` 过期时，后端 `JwtAuthInterceptor` 会返回 **40100 未登录**。前端需判断此响应后，使用 `refreshToken` 发起自动刷新：

**流程**:
1. 原请求收到 `40100` → 检查是否有 `refreshToken`
2. 用 `refreshToken` 重放原请求，在 HTTP 头中额外添加：
   ```
   X-Refresh-Token: {refreshToken}
   ```
3. 后端验证通过后，在**响应头**中返回新令牌：
   ```
   X-Access-Token: {newAccessToken}
   X-Refresh-Token: {newRefreshToken}
   ```
4. 前端更新本地存储的令牌，并使用新 `accessToken` 重试原请求

```typescript
// 前端建议的请求拦截器逻辑
async function request(url, options) {
  const res = await fetch(url, {
    ...options,
    headers: {
      'Authorization': `Bearer ${getAccessToken()}`,
      'Content-Type': 'application/json',
      ...options.headers
    }
  });

  if (res.status === 200) {
    const body = await res.json();
    // 令牌刷新响应头
    const newAccessToken = res.headers.get('X-Access-Token');
    const newRefreshToken = res.headers.get('X-Refresh-Token');
    if (newAccessToken) {
      setAccessToken(newAccessToken);
      setRefreshToken(newRefreshToken);
    }
    if (body.code !== 0) {
      if (body.code === 40100) {
        // 尝试用 refreshToken 刷新
        const refreshSuccess = await tryRefresh(body.data);
        if (refreshSuccess) return request(url, options); // 重试
        else redirectToLogin();
      }
      throw new ApiError(body.code, body.message);
    }
    return body.data;
  }

  if (res.headers.get('X-Access-Token')) {
    // refresh 成功，更新 token 并重试
    updateTokens(res.headers);
    return request(url, options);
  }
  // refresh 失败
  if (body?.code === 40100) redirectToLogin();
}
```

#### 获取当前登录用户

```
GET /user/get/login
Authorization: Bearer {accessToken}
Response: BaseResponse<LoginUserVO>

interface LoginUserVO {
  id: string;          // 注意：Long 转 String（JS 精度丢失处理）
  userName: string;
  userAvatar: string;
  userProfile: string;
  userRole: string;    // "user" | "admin" | "ban"
  createTime: string;
  updateTime: string;
}
```

#### 用户注销

```
POST /user/logout
Authorization: Bearer {accessToken}
X-Refresh-Token: {refreshToken}
```

注销后前端需清除本地存储的令牌。

### 注意事项

#### 1. Long 类型精度（关键！）

`JsonConfig` 全局将 `Long/Long` 类型序列化为 String，前端收到的 ID 字段均为字符串：
```json
{ "id": "1809123456789012345" }
```
前端无需额外处理，直接使用字符串作为 ID。

#### 2. 分页请求统一格式

所有分页接口继承 `PageRequest`：

```typescript
interface PageRequest {
  current: number;      // 当前页号，默认 1
  pageSize: number;     // 每页大小，默认 10（VO 接口最大 20）
  sortField?: string;   // 排序字段名（数据库列名，如 "createTime"）
  sortOrder?: string;   // "ascend" 或 " descend"（注意 descend 前有空格！）
}
```

**!!! 特别注意排序值**：
- 升序: `"ascend"`
- 降序: `" descend"` （**前面有一个空格**，定义在 `CommonConstant.SORT_ORDER_DESC`）

非 VO 的管理员接口（如 `/question/list/page`）不做 pageSize 限制。

#### 3. 权限体系

- `@AuthCheck(mustRole = "admin")` — 仅管理员可访问（如 Question/QuestionBank CRUD）
- 无注解的接口 — 登录用户均可访问（如 Post/PostFavour/PostThumb）
- `/user/add`、`/user/delete`、`/user/update` — 仅管理员

**注意**：JWT 拦截器拦截了除登录/注册外的所有路径，即使接口无 `@AuthCheck` 注解也需带 `Authorization` 头。

#### 4. 文件上传

```
POST /file/upload
Content-Type: multipart/form-data
Authorization: Bearer {accessToken}

字段：
  file: File             // 上传文件，最大 10MB
  biz: string            // 业务类型，当前仅支持 "user_avatar"

响应: BaseResponse<string>  // 文件访问 URL
```

头像上传限制：1MB 以内，仅支持 `jpeg/jpg/svg/png/webp` 格式。

#### 5. 标签字段处理

Post 和 Question 的 tags 字段在数据库中存储为 JSON 字符串，在 VO 中自动解析为数组：

```typescript
// 请求时（AddRequest/EditRequest）
{ "tags": ["Java", "Spring Boot"] }

// 响应时（VO）
"tags": ["Java", "Spring Boot"]
// 数据库实际存储: '["Java","Spring Boot"]'
```

#### 6. 错误处理

全局异常处理器统一拦截异常：
- `BusinessException` → 返回自定义错误码和消息
- `HttpMessageNotReadableException` → 返回 JSON 字段解析错误详情
- 其他异常 → 返回 `50000 系统错误`

前端通过 `response.code !== 0` 判断业务错误。

#### 7. 跨域（CORS）

`CorsConfig` 允许所有 Origin，支持 credentials，允许所有常用 HTTP Method。前端无需额外跨域配置。

#### 8. ES 搜索

Post 和 Question 支持 ES 搜索，通过 `/post/search/page/vo` 和 `/question/search/page/vo` 接口。

默认 ES 配置被注释，启用需：
1. 取消 `application.yml` 中 elasticsearch 配置注释
2. 启用 `job/` 中的同步任务（取消 `@Component` 注释）

#### 9. 模拟面试

模拟面试使用 JSON 字段存储对话消息（非独立消息表）。事件类型: `start`（开始面试）、`chat`（对话消息）、`end`（结束面试）。

---

## 开发指南

- 所有需修改的位置标记了 `// todo` 注释
- Redis 是运行必需（JWT refresh token 存储），如需关闭需修改 `JwtAuthInterceptor`
- MyBatis-Plus 全局逻辑删除字段: `isDelete`（1=已删除, 0=未删除）
- Long 类型返回前端时自动转 String（`JsonConfig` 解决 JS 精度丢失）
- `application.yml` 中 `mybatis-plus.configuration.map-underscore-to-camel-case: false`，数据库列名与 Java 字段名需严格匹配
- COS/微信/数据库/Redis/ES 等外部服务配置前均标记了 `// todo 需替换配置`
