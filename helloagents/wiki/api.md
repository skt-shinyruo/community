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
- `traceId` 对齐请求头 `X-Request-Id`（由 gateway 生成并透传）。

---

## 4. API 列表（迭代 0 已落地 + 目标态建议）

### 4.1 Auth

#### [POST] /api/auth/login
**Description：** 用户登录，返回 access token，并在响应写入 refresh cookie。

- Request Body：`{ "username": "...", "password": "..." }`
- Response.data：
  - `accessToken`：JWT（Bearer）
  - `expiresInSeconds`：access token 过期秒数
  - `userId`：用户 ID
  - `roles`：角色数组（`user/admin/moderator`）
- Side Effect：写入 HttpOnly Cookie `refresh_token`（默认 Path=`/api/auth`）

#### [POST] /api/auth/refresh
**Description：** 刷新 access token（refresh token 从 HttpOnly Cookie 读取，旋转刷新）。

- Request：空 body（Cookie 自动携带）
- Response.data：同 login（返回新的 access token，并更新 refresh cookie）

#### [POST] /api/auth/logout
**Description：** 登出并失效 refresh token（Redis 标记/删除）并清理 refresh cookie。

> 该接口为受保护接口，需要 `Authorization: Bearer <access_token>`。

#### [GET] /api/auth/me
**Description：** 返回当前登录用户信息（用于联调/可观测性）。

- Response.data：`{ userId, roles, traceId }`

---

### 4.2 User

#### [POST] /api/users
**Description：** 注册（可选：返回激活状态/发送邮件）。

#### [GET] /api/users/{userId}
**Description：** 获取用户资料（主页信息）。

#### [PUT] /api/users/{userId}/avatar
**Description：** 更新头像（建议：上传凭证由后端签发，前端直传对象存储）。

---

### 4.3 Content

#### [GET] /api/posts
**Description：** 帖子列表（支持排序：最新/热帖）。

#### [POST] /api/posts
**Description：** 发帖。

#### [GET] /api/posts/{postId}
**Description：** 帖子详情（含评论分页）。

#### [POST] /api/posts/{postId}/comments
**Description：** 评论/回复。

---

### 4.4 Social

#### [POST] /api/likes
**Description：** 点赞/取消点赞。

#### [POST] /api/follows
**Description：** 关注。

#### [DELETE] /api/follows
**Description：** 取消关注。

---

### 4.5 Message

#### [GET] /api/messages/conversations
**Description：** 私信会话列表。

#### [GET] /api/messages/conversations/{conversationId}
**Description：** 私信详情。

#### [GET] /api/notices
**Description：** 系统通知列表。

---

### 4.6 Search

#### [GET] /api/search/posts?keyword=xxx
**Description：** 全文搜索帖子（高亮）。

---

### 4.7 Analytics

#### [GET] /api/analytics/uv?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 UV。

#### [GET] /api/analytics/dau?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 DAU。

---

## 5. 现状接口（迁移期参考）

### 页面入口
- `GET /index`：首页（Thymeleaf）
- `GET /login`、`GET/POST /register`：登录/注册页面
- `GET /search?keyword=xxx`：搜索页面（Thymeleaf）

### Ajax/JSON
- `POST /like`
- `POST /follow`、`POST /unfollow`
