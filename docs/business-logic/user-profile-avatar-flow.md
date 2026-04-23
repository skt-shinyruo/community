# 用户资料聚合与头像链路实现说明

本文档说明当前仓库中用户资料页与头像链路的实际实现路径，聚焦以下问题：

- 用户资料页从哪里进入系统
- 用户资料本来应该聚合哪些域的数据
- 当前代码里哪些聚合逻辑已经接好、哪些仍是占位状态
- 头像上传 token、上传、确认、文件访问是如何串起来的
- 本地存储和 R2 存储各自怎么工作

相关文档：

- `docs/business-logic/wallet-ledger-flow.md`
- `docs/business-logic/social-like-follow-outbox-flow.md`
- `docs/business-logic/content-post-comment-bookmark-subscription-flow.md`

---

## 1. 参与组件

- `UserController`：用户资料、批量摘要、头像相关接口
- `UserProfileApplicationService`：用户资料页聚合入口
- `UserReadApplicationService`：同域用户读取应用入口，同时承接跨域 user query API
- `UserQueryService`：用户主资料 owner，负责基础资料和钱包状态投影
- `UserSocialProfileService`：点赞数 / 关注数 / 粉丝数 / 是否已关注
- `PostReadQueryApi`：跨域读取最近帖子 / 最近评论
- `UserLevelQueryApi`：跨域读取用户等级摘要
- `AvatarService`：上传 token、上传校验、确认消费
- `AvatarStorageRouter`：存储策略路由
- `LocalAvatarStorageProvider`：本地文件存储
- `R2AvatarStorageProvider`：R2 对象存储
- `FilesController`：头像文件读取入口
- `UserService`：最终写回 `headerUrl`

---

## 2. 对外接口

资料相关：

- `GET /api/users/{userId}`
- `GET /api/users/{userId}/recent-posts`
- `GET /api/users/{userId}/recent-comments`
- `GET /api/users/resolve`
- `POST /api/users/batch-summary`

头像相关：

- `GET /api/users/{userId}/avatar/upload-token`
- `POST /api/users/{userId}/avatar/upload`
- `PUT /api/users/{userId}/avatar`
- `GET /files/**`

---

## 3. 用户资料聚合主线

### 3.1 理论上的聚合边界

`UserProfileApplicationService` 聚合：

- `UserReadApplicationService`：用户基础资料、钱包余额、钱包状态
- `UserSocialProfileService`：社交统计与 viewer 是否已关注
- `PostReadQueryApi`：最近帖子 / 最近评论
- `UserLevelQueryApi`：用户等级

### 3.2 当前 `get(...)` 的实际行为

当前实现里，`UserProfileApplicationService.get(...)` 的行为是：

1. 通过 `UserReadApplicationService.getProfile(userId)` 读取用户基础资料
2. 解析当前 viewer
3. 调用 `UserSocialProfileService.userProfileStats(userId, viewerId)` 补齐点赞 / 关注 / 粉丝 / 是否已关注
4. 调用 `UserLevelQueryApi.evaluateLevel(userId)` 补齐等级摘要
5. 最终返回 `UserProfilePageView`

所以当前资料页代码形态是：同域应用服务负责页面级编排，跨域只通过 owner-domain API 读取协作模型。

### 3.2.1 当前字段来源

当前 `GET /api/users/{userId}` 返回值里，可以按来源理解。

来自 `UserProfileView` 的字段：

- `id`
- `username`
- `headerUrl`
- `type`
- `status`
- `createTime`
- `score`
- `level`
- `walletBalance`
- `walletStatus`

来自 `UserSocialProfileService` 的字段：

- `likeCount`
- `followeeCount`
- `followerCount`
- `hasFollowed`
- `socialDegraded`

来自 `UserLevelQueryApi` 的字段：

- `userLevelEnabled`
- `userLevel`
- `signInDaysInWindow`

等级结果为空或禁用时，等级展示字段会返回关闭态；这不是硬编码占位，而是显式的降级语义。

### 3.3 最近帖子与最近评论

当前实现里：

- `listRecentPosts(...)`：先校验用户存在，再调用 `PostReadQueryApi.listPostsByUser(...)`
- `listRecentComments(...)`：先校验用户存在，再调用 `PostReadQueryApi.listRecentCommentsByUser(...)`

也就是说：

- 这两个接口已经对外暴露
- 当前实现已经接入 content 的跨域读模型

### 3.4 当前聚合行为被测试锁定

`UserProfileApplicationServiceTest` 当前明确验证：

- `get(...)` 会组合用户基础资料、社交统计、等级摘要
- 匿名 viewer、本人 viewer、他人 viewer 都有覆盖
- 等级禁用或结果为空时返回关闭态
- `listRecentPosts(...)` / `listRecentComments(...)` 会在用户存在校验后委托 content query API

这部分现在应当被看作已闭环的页面聚合，而不是迁移期占位。

---

## 4. 用户基础资料与摘要

### 4.1 基础资料

基础资料 owner 仍然是 `UserQueryService`：

- `getProfile(userId)` -> `UserProfileView`

它当前主要从 `user` 表组装：

- `id`
- `username`
- `headerUrl`
- `type`
- `status`
- `createTime`
- `score`
- `level`
- `walletBalance`
- `walletStatus`

### 4.2 用户名解析与批量摘要

`UserController` 还提供两个常用协作接口：

- `GET /api/users/resolve`
  - 按 username 解析基础摘要
- `POST /api/users/batch-summary`
  - 按 userId 批量返回 `UserSummaryResponse`

这两个接口更像“用户信息查找边界”，常被前端或其他业务页面复用。

---

## 5. 头像上传三段式链路

头像上传不是一次请求直接写 DB，而是分成三段：

1. 申请上传 token
2. 上传文件
3. 确认并更新 `headerUrl`

### 5.1 签发上传 token

`GET /api/users/{userId}/avatar/upload-token`

主链路：

1. 只能给自己申请
2. `AvatarService.createUploadToken(userId)`
3. 服务端生成安全文件名：
   - `avatar/{userId}/{uuid}`
4. 当前 provider 生成上传参数
5. Redis 绑定 upload ticket，TTL 600 秒

返回内容里包括：

- `provider`
- `fileName`
- `uploadUrl`
- `uploadMethod`
- `maxBytes`
- `mimeLimit`

### 5.2 上传文件

`POST /api/users/{userId}/avatar/upload`

主链路：

1. 只能给自己上传
2. `AvatarService.assertUploadTicketOwner(...)` 校验 ticket 归属
3. 当前 provider 真正存文件

provider 侧的共同约束：

- 文件不能为空
- 大小不能超过 `AvatarConstraints.MAX_AVATAR_BYTES`
- MIME 必须在允许列表中
- `fileName` 必须是服务端生成的 `avatar/{userId}/{uuid}`

### 5.3 确认并写回头像 URL

`PUT /api/users/{userId}/avatar`

主链路：

1. 只能操作自己的头像
2. `AvatarService.assertAndConsumeUploadTicket(...)`
   - 成功后 Redis ticket 会被消费删除
3. `AvatarService.buildAvatarUrl(fileName)` 生成公开 URL
4. `UserService.updateHeaderUrl(userId, url)` 写回用户表

这说明上传成功和头像生效不是同一个步骤。

### 5.4 ticket 的失败路径

头像 ticket 当前依赖 Redis，失败路径要单独看清楚：

- Redis 不可用：`createUploadToken` / `assertUploadTicketOwner` / `assertAndConsumeUploadTicket` 都会失败
- `fileName` 非服务端生成格式：直接拒绝
- 上传 ticket owner 与当前用户不匹配：直接 `FORBIDDEN`
- 确认接口会消费 ticket：同一张 ticket 只能成功确认一次

这意味着当前三段式链路是有意设计成：

- 先签发短期 upload ticket
- 再允许上传
- 最后只允许一次确认生效

---

## 6. 文件访问链路

头像最终通过：

- `GET /files/**`

进入 `FilesController`。

路径级安全边界上，`UserSecurityRules` 当前显式放开了：

- `GET /files/**`

所以文件读取是公开读，不需要登录。

它的关键约束是：

- 只允许匹配 `avatar/{userId}/{uuid}` 的安全 key
- 非法 key 直接拒绝
- 通过当前 provider 读取对象
- 命中后返回：
  - 正确的 `Content-Type`
  - `Cache-Control`
  - `X-Content-Type-Options: nosniff`

未命中对象时：

- 返回 `404`

所以用户表里的 `headerUrl` 本质上只是一个指向 `/files/...` 的可公开访问地址。

---

## 7. 本地存储与 R2 存储

### 7.1 路由

`AvatarStorageRouter` 根据：

- `user.avatar.storage`

选择 provider，默认是：

- `local`

当前默认配置还包括：

- `user.avatar.files-base-dir = /tmp/community-files`
- `user.avatar.public-base-url = ""`

### 7.2 local provider

`LocalAvatarStorageProvider` 的行为：

- 上传目标目录：`user.avatar.files-base-dir`
- 公开 URL 基址：`user.avatar.public-base-url`
- 写到本地文件系统
- 读取时再由 `/files/**` 从本地文件流式返回

如果 `publicBaseUrl` 没配置，`buildAvatarUrl(...)` 会直接失败。

所以 local 模式下“能上传文件”不等于“能成功写回可访问头像 URL”。

### 7.3 r2 provider

`R2AvatarStorageProvider` 的行为：

- 仍然通过服务端接收 multipart
- 再把文件写进 R2 bucket
- 读取时由 `/files/**` 从 R2 拉取对象流式返回

如果 `r2.bucket-name` 没配置，R2 provider 会直接拒绝上传或读取。

所以无论本地还是 R2，当前对前端的写入协议是统一的，变化只在后端 provider。

---

## 8. 当前限制

用户资料链路当前最重要的现状是：

- 社交统计依赖已经注入，但 `get(...)` 还没有实际调用
- 用户等级依赖已经注入，但当前固定返回关闭
- 最近帖子 / 最近评论接口当前固定返回空列表
- 头像链路虽然完整，但依赖 `publicBaseUrl`、Redis ticket、以及所选 storage provider 的底层配置齐全

所以这篇文档要按“当前实现”理解为：

- 头像链路已经比较完整
- 用户资料聚合结构已成型，但聚合结果还未完全落地

---

## 9. 一句话总结

当前用户侧实现的核心思路是：

- 用户基础资料仍由 `user` 域 own
- 用户页聚合入口已经搭好，但社交 / 等级 / 最近内容仍处于占位状态
- 头像采用“签发 token -> 上传文件 -> 消费 ticket -> 写回 headerUrl”三段式模型
- 文件最终统一经 `/files/**` 暴露，存储策略由 router 决定
