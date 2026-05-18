# 业务实现手册

本文档是业务实现总览层，和 [business-logic/README.md](business-logic/README.md) 下的域级详细文档一起使用。这里保留每个业务域的入口、owner、主流程和关键代码定位，细节说明放到按域文档中。架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)。

## 阅读模板

每个能力按以下口径理解：

- Owner / SSOT：谁拥有主事实。
- Entry：请求、事件或 job 从哪里进入。
- Main path：正常链路。
- Consistency：同步、outbox、best-effort 或回源。
- Failure / idempotency：失败、重试、幂等和补偿语义。
- Key code：读源码时的关键类。

## Auth Registration Login And Session

Owner / SSOT：

- auth 模块负责登录、注册、验证码、找回密码、JWT 签发、登录风控。
- user/session 组件托管 DB refresh token 状态，表为 `auth_refresh_token`。
- JWT access token 是短期访问凭证；refresh token 是 HttpOnly cookie。

Entry：

- `/api/auth/register`
- `/api/auth/register/code/resend`
- `/api/auth/register/code/verify`
- `/api/auth/login`
- `/api/auth/refresh`
- `/api/auth/logout`
- `/api/auth/me`
- `/api/auth/password/reset/request`
- `/api/auth/password/reset/confirm`

Main path：

1. controller 只负责 HTTP binding、cookie/header 处理和 DTO 转换。
2. 入口进入 `auth.application.*ApplicationService`。
3. 注册采用 Verify-First：先由 user owner 做用户名/邮箱前置查重，再生成 registration draft 和验证码，不插入 `user` row。
4. 验证码通过邮件发送；本地默认用 MailHog。
5. 验证注册验证码后创建 active 用户，并可自动登录；若用户已创建但 token 签发失败，提示直接登录而不是重新注册。
6. 登录先经过登录风控、验证码要求和密码校验。
7. 密码只接受 BCrypt hash。
8. 登录成功后签发 access token，并通过 HttpOnly cookie 下发 256-bit base64url refresh token；registration token 和 reset token 也由 auth application 使用同一安全随机生成策略。
9. refresh 先消费旧 token，再回源 user 校验用户仍存在且 active，最后才签发同 family 的新 refresh token；family reuse 可触发族撤销。
10. logout 撤销 refresh token / family 并清 cookie。
11. cleanup job 清理过期 refresh session；registration draft 和验证码依赖各自 store TTL 自然过期。

登录、刷新、退出和 JWT 鉴权的详细代码链路见 [auth-login-session-flow.md](auth-login-session-flow.md)。

### Register And Verify Email

The high-concurrency registration flow is Verify-First:

1. `AuthController.register` calls `RegistrationApplicationService.register`.
2. Auth validates captcha and request fields, then calls `user.api.action.UserRegistrationActionApi.prepareRegistrationUser`.
3. User checks username/email conflicts, then prepares normalized username/email, generated provisional user id, BCrypt password hash, and default avatar URL without inserting a `user` row.
4. Auth application 生成 256-bit base64url opaque `registrationToken`，仓储只负责按该 token 存储 `PreparedRegistrationDraft`；token 冲突时 application 最多重试 5 次。
5. `AuthController.verifyRegisterCode` calls `RegistrationVerificationApplicationService.verifyAndLogin`.
6. Auth resolves the draft, consumes the code, then calls `user.api.action.UserRegistrationActionApi.createVerifiedRegistrationUser`.
7. User inserts one active `user` row with `status=1` and publishes `UserPolicyChanged(userExists=true)`.
8. Auth issues login tokens and deletes the draft as best-effort cleanup. If token issuance fails after the user row is created, auth returns `REGISTRATION_ACTIVATED_LOGIN_REQUIRED` and the user should log in manually.

Abandoned registrations expire from the draft/code stores and do not create user rows or IM policy events.

Refresh session DB state：

- `auth_refresh_token` 只保存 refresh token hash、用户、family、过期时间和撤销时间。
- `RefreshTokenSessionApplicationService.store(...)` 在签发 refresh token 时写入 DB session 状态。
- `/api/auth/refresh` 通过 `consume(...)` 消费当前 active token；找不到、已过期或已撤销都会视为不可刷新。
- 消费成功后先回源 user owner 校验用户状态；用户不存在或已禁用时撤销该 refresh family，并清浏览器 cookie。
- 刷新成功会旋转 refresh token，新 token 重新 `store(...)`，旧 token 不再保持 active；不会在用户状态校验前提前签发新 token。
- `revoke(...)` 用于单 token 撤销；`revokeFamily(...)` 用于 family reuse 或整族撤销；`revokeByUserId(...)` 用于密码重置后撤销该用户会话。
- `deleteExpiredBefore(...)` 由 cleanup job 调用，只清理已过期 refresh session，不影响已经签出的 access token。

Password reset：

1. 请求重置密码必须带邮箱和验证码。
2. 服务端先校验 reset base URL 配置，避免签发 token 后才发现无法生成链接；验证码通过后按邮箱/IP 维度做请求限流。
3. 邮箱不存在、用户未激活或状态不可用时也返回“已受理”，但不签发 token，不发邮件，防止用户枚举。
4. 256-bit base64url token 存储在 `auth:pwdreset:<token>`，通过邮件下发链接，HTTP 响应体不返回 reset link；若 token 已写入但邮件发送失败，会 best-effort 删除该 token。
5. 确认重置时再次校验验证码、token 和密码策略。
6. 密码策略由 user owner 校验：长度 8 到 `ValidationLimits.PASSWORD_MAX`，至少包含两类字符，并拒绝首尾空白字符，避免静默修改用户输入。
7. 密码更新成功后撤销该用户 refresh sessions；若密码更新失败，会按消费时捕获的剩余 TTL 恢复 reset token，允许用户在原有效期内重试。

Security：

- `AuthSecurityRules` 决定 `/api/auth/**` 哪些入口允许匿名。
- `AuthOriginGuardFilter` 覆盖所有 public 且会改变认证状态的 auth POST 入口：login、refresh、logout、register、register code resend / verify、password reset request / confirm。
- prod 下 `AuthStartupValidator` 禁止固定验证码、注册验证码回传和 reset link 回传，SMTP 必须可用；OriginGuard 启用且 fail-closed 时必须配置可信 Origin allowlist。
- `/api/auth/me` 直接读 JWT claim；角色变化通常要等 token 重签后体现。

Key code：

- `auth.application.AuthApplicationService`
- `auth.application.*ApplicationService`
- `user.application.RefreshTokenSessionApplicationService`
- `auth.infrastructure.web.AuthOriginGuardFilter`
- `auth.config.AuthStartupValidator`
- `auth.security.AuthSecurityRules`
- `common-security`

## Admin User Role Management

Owner / SSOT：

- 用户角色事实是 `user.type`。
- 管理员用户搜索和角色修改属于治理面，不是完整 RBAC。

Entry：

- `GET /api/users/admin/search`
- 管理员角色修改接口，路径以 `AdminUserController` 为准。

Main path：

1. 搜索支持按 userId、用户名、邮箱定位用户。
2. 搜索接口不是全量列表；空条件直接参数错误。
3. 角色修改必须显式二次确认。
4. 必须填写 reason。
5. 必须指定目标用户。
6. 找不到目标用户失败。
7. 禁止管理员把自己降成非管理员。
8. 相同角色不会重复写库。
9. 真正主事实更新是一条 user role/type update。
10. 变更后写审计日志，不写独立审计表。

Security：

- 路径权限由 `UserSecurityRules` / `CommunitySecurityConfig` 控制。
- controller 自己不写授权矩阵。

Key code：

- `user.controller.AdminUserController`
- `user.application.*ApplicationService`
- `user.security.UserSecurityRules`

## User Profile And Avatar

Owner / SSOT：

- user 域拥有用户基础资料、头像业务和用户摘要；头像对象与公共下载事实由 OSS owner 承担。
- 资料聚合会回源内容域读取最近帖子 / 评论等只读视图。

Entry：

- `/api/users/**`
- `/files/**`
- `/api/oss/**`
- 头像 upload session / confirm 相关接口以 `UserController` 为准。

Main path：

1. 用户资料聚合读取用户基础资料。
2. 最近帖子和最近评论来自 content owner 查询。
3. 批量用户摘要用于内容、通知、社交等展示。
4. 当前聚合字段和行为由测试锁定。
5. 头像上传三段式：
   - 前端请求 `POST /api/users/{userId}/avatar/upload-sessions`，user application 校验本人操作，并通过 `community-oss-client` prepare upload。
   - 响应只包含通用上传会话：`uploadId`、`objectId`、`versionId`、上传 URL / method / form fields、约束和过期时间；前端不读取 storage provider、bucket 或物理路径。
   - 客户端按上传会话直接提交到 OSS 上传入口。
   - 前端使用 `{ "objectId": "..." }` 确认头像，user 回源 OSS metadata 校验对象归属后写回 canonical OSS public URL。
6. 文件访问通过 `/files/**` 暴露，但实际 blob 读取由 `community-oss` 完成。
7. OSS 首版以 Garage 为主后端，dev 可用 `community-oss` 的 local filesystem backend 或 Garage single-node；`community-app` 不再保留本地/R2 文件 provider。

Failure / security：

- confirm 时必须校验 OSS 对象 `usage/owner/visibility/status` 和当前 user 匹配。
- MIME 白名单和 2 MiB 大小限制。
- 上传失败不能兜底更新头像。
- 文件公开地址只使用 OSS canonical URL：`/files/{objectId}/{versionId}/{fileName}`。

Key code：

- `user.controller.UserController`
- `user.application.*ApplicationService`
- `user.domain.*`
- `user.infrastructure.*`
- `user.security.UserSecurityRules`

## User Moderation State

Owner / SSOT：

- user 域 owns 用户处罚状态，例如 `muteUntil`、`banUntil`。
- 内容域、社交域和 IM policy 都要回源或投影该状态。

Entry：

- 管理员治理动作。
- 举报处理链路。
- user moderation application service。

Main path：

1. 举报或管理员动作触发用户处罚。
2. `UserModerationApplicationService` 更新用户处罚状态。
3. user 域发布 policy changed 事件。
4. 内容写路径同步回源 user owner 判断禁言 / 封禁。
5. IM 侧通过 snapshot + Kafka policy event 维护本地 projection。

Consistency：

- 内容同步回源 user SSOT，不依赖本地 user projection。
- IM realtime 为了连接层性能使用本地 policy projection，但启动时从 `community-app` 拉 snapshot，运行期消费 policy change。

Key code：

- `user.application.UserModerationApplicationService`
- `user.api.query.UserModerationQueryApi`
- `im.projection.*`

## Report And Moderation

Owner / SSOT：

- report / moderation action 主事实位于 content / governance 相关表。
- 用户处罚事实位于 user。
- 通知是 notice 派生读模型。

Entry：

- `/api/reports/**`
- `/api/moderation/**`

Main path：

1. 用户创建举报，服务端校验目标内容和举报理由。
2. 管理员或 moderator 执行治理动作。
3. 内容下线是软删除 / 状态修改，不物理删行。
4. 内容 owner 发布删除 / 状态变化事件。
5. 用户处罚由 user owner 更新。
6. user policy changed 事件驱动 IM policy outbox。
7. 治理通知进入 notice projection。

Failure / consistency：

- 内容下线和作者删除一样，都是软删除 + 事件扩散。
- 处罚状态本身不是 after-commit 读模型，而是 user 主事实。
- notice / search 是下游读模型，可能最终一致。

Key code：

- `content.controller.ReportController`
- `content.controller.ModerationController`
- `content.application.*ApplicationService`
- `user.application.UserModerationApplicationService`
- `notice.application.NoticeProjectionApplicationService`

## Content Post Comment Bookmark And Subscription

Owner / SSOT：

- content 域 owns 帖子、评论、回复、收藏、分类订阅、标签和内容治理状态。
- search / notice / growth / wallet 是下游或协作方。

Entry：

- `GET /api/posts`
- `POST /api/posts`
- `POST /api/posts/{postId}/comments`
- `/api/bookmarks`
- `/api/categories/**`
- `/api/subscriptions/categories`
- `/api/tags/**`

Main path: create post：

1. `PostController.create(...)` 读取登录用户和 `Idempotency-Key`。
2. 进入 `PostPublishingApplicationService.create(...)`。
3. `IdempotencyGuard.executeRequired(...)` 包裹真实写操作。
4. application 做文本清洗、分类存在性校验、用户发言资格校验。
5. domain / repository 完成 `DiscussPost` 落库。
6. tag 绑定；新 tag 通过 `ensureTagId(...)` 幂等创建。
7. 同步触发奖励 / 任务进度 owner API。
8. 发布 content domain event。
9. search outbox enqueuer 在 `BEFORE_COMMIT` 写 outbox。
10. notice projection listener 在 `AFTER_COMMIT` best-effort 生成通知。
11. 安排帖子分数刷新副作用。

Main path: create comment：

1. `PostController.addComment(...)` 读取用户和 `Idempotency-Key`。
2. 应用层校验帖子 / 评论目标、用户发言资格、内容。
3. 回复评论时，目标必须是该帖子下的 active 一级评论；服务端解析 target user。
4. 若评论双方存在拉黑关系，写入会被拒绝。
5. 文本在写入前做转义和敏感词过滤。
6. 写 `comment` 并增加帖子 `comment_count`。
7. 同步触发奖励 / 任务进度 owner API。
8. 发布评论事件。
9. notice / growth / score refresh 等下游按各自语义处理。

Main path: update / delete comment：

- 作者只能编辑自己的 active 评论。
- 编辑窗口为评论创建后 15 分钟内。
- 编辑时重新做文本转义和敏感词过滤，并更新 `update_time` / `edit_count`。
- 作者删除必须校验路由上的 `postId` 是该评论真实归属帖子。
- 治理删除和作者删除走同一个删除线程能力。
- 删除评论会软删除该评论及其 active descendant replies。
- 帖子 `comment_count` 按本次从 visible 变为 deleted 的评论数递减。
- 删除后发布每条被删评论的删除事件，并安排帖子热度刷新。
- 删除后通过 social owner action 清理被删评论上的 like 关系。

Bookmarks / subscription：

- 收藏关系是用户对内容的读写偏好。
- 分类订阅中 `subscribed=true` 需要登录态；匿名用户视为未授权。
- 订阅关系用于后续推荐/展示，不改变内容主事实。

Failure / idempotency：

- 发帖和评论使用 HTTP `Idempotency-Key`。
- 内容类接口当前不传请求指纹，只按 `operation + userId + key` 去重。
- 搜索索引通过 outbox 最终追平。
- 通知投影失败不回滚主写事务。
- 删除 / 下线帖子后，search handler 回源 DB 当前状态并异步删除索引文档。
- 评论删除后的 like 清理在事务提交后触发；清理失败不回滚已提交的评论删除。

Key code：

- `content.controller.PostController`
- `content.application.PostPublishingApplicationService`
- `content.application.CommentApplicationService`
- `content.domain.service.CommentDomainService`
- `content.domain.*`
- `content.infrastructure.persistence.*`
- `content.contracts.event.*`
- `search.infrastructure.event.PostOutboxEnqueuer`
- `search.infrastructure.event.PostOutboxHandler`

## Social Like Follow And Events

Owner / SSOT：

- social 域 owns 点赞、关注、拉黑关系。
- content owns 被点赞/关注事件中的内容实体信息。
- notice / growth / wallet 是下游或协作方。

Entry：

- `/api/likes/**`
- `/api/follows/**`
- `/api/blocks/**`

Main path: like：

1. controller 进入 `LikeApplicationService`。
2. application 解析当前 actor。
3. `ContentEntityResolver` 通过 `content.api.query.ContentEntityQueryApi` 回源解析内容实体。
4. 服务端解析 `entityUserId`、`postId` 等事件字段，不信任客户端声明。
5. repository 写点赞关系。
6. storage adapter 若声明需要补偿，application 注册事务回滚补偿。
7. 同步调用奖励 / 任务进度 owner API。
8. 发布 social domain event。
9. `LocalSocialDomainEventPublisher` 映射为 `SocialContractEvent`。
10. notice / growth 等下游消费。

Main path: follow：

- 关注写入关注关系并发布 follow created 事件。
- `unfollow(...)` 当前只删除关注关系，不发布 `FollowRemoved` 事件。

Payload semantics：

- 点赞事件里的目标用户和帖子归属由服务端权威解析。
- 如果 `entityUserId` 不合法，奖励/任务下游会跳过。
- 自己给自己点赞不会获得奖励。

Consistency：

- 点赞 / 关注主业务写入在当前事务域内。
- 奖励 / 任务进度通过同步 owner API 处理。
- notice 是 best-effort after-commit。

Key code：

- `social.application.LikeApplicationService`
- `social.application.FollowApplicationService`
- `social.application.ContentEntityResolver`
- `social.domain.*`
- `social.infrastructure.*`
- `social.contracts.event.*`

## Social Block And IM Governance

Owner / SSOT：

- social 域 owns block relation。
- user 域 owns punishment state。
- IM realtime 持有本地 policy projection，用于连接层快速判定。

Entry：

- `/api/blocks/**`
- `/internal/im/realtime/projections/block-relations`
- `/internal/im/realtime/projections/user-policies`

Main path：

1. `BlockApplicationService` 处理拉黑/取消拉黑。
2. 重复拉黑返回 `false` 或等价幂等结果。
3. 新增 block 关系时，同步移除 blocker -> blocked 和 blocked -> blocker 两个方向的 follow 关系。
4. block 关系变化发布领域事件。
5. `ImPolicyOutboxEnqueuer` 在 `BEFORE_COMMIT` 写入 IM policy outbox。
6. outbox payload topic 是 `projection.im.policy`，payload kind 为 `BLOCK`。
7. `ImPolicyKafkaOutboxHandler` 把 outbox payload 转成 Kafka `UserBlockRelationChanged`。
8. Kafka topic 是 `im.event.user-block-relation-changed`，key 是 blocker userId。
9. `im-realtime` 消费事件，刷新本地 policy projection。
10. `im-realtime` 发送私信前用本地 projection 判断拉黑、处罚、目标用户状态。

Main path: user punishment：

1. `UserModerationApplicationService` 更新用户禁言 / 封禁 / 存在状态。
2. user owner 发布 `USER_POLICY_CHANGED` contract event。
3. `ImPolicyOutboxEnqueuer` 在 `BEFORE_COMMIT` 写入 `projection.im.policy` outbox，payload kind 为 `USER_POLICY`。
4. `ImPolicyKafkaOutboxHandler` 转成 Kafka `UserMessagingPolicyChanged`。
5. Kafka topic 是 `im.event.user-messaging-policy-changed`，key 是 userId。
6. `im-realtime` 消费事件，刷新本地 policy projection。
7. `im-realtime` 发送私信前用本地 projection 判断拉黑、处罚、目标用户状态。

Snapshot：

- `community-app` 暴露 block relations / user policies snapshot。
- `im-realtime` 用 internal scope JWT 拉取。
- 浏览器不能访问这些 internal projection 入口。

Failure：

- 如果发布 block 事件失败，当前实现会尝试回滚刚写入的 block 关系，并恢复本次自动移除的 follow 关系。
- IM policy outbox 失败不应影响已提交主事实，会通过 outbox 重试。

Key code：

- `social.application.BlockApplicationService`
- `im.projection.ImPolicyOutboxEnqueuer`
- `im.projection.ImPolicyKafkaOutboxHandler`
- `im.projection.ImPolicySnapshotController`

## Notice Projection And Read Model

Owner / SSOT：

- notice 是从内容、社交、治理事件派生出的站内通知读模型。
- notice 不是内容、社交或治理主事实。
- 存储使用 `community.notice_record`，不复用 IM 或旧 message 表语义。

Entry：

- `/api/notices/**`
- content / social / moderation contract events。

Read path：

- 通知列表。
- 未读数。
- 摘要。
- 批量已读，`ids` 是通知 UUID 列表。

Projection path：

1. 上游 content / social / moderation 发布 contract event。
2. `NoticeProjectionListener` 在事务提交后接收事件。
3. `NoticeProjectionApplicationService` 判断事件类型、收件人、topic 和 content 快照。
4. `NoticeProjectionDomainService` 判断是否应该投影。
5. 写 notice 记录。

Topics / content：

- 评论、点赞、关注、治理事件都可生成通知。
- `LIKE_REMOVED`、`FOLLOW_REMOVED` 当前不会撤销或生成通知。
- notice `content` 是带上下文的 JSON 快照，不是最终渲染文案。
- 通知 content 保留源事件 `eventId`、`type` 和 payload 关键字段。

Failure：

- notice 当前是本地 after-commit best-effort 投影。
- 失败只记录日志，不回滚源事务。
- 当前没有 notice outbox adapter。

Key code：

- `notice.controller.NoticeController`
- `notice.application.NoticeApplicationService`
- `notice.application.NoticeProjectionApplicationService`
- `notice.domain.service.NoticeProjectionDomainService`
- `notice.infrastructure.event.NoticeProjectionListener`

## IM Private Message

Owner / SSOT：

- `community-im-gateway` owns session bootstrap 和外部 `/ws/im` 入口桥接。
- `im-realtime` owns 内部 worker WebSocket 连接态和在线推送。
- `im-core` owns 私信会话、消息、顺序号、幂等、历史、未读。
- `community-app` owns 用户处罚和拉黑 SSOT，并提供 policy snapshot。

Entry：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：session response `wsUrl`，稳定为 `ws://localhost:12880/ws/im`
- HTTP history：`http://localhost:12880/api/im/**`

Main path：

1. 客户端带 bearer token 调 `POST /api/im/sessions`。
2. `community-im-gateway` 校验 token，选择 worker，生成 session ticket，并返回稳定的 `wsUrl`。
3. 客户端连接返回的 `wsUrl`，即 `/ws/im`。
4. `community-im-gateway` 接收首帧 `connect` 和 ticket，桥接到选定的 `im-realtime` worker。
5. `im-realtime` 完成鉴权并注册连接。
6. 首次鉴权后，`im-realtime` 也会从 `im-core` 拉取房间 membership snapshot，主要服务群聊索引。
7. 客户端发送 `sendPrivateText`。
8. `im-realtime` 确认连接已鉴权。
9. 本地 `PolicyProjectionService` 判定拉黑、处罚、目标用户存在性。
10. 判定通过后写 `im.command.private-text`。
11. `im-core` 消费 command，校验并按 `(conversationId, fromUserId, clientMsgId)` 幂等。
12. `im-core` 分配 seq、落库、更新会话状态。
13. `im-core` 发布 `im.event.private-persisted`。
14. `im-realtime` 消费 persisted event 并在线推送。
15. 客户端断线或错过推送时，通过 HTTP history API 补拉。

Semantics：

- `sendAccepted` 不等于已落库。
- 私信正确性依赖 `im-core` history，不依赖在线推送必达。
- 已读水位由 `im-core` 维护。

Key code：

- `im-realtime` WebSocket handler / policy projection / Kafka producer。
- `im-core` private message service / controller / repository。
- `PolicySnapshotClient`
- `MembershipSnapshotClient`

## IM Room Message

Owner / SSOT：

- `community-im-gateway` owns session bootstrap 和外部 `/ws/im` 入口桥接。
- `im-core` owns 房间、成员、群消息、seq、read state。
- `im-realtime` owns 在线房间索引和更新通知扇出。

Entry：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：session response `wsUrl`，稳定为 `ws://localhost:12880/ws/im`
- HTTP room history：`http://localhost:12880/api/im/**`

Main path：

1. 客户端带 bearer token 调 `POST /api/im/sessions`。
2. `community-im-gateway` 选择 worker、生成 ticket，并返回稳定的 `wsUrl`。
3. 客户端连接返回的 `wsUrl`，发送 `connect` frame 和 ticket 完成 WebSocket 鉴权。
4. `community-im-gateway` 把首帧桥接到选定的 `im-realtime` worker。
5. `im-realtime` 调 `im-core` internal membership snapshot，拉取当前用户所在房间。
6. `im-realtime` 建立本机在线房间索引。
7. 客户端发送 `sendRoomText`。
8. `im-realtime` 写 `im.command.room-text`。
9. `im-core` 消费 command，校验房间存在、发送者是成员。
10. `im-core` 按 `(roomId, fromUserId, clientMsgId)` 做幂等。
11. `im-core` 分配 room seq，持久化消息。
12. `im-core` 发布 `im.event.room-persisted`。
13. `im-realtime` 收到 event 后，不一定广播完整消息，而是推送 `roomUpdatedBatch`。
14. 客户端收到更新后，通过 HTTP 拉取群消息并推进 `lastReadSeq`。

Membership changes：

- `im.event.room-member-changed` 驱动 realtime 更新本地房间索引。
- `JOINED`：把用户当前在线连接加入房间索引。
- `LEFT`：把用户当前在线连接从房间索引移除。
- 用户重连后会重新从 `im-core` 拉取所属房间，重建本地索引。

Semantics：

- `im-realtime` 的房间索引不是成员关系权威来源。
- 群聊在线推送是 state-only，不是 full-message push。
- 消息级恢复依赖 `im-core` history。

Key code：

- `im-realtime` room index / WS handler / Kafka consumer。
- `im-core` room controller / room message service / membership service。
- `InternalRealtimeProjectionController`

## Search Projection

Owner / SSOT：

- content owns 帖子主事实。
- search owns搜索查询、ES repository 和索引别名。
- ES 是最终一致读模型。

Entry：

- `GET /api/search/posts`
- content event -> search outbox。

Search query：

1. `SearchController.searchPosts(...)`。
2. `SearchApplicationService.searchPosts(...)`。
3. `PostSearchDomainService` 校验 keyword、category、tag、page、size。
4. repository 查询 ES 或 memory implementation。
5. 支持标题/内容全文检索、分类过滤、标签过滤、score + createTime 排序、关键词高亮。
6. 单页最大 50，避免深分页风险。
7. 无关键词时退化为 match-all，支持纯分类/标签过滤。

Projection：

1. content 变化发布事件。
2. `PostOutboxEnqueuer` 写 `projection.search.post` outbox。
3. `OutboxWorker` dispatch 到 `PostOutboxHandler`。
4. handler 反序列化 payload，只把它作为触发信号。
5. handler 回源 content owner 当前帖子状态。
6. 当前状态应索引则 upsert ES；已删除 / 不应索引则 delete ES。

Failure：

- ES 故障不阻断发帖、评论、社交等主业务写入。
- 搜索查询会受 ES 故障影响。
- outbox 会重试投影失败。

Key code：

- `search.controller.SearchController`
- `search.application.SearchApplicationService`
- `search.domain.service.PostSearchDomainService`
- `search.infrastructure.persistence.ElasticsearchPostSearchRepository`
- `search.infrastructure.event.PostOutboxEnqueuer`
- `search.infrastructure.event.PostOutboxHandler`
- `search.infrastructure.persistence.PostIndexManager`

## Analytics

Owner / SSOT：

- analytics 域 owns UV / DAU 统计写入和查询。
- Redis 是当前主要存储。

Entry：

- `/api/analytics/**`：查询面，ADMIN / MODERATOR。
- analytics 自动采集 filter / application 写入口，以当前配置和代码为准。

Current state：

- 当前 analytics 对外 HTTP 面主要是查询，不是任意客户端埋点写入。
- `analytics.ingest.enabled` 默认为 `false`；未开启时 filter 直接跳过采集。
- 默认采集路径包括 `/api/posts/**`、`/api/search/**`、`/api/notices/**`。
- 默认排除 `/api/analytics/**`、`/api/auth/**`、`/api/ops/**`、`/actuator/**`、`/internal/**`、`/files/**`。
- `OPTIONS` 和 HTTP `5xx` 响应不采集。

Ingest path：

1. `AnalyticsRequestCaptureFilter` 在请求链路完成后执行采集判断。
2. `AnalyticsRequestClassifier` 根据开关、method、path、status、include / exclude path 决定是否采集。
3. filter 从 `ClientIpResolver` 解析 IP，从当前 `Authentication` 解析用户 UUID。
4. `AnalyticsIngestApplicationService.recordRequest(...)` 按配置分别记录 UV / DAU。
5. 登录成功可通过 `AnalyticsIngestActionApi.recordLoginSuccess(...)` 记录 DAU，但同样受 analytics ingest 开关和 `recordDau` 约束。

UV：

- 依赖请求入口解析 IP。
- 使用 Redis HyperLogLog。
- 按日期记录，区间查询通过合并统计。

DAU：

- JWT subject 是 UUID。
- Phase 1 通过 `RedisAnalyticsUserOrdinalRepository` 把 UUID 映射为 analytics-only 整数 ordinal。
- 写入当日 Bitmap。

Failure / limits：

- analytics 是统计读模型，不应影响核心业务写路径。
- filter 采集异常只记录日志，不改变业务 HTTP 响应。
- Redis 写 UV / DAU 失败只记录日志；前 3 次和 2 的幂次数失败会 warn，debug 下保留堆栈。
- 区间查询和 bitmap offset 要受实现限制约束。

Key code：

- `analytics.application.AnalyticsApplicationService`
- `analytics.application.AnalyticsIngestApplicationService`
- `analytics.domain.service.AnalyticsDomainService`
- `analytics.domain.service.AnalyticsIngestDomainService`
- `analytics.infrastructure.web.AnalyticsRequestCaptureFilter`
- `analytics.infrastructure.web.AnalyticsRequestClassifier`
- `analytics.infrastructure.api.AnalyticsIngestActionApiAdapter`
- `analytics.security.AnalyticsSecurityRules`

## Growth Task Reward Level And Retired Check In Surface

Owner / SSOT：

- growth 域当前保留任务模板、任务进度、事件去重、等级规则和任务投影底座。
- wallet 是在线余额和资金事实 owner。

当前真实存在：

- `task_template`
- `user_task_progress`
- `user_task_event_log`
- `user_level_rule_config`
- 内容 / 社交 / growth 事件驱动的任务投影
- 基于签到任务完成数的等级计算

Task progress path：

1. 内容或社交事件进入 growth。
2. 按事件类型找激活中的任务模板。
3. 解析 periodKey。
4. 记录 source event 到 `user_task_event_log`，做事件去重。
5. 确保有 `user_task_progress`。
6. 锁定并推进进度。
7. 根据模板判断未完成、已达成待领取、已达成自动发奖。
8. 已发过的奖励不重复发。

Reward / wallet：

- 奖励通过钱包侧 requestId 去重和总账规则承担幂等。
- user reward bridge 先把内容/社交奖励语义翻译成统一命令，再进入 wallet owner。

Level：

- 当前等级按签到任务完成数计算。
- 等级不是用户表上的独立实时字段。
- 配置修改落入等级规则配置。

Failure：

- 幂等不靠 listener 自身，而靠 source event log、任务进度状态和 wallet requestId。

Key code：

- `growth.application.TaskProgressApplicationService`
- `growth.domain.service.*`
- `growth.infrastructure.persistence.*`
- `growth.api.*`
- `wallet.api.action.*`

## Market Order And Dispute

Owner / SSOT：

- market owns listing、inventory、order、dispute。
- wallet owns 资金事实、托管、放款、退款。

Entry：

- `/api/market/**`
- `/api/admin/market/**`
- `POST /api/market/orders` 使用 `Idempotency-Key`。

Listing / inventory：

- listing 表达商品发布状态。
- 虚拟商品和实物商品创建差异不同。
- 预加载库存只允许 `goodsType=VIRTUAL` 且 `deliveryMode=PRELOADED` 的 listing 使用。
- `POST /api/market/listings/{listingId}/inventory` 只允许 listing 卖家追加库存。
- 每个 payload 会创建一个 `market_inventory_unit`，状态为 `AVAILABLE`，并增加 listing `stock_total` / `stock_available`。
- 如果 listing 追加库存前是 `SOLD_OUT`，追加成功后恢复为 `ACTIVE`。
- `POST /api/market/inventory/{inventoryUnitId}/invalidate` 只允许卖家把 `AVAILABLE` 库存置为 `INVALID`，并同步扣减 listing 库存；最后一件可用库存失效时 listing 转为 `SOLD_OUT`。
- 库存 payload 必须非空，`payloadType` 必须非空；空批次或空 payload 是参数错误。

Market query：

- `GET /api/market/listings` 只返回公开 listing，口径由 `MarketListingRepository.findPublicListings()` 决定。
- `GET /api/market/listings/{listingId}` 找不到 listing 返回 `404`。
- `GET /api/market/my-listings` 按当前登录卖家列出自己的 listing。
- 买家订单列表按 buyer 过滤，卖家订单列表按 seller 过滤。
- 订单详情只允许买家或卖家查看，其他登录用户返回 `403`。
- 虚拟订单详情优先读取已 `DELIVERED` 的手动交付记录；没有手动交付时，再读取已 `DELIVERED` 的预加载库存 payload。
- 非虚拟商品订单详情不返回 delivery content。

Order path：

1. controller 读取 `Idempotency-Key`；body `requestId` 按未知字段返回 `400`。
2. 缺少 `Idempotency-Key` 返回 `400`。
3. request fingerprint 包含 `listingId`、`quantity`、`addressId`。
4. application 校验 listing、库存、买家、价格和订单总额上限。
5. 物理商品必须提供 active 收货地址，订单保存地址快照。
6. 有限库存或实物商品创建订单时扣减 listing 可用库存。
7. 预加载虚拟库存会先锁定库存单元并绑定订单。
8. 创建订单，初始状态为 `ESCROW_PENDING`。
9. 在 market 本地事务内写入 `market_wallet_action(ESCROW, PENDING)`，不在该事务里直接写 wallet ledger。
10. 对外幂等 key 与钱包账本 requestId 解耦；钱包侧使用服务端派生 id，例如 `market-order:<orderId>:<action>`。

Market wallet action saga：

1. `MarketWalletActionApplicationService` 为 escrow / release / refund 写 durable command。
2. `request_id` 固定为 `market-order:<orderId>:<action>`，重复 enqueue 必须语义一致，否则 replay conflict。
3. `MarketWalletActionProcessorHandler` 触发 `MarketWalletActionProcessorApplicationService.processDue(...)`。
4. processor claim due action，设置 `PROCESSING` 和短 lease。
5. processor 在原 market 事务之外调用 `WalletMarketActionApi`。
6. wallet 成功后，processor 记录 `wallet_txn_id` 并通过 `MarketOrderSagaApplicationService` 条件推进订单 / 争议状态。
7. release / refund 的可恢复钱包错误进入 `RETRYING` 并带 backoff。
8. escrow 的业务失败会进入失败路径并恢复 market 侧库存 / 预加载库存。
9. `MarketWalletActionRecoveryHandler` 负责恢复过期 processing lease、补齐缺失 command、把已有 `wallet_txn_id` 重新应用到 saga 状态。

Order states：

- `ESCROW_PENDING`：订单已创建，等待资金托管 command 处理。
- `ESCROW_CANCEL_PENDING`：取消已接受，若 escrow 尚未落账则应 no-op；若 escrow 已落账则转 refund。
- `ESCROWED`：资金已托管。
- 已交付或已发货。
- `RELEASE_PENDING`：买家确认或自动确认已接受，等待放款 command。
- 买家取消。
- 争议中。
- `REFUND_PENDING`：取消退款已接受，等待退款 command。
- `DISPUTE_REFUND_PENDING` / `DISPUTE_RELEASE_PENDING`：争议裁决已接受，等待退款或放款 command。
- 完成。
- `ESCROW_FAILED`：托管业务失败，需按失败原因处理或展示。

Delivery / shipment：

- 虚拟商品支持卖家手动交付。
- 预加载虚拟商品在 escrow 成功后自动标记库存单元 delivered，并把订单置为 delivered。
- 实物商品支持发货。
- 买家确认后只把订单置为 `RELEASE_PENDING` 并写 release command。
- 买家取消按状态进入 `ESCROW_CANCEL_PENDING` 或 `REFUND_PENDING`，由 processor 决定 no-op 或 refund。

Dispute：

1. 买家发起争议。
2. 卖家接受退款。
3. 卖家拒绝退款。
4. 管理员裁决。
5. 裁决结果进入 wallet 放款或退款。

Auto confirm：

- 后台任务可自动确认满足条件的订单。
- 必须由 market owner 判断状态和时间窗口。
- 每个 due order 单独锁定并确认。
- 自动确认只写 release command，不在批量任务里直接调用 wallet。
- 任务重跑应幂等。

Failure / recovery：

- `market_wallet_action` 是市场到钱包资金动作的 durable business command，不是普通投影事件。
- processor 成功调用 wallet 但未完成 market 状态推进时，恢复任务用同一个 wallet `requestId` / `wallet_txn_id` 继续推进，不重复记账。
- refund / release 不能因为收款方钱包冻结而永久卡死；当前钱包侧允许系统入账类 release / refund / reward / admin adjustment 进入账户。
- 取消早于 escrow 处理时，escrow action 可被标记 `CANCELLED/NOOP` 并恢复库存。
- escrow 已经落账但订单已进入取消路径时，saga 会补 enqueue refund command。
- processor 无法自动修复的失败会保留 action `FAILED` / pending order 状态，留给 recovery job 或人工排查。

Key code：

- `market.controller.*`
- `market.application.MarketOrderApplicationService`
- `market.application.MarketInventoryApplicationService`
- `market.application.MarketQueryApplicationService`
- `market.application.MarketDisputeApplicationService`
- `market.application.MarketWalletActionApplicationService`
- `market.application.MarketWalletActionProcessorApplicationService`
- `market.application.MarketWalletActionRecoveryApplicationService`
- `market.application.MarketOrderSagaApplicationService`
- `market.application.MarketOrderAutoConfirmApplicationService`
- `market.domain.model.MarketWalletAction`
- `market.domain.service.MarketWalletActionDomainService`
- `market.infrastructure.persistence.*`
- `market.infrastructure.job.MarketWalletActionProcessorHandler`
- `market.infrastructure.job.MarketWalletActionRecoveryHandler`
- `wallet.api.action.WalletMarketActionApi`

## Wallet Ledger

Owner / SSOT：

- wallet owns 钱包账户、交易、双分录流水、冻结、冲正和资金事实。
- market / growth / reward 等只能通过 wallet owner action 协作。

Entry：

- `/api/wallet/**`
- `/api/wallet/admin/**`
- wallet owner API / action。

Core concepts：

- `wallet_account`：账户余额视图。
- `wallet_txn`：交易事实。
- `wallet_entry`：双分录明细。
- `requestId`：总账幂等键，保持全局唯一。
- `WalletAmountPolicy.MAX_AMOUNT = 100_000_000`：单次资金动作最大金额。

HTTP writes：

- 充值、提现、转账使用 `Idempotency-Key`。
- body `requestId` 按未知字段返回 `400`。
- HTTP 幂等 key 与总账 requestId 解耦。
- 充值请求指纹只包含 `amount`。
- 提现请求指纹只包含 `amount`。
- 转账请求指纹包含 `toUserId` 和 `amount`。

Recharge：

1. `POST /api/wallet/recharges` 进入 `WalletApplicationService.recharge(...)`。
2. 应用层解析 effective idempotency key，包裹 `wallet:recharge` HTTP 幂等。
3. `WalletRechargeApplicationService.complete(...)` 按 `userId + requestId` 查找或创建 `recharge_order`。
4. 重放必须匹配 `userId` 和 `amount`，否则返回 `REQUEST_REPLAY_CONFLICT`。
5. 未支付订单通过总账写入 `RECHARGE`：借记系统 `PLATFORM_CASH`，贷记用户钱包。
6. 账本成功后订单从 `CREATED` 更新为 `PAID`。

Withdraw：

1. `POST /api/wallet/withdrawals` 进入 `WalletApplicationService.withdraw(...)`。
2. 只有 active 用户钱包可主动提现。
3. 按 `userId + requestId` 查找或创建 `withdraw_order`，重放必须匹配 `userId` 和 `amount`。
4. 新请求会先检查系统 `PLATFORM_CASH` 余额，余额不足且没有既有订单时返回 `PLATFORM_CASH_INSUFFICIENT`。
5. `REQUESTED` 阶段先写 `WITHDRAW` 账本：借记用户钱包，贷记系统 `WITHDRAW_PENDING`，订单转 `PROCESSING`。
6. `PROCESSING` 阶段再写 settlement 账本：借记 `WITHDRAW_PENDING`，贷记 `PLATFORM_CASH`，订单转 `SUCCEEDED`。
7. 两段账本都使用服务端派生 requestId，重跑会由总账幂等吸收。

Transfer：

1. `POST /api/wallet/transfers` 进入 `WalletApplicationService.transfer(...)`。
2. 转出用户和转入用户必须合法，不能自转；金额必须为正。
3. 只有 active 转出方钱包可主动转账。
4. 按 `fromUserId + requestId` 查找或创建 `transfer_order`，重放必须匹配 `fromUserId`、`toUserId` 和 `amount`。
5. 未成功订单写 `TRANSFER` 总账：借记转出用户钱包，贷记转入用户钱包。
6. 账本成功后订单从 `CREATED` 更新为 `SUCCEEDED`。

Ledger idempotency：

- `requestId` 重放且交易类型、业务类型、业务 id、金额和分录指纹一致：复用或返回等价结果。
- `requestId` 重放但语义不一致：`REQUEST_REPLAY_CONFLICT`。
- 资金动作必须通过总账规则保证双分录一致。
- 总账分录必须借贷平衡，借方总额必须为正且不超过 `WalletAmountPolicy.MAX_AMOUNT`。
- 金额累加使用 checked arithmetic，溢出会作为业务非法请求拒绝。

Market / reward integration：

- market 托管、放款、退款由 market wallet action processor 调用 wallet market action API。
- growth / reward 奖励写入 wallet，由钱包 requestId 去重。
- 冻结钱包不能发起用户主动转账、提现或市场购买。
- 冻结钱包仍可接收系统必须完成的入账或补偿动作，例如退款、放款、奖励和管理员调整。

Admin operations：

- `POST /api/wallet/admin/freeze` 校验管理员动作 actor 和 reason 后，把目标用户钱包状态置为 `FROZEN`，并写 `wallet_admin_action` 审计。
- freeze 当前是单向治理动作；恢复 active 状态没有独立 HTTP 管理入口。
- `POST /api/wallet/admin/reverse` 只按 wallet txn requestId 查找原交易。
- 只有 `TRANSFER`、`ORDER_RELEASE`、`REWARD_ISSUE` 可冲正。
- 冲正通过 `reversal:<originalRequestId>` 写一笔 `REVERSAL` 总账，不直接改旧交易或旧分录。
- 冲正前会检查反向分录导致的扣款账户余额是否足够；余额不足时拒绝。
- 同一原交易重复冲正时，总账 requestId 和 admin audit requestId 都是幂等的。

Failure：

- 资金类 HTTP 写入口幂等存储异常时 fail-closed。
- 总账 requestId 冲突不会重复记账。
- 冻结、冲正和转账必须保持账户与 entry 一致。
- 钱包账户余额更新使用 version 条件更新；余额不足或并发冲突会转成明确业务错误。

Key code：

- `wallet.controller.*`
- `wallet.application.WalletApplicationService`
- `wallet.application.WalletRechargeApplicationService`
- `wallet.application.WalletWithdrawApplicationService`
- `wallet.application.WalletTransferApplicationService`
- `wallet.application.WalletLedgerApplicationService`
- `wallet.application.WalletAccountApplicationService`
- `wallet.application.WalletAdminOpsApplicationService`
- `wallet.domain.service.WalletLedgerDomainService`
- `wallet.domain.service.WalletAccountDomainService`
- `wallet.domain.service.WalletOrderDomainService`
- `wallet.infrastructure.persistence.*`
- `wallet.api.action.*`

## Scheduler And Compensation

Owner / SSOT：

- 调度和补偿任务由各 owner 域的 job / handler 承载。
- 具体业务动作仍由 owner domain 执行，例如 auth cleanup、outbox worker。

Entry：

- XXL Job handlers。
- 本地 `@Scheduled`。

Current tasks：

- `OutboxWorkerScheduler`
- 帖子热度刷新 / score refresh
- `marketOrderAutoConfirm`
- `marketWalletActionProcessor`
- `marketWalletActionRecovery`

Task details：

- `RefreshTokenCleanupJob` 是本地 `@Scheduled` 清理，受 `auth.refresh.cleanup.interval-ms` 和 `auth.refresh.cleanup.enabled` 控制，调用 refresh token application 删除过期 session。
- `PostScoreRefresher` 是本地 `@Scheduled`，受 `content.score.refresh.enabled`、`content.score.refresh.delay-ms` 和 `content.score.refresh.batch-size` 控制，batch size 被限制在 1 到 2000。
- `MarketOrderAutoConfirmHandler` 是 XXL `marketOrderAutoConfirm`，进入 market owner 自动确认 due orders，只写 release command。
- `MarketWalletActionProcessorHandler` 是 XXL `marketWalletActionProcessor`，每轮处理数量受 `market.wallet-action.process-batch-size` 控制。
- `MarketWalletActionRecoveryHandler` 是 XXL `marketWalletActionRecovery`，每轮 reconcile 数量受 `market.wallet-action.recovery-batch-size` 控制；同时恢复过期 lease、补齐命令和应用已有 wallet 结果。
- `OutboxWorkerScheduler` 是 common-outbox 本地 `@Scheduled` worker，按配置轮询 outbox 表。

Rules：

- scheduler / job 不直接拼业务规则。
- 本地 listener / job 只能进入同域 `ApplicationService`。
- 需要单实例执行的任务使用 single-flight。
- 清理和补偿任务必须可重跑。

Outbox worker：

- 轮询 `community.outbox_event`。
- 恢复过期 lease。
- claim due PENDING events。
- 按 topic dispatch handler。
- 成功标记 `SUCCEEDED`。
- 失败重试，超过次数进 `DEAD`。

Search reindex：

- HTTP 和 XXL 走同一个 search action / application service。
- Redis-backed single-flight 防并发。
- alias 原子切换保证搜索服务不中断。
- 已存在 alias 的 mapping 必须满足当前必需字段；不满足时启动失败，不做静默兼容迁移。

Failure：

- outbox 失败不打爆 HTTP 主写路径。
- `DEAD` 事件需要人工排查。
- single-flight lock 异常要看 Redis 和 heartbeat。
- 本地 cleanup / score refresh job 捕获异常并记录日志，不回滚其他业务事务。
- XXL handler 捕获异常后通过 `XxlJobHelper.handleFail(...)` 标记失败；成功或 skipped 会写 job log 并 `handleSuccess(...)`。
- fail-open / fail-closed 由上层任务语义决定。

Key code：

- `auth.infrastructure.job.RefreshTokenCleanupJob`
- `content.infrastructure.job.PostScoreRefresher`
- `market.infrastructure.job.MarketOrderAutoConfirmHandler`
- `market.infrastructure.job.MarketWalletActionProcessorHandler`
- `market.infrastructure.job.MarketWalletActionRecoveryHandler`
- `common-outbox` 的 `OutboxWorker` / `OutboxWorkerScheduler`
