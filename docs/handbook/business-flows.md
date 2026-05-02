# 业务实现手册

本文档替代旧的 `docs/handbook/business-logic/*.md`。每个业务域只保留当前代码真实存在的入口、owner、主流程、失败语义和关键代码定位。架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)。

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
3. 注册创建待激活用户和 registration token。
4. 验证码通过邮件发送；本地默认用 MailHog。
5. 验证注册验证码后激活用户，并可自动登录。
6. 登录先经过登录风控、验证码要求和密码校验。
7. 密码格式兼容历史 hash。
8. 登录成功后签发 access token，并通过 HttpOnly cookie 下发 refresh token。
9. refresh token 旋转刷新，旧 token 失效；family reuse 可触发族撤销。
10. logout 撤销 refresh token / family 并清 cookie。
11. cleanup job 清理过期待激活用户和 session 状态。

Password reset：

1. 请求重置密码必须带邮箱和验证码。
2. 服务端先校验 reset base URL 配置，避免签发 token 后才发现无法生成链接。
3. 邮箱不存在、用户未激活或状态不可用时也返回“已受理”，但不签发 token，不发邮件，防止用户枚举。
4. token 存储在 `auth:pwdreset:<token>`，通过邮件下发链接，HTTP 响应体不返回 reset link。
5. 确认重置时再次校验验证码、token 和密码策略。
6. 密码策略由 user owner 校验：长度 8 到 `ValidationLimits.PASSWORD_MAX`，至少包含两类字符。
7. 密码更新成功后撤销该用户 refresh sessions；若密码更新失败，会恢复 reset token TTL 以便用户重试。

Security：

- `AuthSecurityRules` 决定 `/api/auth/**` 哪些入口允许匿名。
- `AuthOriginGuardFilter` 只覆盖 login / refresh / logout 这类 cookie 会话敏感入口。
- prod 下禁止固定验证码和验证码/reset link 回传，SMTP 必须可用。
- `/api/auth/me` 直接读 JWT claim；角色变化通常要等 token 重签后体现。

Key code：

- `auth.application.AuthApplicationService`
- `auth.application.*ApplicationService`
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

- user 域拥有用户基础资料、头像和用户摘要。
- 资料聚合会回源内容域读取最近帖子 / 评论等只读视图。

Entry：

- `/api/users/**`
- `/files/**`
- 头像 token / upload / confirm 相关接口以 `UserController` 为准。

Main path：

1. 用户资料聚合读取用户基础资料。
2. 最近帖子和最近评论来自 content owner 查询。
3. 批量用户摘要用于内容、通知、社交等展示。
4. 当前聚合字段和行为由测试锁定。
5. 头像上传三段式：
   - 签发上传 token。
   - 客户端上传文件。
   - 确认并写回头像 URL。
6. 文件访问通过 `/files/**` 暴露。
7. 本地 provider 支持本地存储；R2 provider 支持对象存储直传。

Failure / security：

- 上传 ticket 绑定 `fileName -> userId`，Redis TTL。
- confirm 时一次性消费 ticket。
- `fileName` 必须为 `avatar/{userId}/...`。
- MIME 白名单和 2 MiB 大小限制。
- 上传失败不能兜底更新头像。

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
- `/api/categories/{categoryId}/subscribe`
- `/api/subscriptions/categories`
- `/api/tags/**`

Main path: create post：

1. `PostController.create(...)` 读取登录用户和 `Idempotency-Key`。
2. 进入 `PostPublishingApplicationService.create(...)`。
3. `IdempotencyGuard.executeRequired(...)` 包裹真实写操作。
4. application 做文本清洗、分类存在性校验、用户发言资格校验。
5. domain / repository 完成 `DiscussPost` 落库。
6. tag 绑定；新 tag 通过 `ensureTagId(...)` 幂等创建。
7. 同步触发积分 / 任务进度 owner API。
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
7. 同步触发积分 / 任务进度 owner API。
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
7. 同步调用积分 / 任务进度 owner API。
8. 发布 social domain event。
9. `LocalSocialDomainEventPublisher` 映射为 `SocialContractEvent`。
10. notice / growth 等下游消费。

Main path: follow：

- 关注写入关注关系并发布 follow created 事件。
- `unfollow(...)` 当前只删除关注关系，不发布 `FollowRemoved` 事件。

Payload semantics：

- 点赞事件里的目标用户和帖子归属由服务端权威解析。
- 如果 `entityUserId` 不合法，积分/任务下游会跳过。
- 自己给自己点赞不会获得积分奖励。

Consistency：

- 点赞 / 关注主业务写入在当前事务域内。
- 积分 / 任务进度是同步 owner API，不再通过旧 points / task-progress outbox adapter。
- notice 是 best-effort after-commit。
- `events.outbox.enabled` 不会启用旧 notice / points / task-progress social outbox adapter。

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
6. `ImPolicyKafkaOutboxHandler` 发布到 IM policy Kafka topic。
7. `im-realtime` 消费事件，刷新本地 policy projection。
8. `im-realtime` 发送私信前用本地 projection 判断拉黑、处罚、目标用户状态。

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
- 存储复用 `community.message` 表承载站内通知语义，不再承载 IM 私信 SSOT。

Entry：

- `/api/notices/**`
- content / social / moderation contract events。

Read path：

- 通知列表。
- 未读数。
- 摘要。
- 批量已读，`ids` 是通知 UUID 列表，不能使用旧 numeric message id。

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

- `im-realtime` owns WebSocket 连接态和在线推送。
- `im-core` owns 私信会话、消息、顺序号、幂等、历史、未读。
- `community-app` owns 用户处罚和拉黑 SSOT，并提供 policy snapshot。

Entry：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：session response `wsUrl`，gateway worker-proxy 模式下形如 `ws://localhost:12880/ws/im/workers/{workerId}`
- HTTP history：`http://localhost:12880/api/im/**`

Main path：

1. 客户端带 bearer token 调 `POST /api/im/sessions`。
2. `im-realtime` 校验 token，生成 session ticket，并返回 `wsUrl`。
3. 客户端连接返回的 `wsUrl`，gateway worker-proxy 模式下路径为 `/ws/im/workers/{workerId}`。
4. WebSocket 建连后客户端发送 `connect` frame 和 ticket，`im-realtime` 完成鉴权并注册连接。
5. 首次鉴权后，`im-realtime` 也会从 `im-core` 拉取房间 membership snapshot，主要服务群聊索引。
6. 客户端发送 `sendPrivateText`。
7. `im-realtime` 确认连接已鉴权。
8. 本地 `PolicyProjectionService` 判定拉黑、处罚、目标用户存在性。
9. 判定通过后写 `im.command.private-text`。
10. `im-core` 消费 command，校验并按 `(conversationId, fromUserId, clientMsgId)` 幂等。
11. `im-core` 分配 seq、落库、更新会话状态。
12. `im-core` 发布 `im.event.private-persisted`。
13. `im-realtime` 消费 persisted event 并在线推送。
14. 客户端断线或错过推送时，通过 HTTP history API 补拉。

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

- `im-core` owns 房间、成员、群消息、seq、read state。
- `im-realtime` owns 在线房间索引和更新通知扇出。

Entry：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：session response `wsUrl`，gateway worker-proxy 模式下形如 `ws://localhost:12880/ws/im/workers/{workerId}`
- HTTP room history：`http://localhost:12880/api/im/**`

Main path：

1. 客户端带 bearer token 调 `POST /api/im/sessions`。
2. 客户端连接返回的 `wsUrl`，发送 `connect` frame 和 ticket 完成 WebSocket 鉴权。
3. `im-realtime` 调 `im-core` internal membership snapshot，拉取当前用户所在房间。
4. `im-realtime` 建立本机在线房间索引。
5. 客户端发送 `sendRoomText`。
6. `im-realtime` 写 `im.command.room-text`。
7. `im-core` 消费 command，校验房间存在、发送者是成员。
8. `im-core` 按 `(roomId, fromUserId, clientMsgId)` 做幂等。
9. `im-core` 分配 room seq，持久化消息。
10. `im-core` 发布 `im.event.room-persisted`。
11. `im-realtime` 收到 event 后，不一定广播完整消息，而是推送 `roomUpdatedBatch`。
12. 客户端收到更新后，通过 HTTP 拉取群消息并推进 `lastReadSeq`。

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

## Search Projection And Reindex

Owner / SSOT：

- content owns 帖子主事实。
- search owns搜索查询、ES repository、索引别名和 reindex。
- ES 是最终一致读模型。

Entry：

- `GET /api/search/posts`
- `POST /api/ops/search/reindex`
- XXL Job：`searchReindex`
- content event -> search outbox。

Search query：

1. `SearchController.searchPosts(...)`。
2. `SearchApplicationService.searchPosts(...)`。
3. `PostSearchDomainService` 校验 keyword、category、tag、page、size。
4. repository 查询 ES 或 memory implementation。
5. 支持标题/内容全文检索、分类过滤、标签过滤、score + createTime 排序、关键词高亮。
6. 单页最大 50，避免深分页风险。
7. 无关键词时退化为 match-all，兼容纯分类/标签过滤。

Projection：

1. content 变化发布事件。
2. `PostOutboxEnqueuer` 写 `projection.search.post` outbox。
3. `OutboxWorker` dispatch 到 `PostOutboxHandler`。
4. handler 反序列化 payload，只把它作为触发信号。
5. handler 回源 content owner 当前帖子状态。
6. 当前状态应索引则 upsert ES；已删除 / 不应索引则 delete ES。

Reindex：

1. 通过 HTTP ops 或 XXL 触发。
2. `SearchReindexApplicationService` 获取 single-flight 执行权。
3. 生成 `community_posts_vYYYYMMDDHHmmss[_n]`。
4. 创建新索引并应用 mapping。
5. 启动 heartbeat 续期。
6. 游标分页扫描 content 权威快照。
7. 批量写入新索引。
8. 原子切换 alias `community_posts_alias`。
9. 清理超过保留数量的旧索引。

Failure：

- ES 故障不阻断发帖、评论、社交等主业务写入。
- 搜索查询会受 ES 故障影响。
- outbox 会重试投影失败。
- reindex 失败不影响旧 alias 继续服务。

Key code：

- `search.controller.SearchController`
- `search.application.SearchApplicationService`
- `search.application.SearchReindexApplicationService`
- `search.domain.service.PostSearchDomainService`
- `search.infrastructure.persistence.ElasticsearchPostSearchRepository`
- `search.infrastructure.persistence.InMemoryPostSearchRepository`
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
- `/api/analytics/me`：鉴权联调接口，不是业务埋点主入口。

Current state：

- 当前 analytics 对外 HTTP 面主要是查询，不是任意客户端埋点写入。
- 默认采集路径包括 `/api/posts/**`、`/api/search/**`、`/api/messages/**`、`/api/notices/**`、历史 `/api/im-governance/**`。
- `/api/im-governance/**` 是遗留采集配置；当前 IM governance 已迁到 realtime 本地 projection 和 `/internal/im/realtime/projections/**` snapshot。
- `/internal/**` 默认被 exclude，不进入 analytics filter 采集。

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
- 区间查询和 bitmap offset 要受实现限制约束。

Key code：

- `analytics.application.*ApplicationService`
- `analytics.domain.service.*`
- `analytics.infrastructure.*`
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

当前明确不存在：

- 对外签到 controller。
- 签到事实表。
- 任务中心 controller / service / DTO。
- 手动领取奖励接口。
- growth 专属 security rules。
- 奖励商城、排行榜等旧 growth 面。

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

- 当前积分投影不再依赖 `reward_grant_record` 作为在线幂等面。
- 奖励/积分最终通过钱包侧 requestId 去重和总账规则承担幂等。
- 积分投影先翻译成统一命令，再进入 wallet owner。

Level：

- 当前等级按签到任务完成数计算。
- 等级不是用户表上的独立实时字段。
- 配置修改落入等级规则配置。

Failure：

- 幂等不靠 listener 自身，而靠 source event log、任务进度状态和 wallet requestId。
- 旧签到 / 任务中心名称在历史计划或设计稿里可能存在，但当前代码表面已退休。

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
- 预置库存用于虚拟或特定商品交付。

Order path：

1. controller 读取 `Idempotency-Key`；旧 body `requestId` 仅 header 缺失时 fallback。
2. header/body 不一致返回 `400`。
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
- 旧 body `requestId` 仅 header 缺失时 fallback。
- header/body 不一致返回 `400`。
- HTTP 幂等 key 与总账 requestId 解耦。

Ledger idempotency：

- `requestId` 重放且交易类型、业务类型、业务 id、金额和分录指纹一致：复用或返回等价结果。
- `requestId` 重放但语义不一致：`REQUEST_REPLAY_CONFLICT`。
- 资金动作必须通过总账规则保证双分录一致。
- 总账分录必须借贷平衡，借方总额必须为正且不超过 `WalletAmountPolicy.MAX_AMOUNT`。
- 金额累加使用 checked arithmetic，溢出会作为业务非法请求拒绝。

Market / reward integration：

- market 托管、放款、退款由 market wallet action processor 调用 wallet market action API。
- growth / reward 积分或奖励写入 wallet，由钱包 requestId 去重。
- wallet 是在线余额事实 owner；历史 reward account / ledger 表不再作为在线余额运行面。
- 冻结钱包不能发起用户主动转账、提现或市场购买。
- 冻结钱包仍可接收系统必须完成的入账或补偿动作，例如退款、放款、奖励和管理员调整。

Failure：

- 资金类 HTTP 写入口幂等存储异常时 fail-closed。
- 总账 requestId 冲突不会重复记账。
- 冻结、冲正和转账必须保持账户与 entry 一致。
- 钱包账户余额更新使用 version 条件更新；余额不足或并发冲突会转成明确业务错误。

Key code：

- `wallet.controller.*`
- `wallet.application.*ApplicationService`
- `wallet.domain.*`
- `wallet.infrastructure.persistence.*`
- `wallet.api.action.*`

## Ops Scheduler And Compensation

Owner / SSOT：

- ops 域提供对外运维平面。
- 具体业务动作仍由 owner domain 执行，例如 search reindex、auth cleanup、outbox worker。

Entry：

- `/api/ops/**`
- XXL Job handlers。
- 本地 `@Scheduled`。

Current tasks：

- `pendingRegistrationUserCleanup`
- `searchReindex`
- `OutboxWorkerScheduler`
- 帖子热度刷新 / score refresh
- `marketOrderAutoConfirm`
- `marketWalletActionProcessor`
- `marketWalletActionRecovery`

Rules：

- scheduler / job 不直接拼业务规则。
- 本地 listener / job 只能进入同域 `ApplicationService`。
- 跨域 job 调用 owner `api.action`。
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

Failure：

- outbox 失败不打爆 HTTP 主写路径。
- `DEAD` 事件需要人工排查。
- single-flight lock 异常要看 Redis 和 heartbeat。
- fail-open / fail-closed 由上层任务语义决定。

Key code：

- `ops.controller.OpsController`
- `ops.application.OpsApplicationService`
- `search.application.ReindexJobApplicationService`
- `search.application.SearchReindexApplicationService`
- `common-outbox` 的 `OutboxWorker` / `OutboxWorkerScheduler`
