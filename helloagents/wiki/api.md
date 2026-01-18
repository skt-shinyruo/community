# API 手册

## 1. 概览

当前仓库存在两类接口：
1. **页面渲染型接口（现状）：** 返回 Thymeleaf HTML（例如 `/index`、`/login`）。
2. **JSON 接口（现状少量 + 目标态为主）：** 供前后端分离后使用。

本手册以“目标态（Vue3 SPA + REST API）”为主，并保留“现状接口”用于迁移期兼容说明。

---

## 2. 认证方式（目标态）

### 2.1 Access Token
- Header：`Authorization: Bearer <access_token>`
- Access Token 过期后使用 Refresh Token 刷新

### 2.2 Refresh Token
- 建议存放在 HttpOnly Cookie（降低 XSS 风险），或存储在安全存储并做旋转刷新（rotation）

### 2.3 CORS / CSRF（refresh cookie）
- CORS：由 gateway 统一配置 `spring.cloud.gateway.globalcors`，并开启 `allowCredentials=true`（Cookie 才能随请求发送）。
- CSRF：refresh token 走 Cookie 时，需要关注 CSRF 风险。
  - 默认策略：refresh cookie 使用 `SameSite=Lax` + `HttpOnly` + 限制 Path（默认 `/api/auth`），可显著降低跨站请求携带 Cookie 的概率。
  - 若未来需要跨站（例如 `SameSite=None`）或对更严格场景（内嵌/跨域）支持，建议引入 CSRF Token（或双提交 Cookie）并配合严格 CORS 白名单。

---

## 3. 统一返回结构（目标态）

后端统一返回 `Result<T>`：

```json
{
  "code": 0,
  "message": "OK",
  "data": {},
  "traceId": "7f2f3c...",
  "timestamp": 1737012345678
}
```

- `code=0` 表示成功；非 0 表示业务/鉴权/系统错误。
- `traceId` 对齐请求头 `X-Trace-Id`（由 gateway 生成并透传）。
- Trace 兼容：同时支持 `traceparent`（W3C Trace Context），由 gateway 与各服务 Filter 解析/生成并透传。
- HTTP 状态码约定：
  - `CommonErrorCode`（400/401/403/404/429/500）会同步映射到 HTTP status（例如 401 对应 HTTP 401）。
  - 模块业务错误码（例如 `AuthErrorCode` 的 10001/10002/...）默认仍返回 HTTP 200，业务方通过 `Result.code` 识别。

---

## 4. API 列表（迭代 0 已落地 + 目标态建议）

### 4.1 Auth

#### [POST] /api/auth/login
**Description：** 用户登录，返回 access token，并在响应写入 refresh cookie。

- Request Body：`{ "username": "...", "password": "...", "captchaId?": "...", "captchaCode?": "..." }`
- Notes：登录失败达到阈值后（风险触发），后端会要求必须携带验证码（避免仅 UI 校验可绕过）。
- Response.data：
  - `accessToken`：JWT（Bearer）
- Side Effect：写入 HttpOnly Cookie `refresh_token`（默认 Path=`/api/auth`）
- Error：
  - 账号/密码错误：HTTP 200 + `code=10001`
  - 触发登录限流：HTTP 429 + `code=429`
  - 需要验证码：HTTP 200 + `code=10005`
  - 验证码错误：HTTP 200 + `code=10006`

#### [POST] /api/auth/refresh
**Description：** 刷新 access token（refresh token 从 HttpOnly Cookie 读取，旋转刷新）。

- Request：空 body（Cookie 自动携带）
- Response.data：`{ "accessToken": "..." }`（返回新的 access token，并更新 refresh cookie）

#### [POST] /api/auth/logout
**Description：** 登出并失效 refresh token（按 token 家族失效）并清理 refresh cookie。

> 该接口为受保护接口，需要 `Authorization: Bearer <access_token>`。

#### [GET] /api/auth/me
**Description：** 返回当前登录用户信息（用于联调/可观测性）。

- Response.data：`{ userId, username, authorities }`

#### [POST] /api/auth/register
**Description：** 注册新用户（创建未激活账号，生成激活方式）。

- Request Body：`{ "username": "...", "password": "...", "email": "...", "captchaId": "...", "captchaCode": "..." }`
- Response.data：`{ userId, activationIssued, activationLink? }`
  - `activationLink` 默认关闭，仅用于本地/测试联调（由 `AUTH_EXPOSE_ACTIVATION_LINK` 控制）。

#### [GET] /api/auth/activation/{userId}/{code}
**Description：** 激活账号（返回值与旧单体对齐）。

- Response.data：`0=success, 1=repeat, 2=failure`

#### [GET] /api/auth/captcha
**Description：** 获取验证码（captchaId + 图片 base64）。

- Response.data：`{ captchaId, imageBase64, ttlSeconds }`

#### [POST] /api/auth/captcha/verify
**Description：** 校验验证码（一次性/TTL）。

- Request Body：`{ "captchaId": "...", "code": "...." }`
- Response.data：`true/false`

#### [POST] /api/auth/password/reset/request
**Description：** 请求重置密码（必须验证码；为避免用户枚举，统一返回“已处理”）。

- Request Body：`{ "email": "...", "captchaId": "...", "captchaCode": "..." }`
- Response.data：`{ issued, resetLink? }`
  - `resetLink` 默认关闭，仅用于本地/测试联调（由 `AUTH_EXPOSE_RESET_LINK` 控制）。

#### [POST] /api/auth/password/reset/confirm
**Description：** 确认重置密码（必须验证码 + resetToken，一次性消费）。

- Request Body：`{ "resetToken": "...", "newPassword": "...", "captchaId": "...", "captchaCode": "..." }`
- Response.data：`true/false`

**Error Codes（业务码）：**
- `10005`：需要验证码（CAPTCHA_REQUIRED）
- `10006`：验证码不正确或已失效（CAPTCHA_INVALID）
- `10007`：重置凭证无效或已过期（PASSWORD_RESET_INVALID）

---

### 4.2 User

#### [GET] /api/users/{userId}
**Description：** 获取用户资料（主页信息）。

#### [GET] /api/users/{userId}/avatar/upload-token
**Description：** 获取头像上传凭证（七牛），仅本人可调用。

#### [PUT] /api/users/{userId}/avatar
**Description：** 更新头像 URL（建议：前端直传对象存储，后端只负责签发凭证与回写 URL），仅本人可调用。

#### [GET] /api/users/resolve?username=xxx
**Description：** 将用户名解析为用户 ID（供私信 toName 等兼容旧单体交互）。

---

### 4.3 Content

#### [GET] /api/posts
**Description：** 帖子列表（支持排序：最新/热帖）。

- Query：`order=latest|hot`（可选，默认 latest）
- Query：`page/size`（可选，0-based）

#### [POST] /api/posts
**Description：** 发帖。

#### [GET] /api/posts/{postId}
**Description：** 帖子详情（含帖子点赞信息：likeCount/liked）。

#### [GET] /api/posts/{postId}/comments
**Description：** 评论列表（分页）。

- Query：`page/size`（可选，0-based）

#### [POST] /api/posts/{postId}/comments
**Description：** 评论/回复（兼容旧单体）。

- Request Body：`{ content, entityType?, entityId?, targetId? }`
  - `entityType=1`：评论帖子（默认）
  - `entityType=2`：回复评论（`entityId=commentId`）
  - `targetId`：回复目标用户（可选；不传则默认为被回复评论作者）

#### [GET] /api/posts/{postId}/comments/{commentId}/replies
**Description：** 评论回复列表（对齐旧单体“楼中楼”体验）。

#### [POST] /api/posts/{postId}/top
#### [POST] /api/posts/{postId}/wonderful
#### [POST] /api/posts/{postId}/delete
**Description：** 管理/审核操作：置顶/加精/删除。

- Auth：管理员/版主（gateway + content-service 双层校验）

---

### 4.4 Social

#### [POST] /api/likes
**Description：** 点赞/取消点赞。

- Request.body：`{ entityType, entityId, entityUserId?, postId?, liked }`
- 说明：
  - `liked` 为空时采用 **toggle 语义**（兼容旧单体交互）。
  - `liked=true/false` 时采用显式幂等设置语义。
  - `entityUserId/postId` 用于“获赞计数/通知”，迁移期由调用方提供（后续可进一步内聚到 content-service）。

#### [GET] /api/likes/status?entityType=1&entityId=100
**Description：** 查询当前用户对实体是否已点赞（需要登录）。

#### [GET] /api/likes/count?entityType=1&entityId=100
**Description：** 查询实体点赞总数（公开）。

#### [GET] /api/likes/users/{userId}/count
**Description：** 查询用户“获赞数”（公开）。

#### [POST] /api/follows
**Description：** 关注（关注时间按 ZSet score 记录）。

- Request.body：`{ entityType, entityId, entityUserId? }`

#### [DELETE] /api/follows?entityType=3&entityId=2
**Description：** 取消关注（幂等）。

#### [GET] /api/follows/status?entityType=3&entityId=2
**Description：** 查询当前用户是否已关注该实体（需要登录）。

#### [GET] /api/follows/{userId}/followees?entityType=3&page=0&size=10
**Description：** 查询关注列表（按关注时间倒序，公开）。

#### [GET] /api/follows/{userId}/followers?entityType=3&page=0&size=10
**Description：** 查询粉丝列表（按关注时间倒序，公开）。

#### [GET] /api/follows/{userId}/followees/count?entityType=3
**Description：** 查询关注数（公开）。

#### [GET] /api/follows/{userId}/followers/count?entityType=3
**Description：** 查询粉丝数（公开）。

---

### 4.5 Message

#### [GET] /api/messages/conversations
**Description：** 私信会话列表。

#### [GET] /api/messages/conversations/detail
**Description：** 会话列表聚合（含 unreadCount/letterCount/targetUser），对齐旧单体 UI 交互。

#### [GET] /api/messages/conversations/{conversationId}
**Description：** 私信详情。

#### [POST] /api/messages
**Description：** 发送私信（创建新消息）。

#### [GET] /api/messages/unread-count?conversationId=xxx
**Description：** 查询未读私信数量（conversationId 可选；为空表示全部会话）。

#### [PUT] /api/messages/read
**Description：** 标记私信为已读（按消息 ids）。

#### [GET] /api/notices
**Description：** 系统通知列表（topic 必填，例如 like/comment/follow）。

#### [GET] /api/notices/unread-count?topic=like
**Description：** 查询系统通知未读数（topic 可选；为空表示所有 topic 汇总）。

#### [GET] /api/notices/summary
**Description：** 通知聚合摘要（comment/like/follow），用于列表页/红点展示。

#### [PUT] /api/notices/read
**Description：** 标记通知为已读（按消息 ids）。

---

### 4.6 Search

#### [GET] /api/search/posts?keyword=xxx
**Description：** 全文搜索帖子（高亮）。

#### [POST] /api/search/internal/reindex
**Description：** 迁移期索引重建（从数据库拉取帖子并重建索引）。

- Auth：仅管理员（`ROLE_ADMIN`）可调用（gateway 与 search-service 双层校验）。

#### [POST] /internal/search/reindex
**Description：** search-service 内部索引重建入口（不经 gateway 路由；用于运维/脚本）。

- Header：`X-Internal-Token: <SEARCH_INTERNAL_TOKEN>`

---

### 4.7 Analytics

#### [GET] /api/analytics/uv?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 UV。

- Auth：管理员/版主（`ROLE_ADMIN` / `ROLE_MODERATOR`）

#### [GET] /api/analytics/dau?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 DAU。

- Auth：管理员/版主（`ROLE_ADMIN` / `ROLE_MODERATOR`）

#### [GET] /api/analytics/me
**Description：** 当前用户标识回显（用于联调验证鉴权链路）。

- Auth：需要登录

#### [POST] /internal/analytics/uv/record
**Description：** 内部写入口：记录 UV（Gateway Filter 调用，需 internal token）。

#### [POST] /internal/analytics/dau/record
**Description：** 内部写入口：记录 DAU（Gateway Filter 调用，需 internal token）。

---

## 5. 现状接口（迁移期参考）

### 页面入口
- `GET /index`：首页（Thymeleaf，迁移期开关开启后将跳转到 SPA：`/#/posts`）
- `GET /discuss/detail/{postId}`：帖子详情页（迁移期开关开启后将跳转到 SPA：`/#/posts/{postId}`）
- `GET /user/profile/{userId}`：用户主页（迁移期开关开启后将跳转到 SPA：`/#/users/{userId}`）
- `GET /login`、`GET/POST /register`：legacy 登录/注册页面（迁移期可保留，目标态由 SPA 统一承接）
- `GET /search?keyword=xxx`：legacy 搜索页面（迁移期可保留，目标态由 `/api/search/posts` + SPA 承接）

### Ajax/JSON
- `POST /like`、`POST /follow`、`POST /unfollow`：legacy 旧 Ajax 接口（Deprecated）
  - 目标态改为：`/api/likes/**`、`/api/follows/**`

> 迁移期只读保护：当 `legacy.readonly.enabled=true` 时，legacy 的写入口（如发帖/评论/头像更新等）会返回 410，提示使用 `/api/**` 新接口。

### 兼容与下线（迁移期约定）
- 旧入口 `/search`、`/notice`、`/data` 归属于 `legacy-community`（Thymeleaf/页面化）并保留兼容期。
- 新入口统一走 Gateway + REST：`/api/search/**`、`/api/notices/**`、`/api/analytics/**`。
- 下线策略建议：先在文档标记 Deprecated → 网关侧不再暴露旧入口 → 观测无流量后再 301/404（具体节奏见方案包 task 与 ADR）。
