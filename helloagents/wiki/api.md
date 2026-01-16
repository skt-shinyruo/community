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

## 3. API 列表（目标态建议）

### 3.1 Auth

#### [POST] /api/auth/login
**Description：** 用户登录，返回 token。

#### [POST] /api/auth/refresh
**Description：** 刷新 access token。

#### [POST] /api/auth/logout
**Description：** 登出并失效 refresh token（如有黑名单/旋转策略）。

---

### 3.2 User

#### [POST] /api/users
**Description：** 注册（可选：返回激活状态/发送邮件）。

#### [GET] /api/users/{userId}
**Description：** 获取用户资料（主页信息）。

#### [PUT] /api/users/{userId}/avatar
**Description：** 更新头像（建议：上传凭证由后端签发，前端直传对象存储）。

---

### 3.3 Content

#### [GET] /api/posts
**Description：** 帖子列表（支持排序：最新/热帖）。

#### [POST] /api/posts
**Description：** 发帖。

#### [GET] /api/posts/{postId}
**Description：** 帖子详情（含评论分页）。

#### [POST] /api/posts/{postId}/comments
**Description：** 评论/回复。

---

### 3.4 Social

#### [POST] /api/likes
**Description：** 点赞/取消点赞。

#### [POST] /api/follows
**Description：** 关注。

#### [DELETE] /api/follows
**Description：** 取消关注。

---

### 3.5 Message

#### [GET] /api/messages/conversations
**Description：** 私信会话列表。

#### [GET] /api/messages/conversations/{conversationId}
**Description：** 私信详情。

#### [GET] /api/notices
**Description：** 系统通知列表。

---

### 3.6 Search

#### [GET] /api/search/posts?keyword=xxx
**Description：** 全文搜索帖子（高亮）。

---

### 3.7 Analytics

#### [GET] /api/analytics/uv?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 UV。

#### [GET] /api/analytics/dau?start=YYYY-MM-DD&end=YYYY-MM-DD
**Description：** 查询 DAU。

---

## 4. 现状接口（迁移期参考）

### 页面入口
- `GET /index`：首页（Thymeleaf）
- `GET /login`、`GET/POST /register`：登录/注册页面
- `GET /search?keyword=xxx`：搜索页面（Thymeleaf）

### Ajax/JSON
- `POST /like`
- `POST /follow`、`POST /unfollow`

