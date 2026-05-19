# 面试鸭 API 接口文档

> 基础 URL: `http://localhost:8101/api`
>
> Content-Type: `application/json`（文件上传除外）
>
> 统一响应格式: `BaseResponse<T>`

## 统一响应格式

```json
{
  "code": 0,        // 状态码（0 成功，其他为错误）
  "data": {},       // 响应数据
  "message": "ok"   // 提示信息
}
```

### 错误码

| code | 说明 |
|------|------|
| 0 | ok |
| 40000 | 请求参数错误 |
| 40100 | 未登录 |
| 40101 | 无权限 |
| 40300 | 禁止访问 |
| 40400 | 请求数据不存在 |
| 50000 | 系统内部异常 |
| 50001 | 操作失败 |

---

## 一、用户模块 `/user`

### 1.1 用户注册

**POST** `/user/register`

请求体：

```json
{
  "userAccount": "string",
  "userPassword": "string",
  "checkPassword": "string"
}
```

响应：`BaseResponse<Long>`（用户 ID）

---

### 1.2 用户登录

**POST** `/user/login`

请求体：

```json
{
  "userAccount": "string",
  "userPassword": "string"
}
```

响应：`BaseResponse<LoginUserVO>`

---

### 1.3 微信开放平台登录

**GET** `/user/login/wx_open?code={code}`

参数：
- `code` (query) - 微信授权码

响应：`BaseResponse<LoginUserVO>`

---

### 1.4 用户注销

**POST** `/user/logout`

响应：`BaseResponse<Boolean>`

---

### 1.5 获取当前登录用户

**GET** `/user/get/login`

响应：`BaseResponse<LoginUserVO>`

---

### 1.6 创建用户（管理员）

**POST** `/user/add`

请求体：

```json
{
  "userName": "string",
  "userAccount": "string",
  "userAvatar": "string",
  "userRole": "user | admin"
}
```

响应：`BaseResponse<Long>`（用户 ID）

---

### 1.7 删除用户（管理员）

**POST** `/user/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 1.8 更新用户（管理员）

**POST** `/user/update`

请求体：

```json
{
  "id": 0,
  "userName": "string",
  "userAvatar": "string",
  "userProfile": "string",
  "userRole": "user | admin"
}
```

响应：`BaseResponse<Boolean>`

---

### 1.9 编辑用户信息

**POST** `/user/edit`

请求体：

```json
{
  "userName": "string",
  "userAvatar": "string",
  "userProfile": "string"
}
```

响应：`BaseResponse<Boolean>`

---

### 1.10 根据 ID 获取用户（管理员）

**GET** `/user/get?id={id}`

参数：
- `id` (query) - 用户 ID

响应：`BaseResponse<User>`

---

### 1.11 根据 ID 获取用户 VO

**GET** `/user/get/vo?id={id}`

参数：
- `id` (query) - 用户 ID

响应：`BaseResponse<UserVO>`

---

### 1.12 分页获取用户列表（管理员）

**POST** `/user/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "userAccount": "string",
  "userName": "string",
  "userRole": "user | admin"
}
```

响应：`BaseResponse<Page<User>>`

---

### 1.13 分页获取用户 VO 列表

**POST** `/user/list/page/vo`

请求体：同上（`user/list/page`）

响应：`BaseResponse<Page<UserVO>>`

---

### 1.14 更新个人信息

**POST** `/user/update/my`

请求体：

```json
{
  "userName": "string",
  "userAvatar": "string",
  "userProfile": "string"
}
```

响应：`BaseResponse<Boolean>`

---

### 1.15 添加签到记录

**POST** `/user/add/sign_in`

响应：`BaseResponse<Boolean>`

---

### 1.16 获取签到记录

**GET** `/user/get/sign_in?year={year}`

参数：
- `year` (query, 可选) - 年份，默认为当前年份

响应：`BaseResponse<List<Integer>>`

---

## 二、帖子模块 `/post`

### 2.1 创建帖子

**POST** `/post/add`

请求体：

```json
{
  "title": "string",
  "content": "string",
  "tags": ["string"]
}
```

响应：`BaseResponse<Long>`（帖子 ID）

---

### 2.2 删除帖子

**POST** `/post/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 2.3 更新帖子（管理员）

**POST** `/post/update`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "content": "string",
  "tags": ["string"]
}
```

响应：`BaseResponse<Boolean>`

---

### 2.4 根据 ID 获取帖子 VO

**GET** `/post/get/vo?id={id}`

参数：
- `id` (query) - 帖子 ID

响应：`BaseResponse<PostVO>`

---

### 2.5 分页获取帖子列表（管理员）

**POST** `/post/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "title": "string",
  "content": "string",
  "tags": ["string"],
  "orTags": ["string"],
  "userId": 0,
  "favourUserId": 0,
  "searchText": "string"
}
```

响应：`BaseResponse<Page<Post>>`

---

### 2.6 分页获取帖子 VO 列表

**POST** `/post/list/page/vo`

请求体：同上（`post/list/page`）

响应：`BaseResponse<Page<PostVO>>`

---

### 2.7 分页获取当前用户帖子

**POST** `/post/my/list/page/vo`

请求体：同上（`post/list/page`）

响应：`BaseResponse<Page<PostVO>>`

---

### 2.8 ES 分页搜索帖子

**POST** `/post/search/page/vo`

请求体：同上（`post/list/page`）

响应：`BaseResponse<Page<PostVO>>`

---

### 2.9 编辑帖子

**POST** `/post/edit`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "content": "string",
  "tags": ["string"]
}
```

响应：`BaseResponse<Boolean>`

---

## 三、题目模块 `/question`

### 3.1 创建题目（管理员）

**POST** `/question/add`

请求体：

```json
{
  "title": "string",
  "content": "string",
  "tags": ["string"],
  "answer": "string"
}
```

响应：`BaseResponse<Long>`（题目 ID）

---

### 3.2 删除题目（管理员）

**POST** `/question/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 3.3 更新题目（管理员）

**POST** `/question/update`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "content": "string",
  "tags": ["string"],
  "answer": "string"
}
```

响应：`BaseResponse<Boolean>`

---

### 3.4 根据 ID 获取题目 VO

**GET** `/question/get/vo?id={id}`

参数：
- `id` (query) - 题目 ID

响应：`BaseResponse<QuestionVO>`

---

### 3.5 分页获取题目列表（管理员）

**POST** `/question/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "id": 0,
  "notId": 0,
  "title": "string",
  "content": "string",
  "tags": ["string"],
  "answer": "string",
  "questionBankId": 0,
  "userId": 0,
  "searchText": "string"
}
```

响应：`BaseResponse<Page<Question>>`

---

### 3.6 分页获取题目 VO 列表

**POST** `/question/list/page/vo`

请求体：同上（`question/list/page`）

响应：`BaseResponse<Page<QuestionVO>>`

---

### 3.7 分页获取题目 VO 列表（限流版）

**POST** `/question/list/page/vo/sentinel`

请求体：同上（`question/list/page`）

响应：`BaseResponse<Page<QuestionVO>>`

---

### 3.8 分页获取当前用户题目

**POST** `/question/my/list/page/vo`

请求体：同上（`question/list/page`）

响应：`BaseResponse<Page<QuestionVO>>`

---

### 3.9 编辑题目（管理员）

**POST** `/question/edit`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "content": "string",
  "tags": ["string"],
  "answer": "string"
}
```

响应：`BaseResponse<Boolean>`

---

### 3.10 ES 分页搜索题目

**POST** `/question/search/page/vo`

请求体：同上（`question/list/page`）

响应：`BaseResponse<Page<QuestionVO>>`

---

### 3.11 批量删除题目（管理员）

**POST** `/question/delete/batch`

请求体：

```json
{
  "questionIdList": [0, 1, 2]
}
```

响应：`BaseResponse<Boolean>`

---

### 3.12 AI 生成题目（管理员）

**POST** `/question/ai/generate/question`

请求体：

```json
{
  "questionType": "Java",
  "number": 10
}
```

响应：`BaseResponse<Boolean>`

---

## 四、题库模块 `/questionBank`

### 4.1 创建题库（管理员）

**POST** `/questionBank/add`

请求体：

```json
{
  "title": "string",
  "description": "string",
  "picture": "string"
}
```

响应：`BaseResponse<Long>`（题库 ID）

---

### 4.2 删除题库（管理员）

**POST** `/questionBank/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 4.3 更新题库（管理员）

**POST** `/questionBank/update`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "description": "string",
  "picture": "string"
}
```

响应：`BaseResponse<Boolean>`

---

### 4.4 根据 ID 获取题库 VO

**GET** `/questionBank/get/vo?id={id}&needQueryQuestionList={bool}&pageSize={int}&current={int}`

参数：
- `id` (query) - 题库 ID
- `needQueryQuestionList` (query, 可选) - 是否关联查询题目列表
- `pageSize` (query, 可选) - 关联题目分页大小
- `current` (query, 可选) - 关联题目页码

响应：`BaseResponse<QuestionBankVO>`

---

### 4.5 分页获取题库列表（管理员）

**POST** `/questionBank/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "id": 0,
  "notId": 0,
  "title": "string",
  "description": "string",
  "picture": "string",
  "userId": 0,
  "searchText": "string"
}
```

响应：`BaseResponse<Page<QuestionBank>>`

---

### 4.6 分页获取题库 VO 列表

**POST** `/questionBank/list/page/vo`

请求体：同上（`questionBank/list/page`）

响应：`BaseResponse<Page<QuestionBankVO>>`

---

### 4.7 分页获取当前用户题库

**POST** `/questionBank/my/list/page/vo`

请求体：同上（`questionBank/list/page`）

响应：`BaseResponse<Page<QuestionBankVO>>`

---

### 4.8 编辑题库（管理员）

**POST** `/questionBank/edit`

请求体：

```json
{
  "id": 0,
  "title": "string",
  "description": "string",
  "picture": "string"
}
```

响应：`BaseResponse<Boolean>`

---

## 五、题库题目关联 `/questionBankQuestion`

### 5.1 创建关联（管理员）

**POST** `/questionBankQuestion/add`

请求体：

```json
{
  "questionBankId": 0,
  "questionId": 0
}
```

响应：`BaseResponse<Long>`（关联 ID）

---

### 5.2 删除关联

**POST** `/questionBankQuestion/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 5.3 更新关联（管理员）

**POST** `/questionBankQuestion/update`

请求体：

```json
{
  "id": 0,
  "questionBankId": 0,
  "questionId": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 5.4 根据 ID 获取关联 VO

**GET** `/questionBankQuestion/get/vo?id={id}`

参数：
- `id` (query) - 关联 ID

响应：`BaseResponse<QuestionBankQuestionVO>`

---

### 5.5 分页获取关联列表（管理员）

**POST** `/questionBankQuestion/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "id": 0,
  "notId": 0,
  "questionBankId": 0,
  "questionId": 0,
  "userId": 0
}
```

响应：`BaseResponse<Page<QuestionBankQuestion>>`

---

### 5.6 分页获取关联 VO 列表

**POST** `/questionBankQuestion/list/page/vo`

请求体：同上（`questionBankQuestion/list/page`）

响应：`BaseResponse<Page<QuestionBankQuestionVO>>`

---

### 5.7 分页获取当前用户关联列表

**POST** `/questionBankQuestion/my/list/page/vo`

请求体：同上（`questionBankQuestion/list/page`）

响应：`BaseResponse<Page<QuestionBankQuestionVO>>`

---

### 5.8 移除关联（管理员）

**POST** `/questionBankQuestion/remove`

请求体：

```json
{
  "questionBankId": 0,
  "questionId": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 5.9 批量添加题目到题库（管理员）

**POST** `/questionBankQuestion/add/batch`

请求体：

```json
{
  "questionBankId": 0,
  "questionIdList": [0, 1, 2]
}
```

响应：`BaseResponse<Boolean>`

---

### 5.10 批量从题库移除题目（管理员）

**POST** `/questionBankQuestion/remove/batch`

请求体：

```json
{
  "questionBankId": 0,
  "questionIdList": [0, 1, 2]
}
```

响应：`BaseResponse<Boolean>`

---

## 六、帖子收藏 `/post_favour`

### 6.1 收藏/取消收藏

**POST** `/post_favour/`

请求体：

```json
{
  "postId": 0
}
```

响应：`BaseResponse<Integer>`（收藏变化数）

---

### 6.2 获取我收藏的帖子

**POST** `/post_favour/my/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "tags": ["string"],
  "userId": 0,
  "searchText": "string"
}
```

响应：`BaseResponse<Page<PostVO>>`

---

### 6.3 获取用户收藏的帖子

**POST** `/post_favour/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "userId": 0,
  "postQueryRequest": {
    "searchText": "string",
    "title": "string",
    "tags": ["string"]
  }
}
```

响应：`BaseResponse<Page<PostVO>>`

---

## 七、帖子点赞 `/post_thumb`

### 7.1 点赞/取消点赞

**POST** `/post_thumb/`

请求体：

```json
{
  "postId": 0
}
```

响应：`BaseResponse<Integer>`（点赞变化数）

---

## 八、文件上传 `/file`

### 8.1 文件上传

**POST** `/file/upload`

Content-Type: `multipart/form-data`

| 字段 | 类型 | 说明 |
|------|------|------|
| file | File | 上传文件 |
| biz | String | 业务类型（user_avatar） |

响应：`BaseResponse<String>`（文件访问 URL）

---

## 九、模拟面试 `/mockInterview`

### 9.1 创建模拟面试

**POST** `/mockInterview/add`

请求体：

```json
{
  "workExperience": "1-3年",
  "jobPosition": "Java 开发工程师",
  "difficulty": "简单"
}
```

响应：`BaseResponse<Long>`（模拟面试 ID）

---

### 9.2 删除模拟面试

**POST** `/mockInterview/delete`

请求体：

```json
{
  "id": 0
}
```

响应：`BaseResponse<Boolean>`

---

### 9.3 获取模拟面试详情

**GET** `/mockInterview/get?id={id}`

参数：
- `id` (query) - 模拟面试 ID

响应：`BaseResponse<MockInterview>`

---

### 9.4 分页获取模拟面试列表（管理员）

**POST** `/mockInterview/list/page`

请求体：

```json
{
  "current": 1,
  "pageSize": 10,
  "sortField": "string",
  "sortOrder": "ascend | descend",
  "id": 0,
  "workExperience": "string",
  "jobPosition": "string",
  "difficulty": "string",
  "status": 0,
  "userId": 0
}
```

响应：`BaseResponse<Page<MockInterview>>`

---

### 9.5 分页获取当前用户模拟面试

**POST** `/mockInterview/my/list/page/vo`

请求体：同上（`mockInterview/list/page`）

响应：`BaseResponse<Page<MockInterview>>`

---

### 9.6 处理模拟面试事件（AI 对话）

**POST** `/mockInterview/handleEvent`

请求体：

```json
{
  "event": "start | chat | end",
  "message": "string",
  "id": 0
}
```

支持的事件类型：`start`（开始面试）、`chat`（对话消息）、`end`（结束面试）

响应：`BaseResponse<String>`（AI 的回复内容）

---

## 十、微信公众号 `/`

### 10.1 消息验证

**GET** `/`

参数：
- `signature` - 微信签名
- `timestamp` - 时间戳
- `nonce` - 随机数
- `echostr` - 随机字符串

### 10.2 接收微信消息

**POST** `/`

接收微信公众号推送的消息。

### 10.3 设置公众号菜单

**GET** `/setMenu`

设置微信公众号自定义菜单。

---

## 十一、测试接口 `/test/user`

### 11.1 测试登录

**GET** `/test/user/doLogin?username=zhang&password=123456`

### 11.2 测试登录状态

**GET** `/test/user/isLogin`
