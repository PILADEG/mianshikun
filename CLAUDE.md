# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

**面试鸭** (mianshikun) — 基于 Spring Boot 2.7.x 的面试刷题平台后端。核心业务：题目管理、题库管理、模拟面试（AI 对话）、帖子/点赞/收藏、微信公众平台集成。

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

# Docker 构建
docker build -t mianshikun .
```

## Architecture

### 分层结构 (Standard 4-layer)

```
controller → service(impl) → mapper → mysql
     ↕            ↕
    dto          entity/vo
```

- **controller** — REST 接口，接收 DTO 请求参数，返回统一 BaseResponse<T>
- **service** — 业务逻辑层（目前为空模板状态，需自行实现）
- **mapper** — MyBatis-Plus Mapper 接口，对应 resources/mapper/*.xml
- **model/entity** — 数据库实体，带 @TableLogic 逻辑删除
- **model/dto** — 请求参数（Add/Update/Delete/Query 等）
- **model/vo** — 返回视图对象（脱敏，如 LoginUserVO 不返回密码）

### 核心模块

| 模块 | 说明 | 关键表 |
|------|------|--------|
| User | 注册/登录/微信开放平台登录/签到 | user |
| Question | 题目 CRUD + ES 搜索 + AI 生成 | question |
| QuestionBank | 题库 CRUD，与题目多对多关联 | question_bank, question_bank_question |
| Post | 帖子 CRUD + ES 搜索 | post（需在 sql 中补建表） |
| PostFavour | 帖子收藏 | post_favour |
| PostThumb | 帖子点赞 | post_thumb |
| MockInterview | AI 模拟面试（SSE 流式对话） | mock_interview |
| File | 腾讯云 COS 文件上传 | — |
| WX MP | 微信公众号消息/菜单 | — |

### 公共组件

- **common/** — BaseResponse、ErrorCode (枚举)、ResultUtils (快速响应)、PageRequest、DeleteRequest
- **exception/** — BusinessException (自定义异常)、GlobalExceptionHandler (全局处理 + JSON 解析错误增强)
- **annotation/AuthCheck** + **aop/AuthInterceptor** — 基于注解的权限校验（user/admin/ban）
- **aop/LogInterceptor** — 全局请求日志（AOP 记录耗时）
- **config/** — CorsConfig (跨域)、CosClientConfig (对象存储)、MyBatisPlusConfig (分页)、JsonConfig (Long 序列化精度)、WxOpenConfig
- **constant/** — UserConstant (角色/状态)、CommonConstant (排序)、FileConstant (COS 域名)
- **job/** — FullSyncPostToEs (全量同步)、IncSyncPostToEs (增量同步定时任务)
- **manager/** — CosManager (COS 对象存储封装)
- **esdao/** — PostEsDao (Elasticsearch Repository)
- **generate/CodeGenerator** — 代码生成器（根据模板生成 Controller/Service 骨架）

### 数据存储

- **MySQL** — 主数据库，MyBatis + MyBatis-Plus (分页插件)
- **Redis** — 可选分布式 Session（Spring Session Redis），默认排除
- **Elasticsearch** — Post/Question 全文搜索，通过 Spring Data ES 操作
- **腾讯云 COS** — 文件/图片存储

### 多环境配置

- `application.yml` — 公共配置（默认 dev）
- `application-test.yml` — 测试环境
- `application-prod.yml` — 生产环境（Docker 默认激活）

### API 规范

- 基础路径: `http://localhost:8101/api`
- 统一响应: `BaseResponse<T>` with `code`/`data`/`message`
- 错误码: SUCCESS(0), PARAMS_ERROR(40000), NOT_LOGIN(40100), NO_AUTH(40101), NOT_FOUND(40400), FORBIDDEN(40300), SYSTEM_ERROR(50000), OPERATION_ERROR(50001)
- 接口文档（开发环境）: `http://localhost:8101/api/doc.html` (Knife4j/Swagger)

## Service 层注意事项

当前 `service/` 和 `service/impl/` 目录为空。`aop/AuthInterceptor` 中已引用了 `UserService`，实现时需保证 `UserService` Bean 存在。所有 Service 类需手动创建（可利用 `generate/CodeGenerator` 生成骨架）。

## 开发指南

- 所有需修改的位置标记了 `// todo` 注释
- 开启 Redis 需：①移除 `@SpringBootApplication` 的 `exclude` 中的 `RedisAutoConfiguration` ②配置 `spring.session.store-type: redis`
- 开启 ES 搜索：取消 `application.yml` 中 elasticsearch 配置注释，并在 `job/` 中取消 `@Component` 启用同步任务
- MyBatis-Plus 全局逻辑删除字段: `isDelete`（1=已删除, 0=未删除）
- Long 类型返回前端时自动转 String（JsonConfig 解决 JS 精度丢失）
- 模拟面试使用 JSON 字段存储对话消息，非独立消息表
