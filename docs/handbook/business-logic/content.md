# Content 内容业务逻辑

内容域是主社区链路的核心 owner，拥有帖子、评论、分类、标签、收藏、订阅、举报和内容治理状态。它还负责把内容变更转成事件，驱动搜索、通知、成长任务、奖励和社交清理。

## Owner / SSOT

- content owns 帖子、评论、回复、分类、标签、帖子标签关系、收藏关系、分类订阅关系、举报记录和内容治理动作。
- user owns 作者账号、处罚状态和角色。
- social owns 点赞、关注和拉黑关系。
- search owns Elasticsearch 索引读模型。
- notice owns 站内通知读模型。
- growth/wallet owns 任务进度、奖励和余额事实。

## 入口

HTTP：

- `GET /api/feed/global`
- `GET /api/boards/{boardId}/feed`
- `GET /api/feed/follow`
- `POST /api/posts`
- `POST /api/posts/batch-summary`
- `GET /api/posts/{postId}`
- `PUT /api/posts/{postId}`
- `DELETE /api/posts/{postId}`
- `POST /api/posts/{postId}/delete`
- `POST /api/posts/{postId}/top`
- `POST /api/posts/{postId}/wonderful`
- `GET /api/posts/{postId}/comments`
- `POST /api/posts/{postId}/comments`
- `PUT /api/posts/{postId}/comments/{commentId}`
- `GET /api/posts/{postId}/comments/{commentId}/replies`
- `GET /api/categories`
- `GET /api/subscriptions/categories`
- `GET /api/tags/hot`
- `GET /api/tags/suggest`
- `PUT /api/posts/{postId}/bookmark`
- `DELETE /api/posts/{postId}/bookmark`
- `GET /api/bookmarks`
- `POST /api/reports`
- `GET /api/moderation/reports`
- `POST /api/moderation/actions`
- `GET /api/moderation/actions`
- `POST /api/posts/media/upload-sessions`
- `POST /api/posts/media/{assetId}/upload`

事件/内部：

- content domain event bridge 进入 `PostContractEventApplicationService` / `CommentContractEventApplicationService`，将 contract event 与 owner 主事实同事务写入 `eventbus.content`。
- `ContentEventKafkaOutboxHandler` 发布 `content.events`，Search、Notice、Growth、Wallet reward、Hot feed 和 Social deletion cleanup 的 Kafka listener 分别进入各自同域 ApplicationService。
- 内容删除只提交 content 主事实和 owner event；social 通过 `content.events` 异步清理失效点赞关系。

## 数据流

内容域是社区主写路径的事件源，核心数据流如下：

1. 读帖：全局/版块/关注 feed 分别从 `FeedController` 进入 `FeedReadApplicationService` / `FollowFeedReadApplicationService`；详情和批量摘要从 `PostController` 进入 `PostReadApplicationService`。content 以帖子主事实为准，不从 search 或 notice 反查。
2. 发帖：`PostPublishingApplicationService.create(...)` 用 `IdempotencyGuard` 包住写操作，先回源 user 判断能否发言，再写帖子元信息、正文 blocks、媒体引用 desired state 和 tag 关系。写入后发布 domain event，并由 owner outbox / Kafka 驱动 Search、Notice、Growth、Wallet reward 和 Hot feed 下游投影。
3. 媒体：帖子媒体先在 content 保存 draft asset，再通过 OSS upload session 完成 blob 上传。发帖或改帖时只允许使用当前用户已上传且类型匹配的 asset；主事务把引用写成 pending 并同事务 enqueue command，OSS bind/release 在 outbox handler 中执行。
4. 评论：`CommentApplicationService` 校验帖子、目标评论、作者发言资格和拉黑关系后写 `comment`，同步更新帖子评论数并发布评论事件；奖励、成长任务和通知由 `content.events` consumer 异步追平。
5. 删除和治理：作者删除、治理删除和帖子下线都改变 content 主事实，再发布事件让 search 删除或更新 ES 文档、notice 生成治理通知、social listener 清理失效实体上的点赞关系。

## 帖子读取

feed HTTP 列表使用 opaque cursor：`FeedReadApplicationService` 读全局/版块热流，`FollowFeedReadApplicationService` 读关注流。`PostReadApplicationService` 负责详情、批量摘要和 owner query 所需的帖子读模型装配：

1. `listPosts(...)` 只通过 content owner query contract 供跨域组装使用，不对应已退役的 `GET /api/posts` 列表路由。
2. owner query 可按 `order`、`categoryId`、`tag` 和订阅条件组装摘要。
3. feed 的排序与分页语义以 `FeedReadApplicationService` 返回的 rank version 和 opaque cursor 为准。
4. 读出帖子后批量加载最新评论活动、标签和正文 blocks。
5. 摘要装配不把点赞状态放在 content 主事实里。
6. 摘要 `preview` 从 blocks 投影生成，不读取旧正文内容字段。
7. `getPostDetail(...)` 读取帖子详情、正文 blocks、媒体资源、标签、点赞数、当前用户点赞状态、收藏状态。
8. 帖子详情返回 `blocks`，正文由 `paragraph`、`code`、`image`、`video`、`file` 组成。
9. 媒体 block 会带出可展示的媒体视图；视频允许以处理中状态展示。
10. `listPostsByUser(...)` 用于用户主页最近帖子。
11. `listPostsByIds(...)` 用于批量摘要。

匿名用户可读公开帖子，但订阅流和收藏状态需要当前用户。

### Feed、缓存与降级

全局和版块 hot feed 的读取顺序：

1. `FeedReadApplicationService` 解码 opaque cursor，并把 page size 约束在 `1..50`。
2. 优先从 `PostFeedCache` 读取全局或版块帖子 ID，再由 `PostFeedSummaryLoader` 读取/回填摘要；返回值携带当前 rank version。
3. cache miss 或 Redis 异常时进入跨节点 `HotPathSingleFlight`。同一 scope/page/size 只有一个请求回源，其他请求返回空的 degraded-safe 页面并记录 `singleflight_busy`，避免并发打满 repository。
4. `content.feed.latest-fallback-enabled=true` 时，leader 从 content repository 按 hot order 回源，best-effort 回填 feed 和 summary cache；关闭 fallback 时 miss 返回空页。
5. Redis 读取、rank version、回填失败不会把缓存当主事实；路径记录 `hit`、`fallback`、`empty`、`degraded` 或 `singleflight_busy` 指标。

关注流由 `FollowFeedReadApplicationService` 处理：必须登录，最多向 social 查询 `200` 个 followee，以 `createTime + postId` 游标读取最近可见帖子；页面缓存不是关注关系或帖子事实。

`CacheTtlPolicy` 对 summary、detail、comment page 和 follow page 使用稳定抖动：以 cache key 的 CRC32 在 `0..ttlJitterSeconds` 取偏移并加到基础 TTL，同一个 key 的 TTL 可重复，批量 key 不会同刻过期。当前代码默认基础 TTL 分别为 `300s`、`300s`、`120s`、`60s`，jitter 为 `60s`；非正基础 TTL 收敛到 `1s`。

### 热度、预热与 Counter

`PostHotFeedProjectionKafkaListener` 识别 post publish/update/delete、comment create/delete 和 post like create/remove。已识别事件必须有 event ID、发生时间和正数 owner version；事件信号权重分别为 `+1/+1/0/+1/-1/+1/-1`。`PostHotFeedProjectionApplicationService` 使用 source event ID/version guard 拒绝重复或旧投影，回源当前帖子和点赞事实后再更新：

```text
weight = (wonderful ? 75 : 0)
       + max(commentCount, 0) * 10
       + max(likeCount, 0) * 2
       + signalWeight
score  = log10(max(weight, 1)) + daysSince(2014-08-01 UTC)
```

删除、下线或非正常状态的帖子会从 feed、summary 和 detail cache 移除。正常帖子更新 DB score、counter cache、global/board hot feed，并淘汰旧 summary/detail；projection guard 只在业务事务提交后 commit，回滚或异常时 abort，失败交给 Kafka retry / `.dlq`。

`HotPathPrewarmApplicationService` 在 single-flight 锁内从 content 当前事实预热全局及前 N 个版块的 hot feed、summary 和 detail。代码默认每次 `2` 页、每页 `20`、最多 `20` 个版块、锁 TTL `30s`；`HotPathPrewarmJob` 默认每 `60s` 调用一次，重复节点由 single-flight 收敛。

`PostCounterApplicationService` 把浏览、点赞、评论、收藏和 score 作为派生 counter 读取模型：同一 `postId + viewerKey` 默认 `86400s` 内只增加一次浏览；脏 post ID 进入 Redis 有序集合。读取时以 cache view/bookmark 为基础，并回源 social 点赞数、content 评论数和持久 score；flush 每批限制在 `1..500`，upsert `post_counter_snapshot` 成功后才移除 dirty marker。`PostCounterSnapshotFlushJob` 默认启用、每 `30s` 运行，默认 batch `200`；失败只记录并留待下轮重试。

## 发帖

`PostPublishingApplicationService.create(...)` 是发帖写路径：

1. controller 读取当前用户、`Idempotency-Key` 和请求体。
2. application 使用 `IdempotencyGuard.executeRequired("content:create_post", userId, key, ...)` 包裹真实写操作。
3. `ModerationGuard.assertCanSpeak(userId)` 同步回源 user owner 判断是否可发言。
4. 校验分类存在。
5. 校验并规范化正文 blocks，标题、段落、代码、说明文本做 HTML 转义和敏感词处理。
6. `PostPublishingDomainService.createDraft(...)` 只生成帖子元信息草稿。
7. `PostRepository.create(...)` 写入帖子元信息。
8. 校验媒体资源归属、类型和上传状态，并把被 blocks 引用的媒体资源绑定到帖子。
9. `PostContentBlockRepository.replaceBlocks(...)` 写入有序正文 blocks。
10. `PostTagRepository.bindTagsToPost(...)` 绑定标签。
11. 发布 `PostPublishedDomainEvent`。
12. domain event bridge 将帖子事实映射为 content contract event，并写入 outbox。
13. content contract event 进入 Kafka 后，驱动 Search、Notice、Wallet reward、Growth 和 Hot feed 投影异步追平。
14. `PostHotFeedProjectionApplicationService` 收到该 owner event 后回源帖子/点赞当前状态，重算 score 并更新缓存与 hot feed。
15. 写业务事件日志。

幂等语义按 operation + userId + key 去重；发帖指纹包含 title、categoryId、tags 和 blocks。同 key 且指纹相同时返回首次结果，指纹不同时返回 replay conflict。

帖子写接口只接收 block payload。项目尚未部署，没有历史帖子数据，当前实现不保留正文格式迁移路径。

## 帖子媒体上传

`PostMediaApplicationService` 负责帖子正文 block 引用的图片、视频和附件上传会话。

Prepare upload：

1. controller 从登录态解析 actor。
2. `prepareUpload(...)` 校验文件名、content type、媒体类型和文件大小。
3. 文件名 trim 后不能为空，不能超过 255，不能包含 `..`、`/`、`\` 或 null 字符。
4. 支持的 MIME：`image/png`、`image/jpeg`、`image/webp`、`image/gif`、`video/mp4`、`video/webm`、`application/pdf`、`application/zip`。
5. 如果请求显式传 `mediaKind`，必须与 content type 推断出的 `IMAGE`、`VIDEO` 或 `FILE` 一致。
6. 大小限制：图片 10MiB，视频 100MiB，文件 50MiB。
7. application 创建 `PostMediaAsset` draft，生命周期为 `DRAFT`，上传状态为 `PREPARED`，视频状态初始为 `NONE`。
8. 通过 `PostMediaStoragePort.prepareUpload(...)` 创建 OSS 上传会话。
9. OSS 返回的 assetId、objectId、versionId 和 uploadId 必须可回填到 draft，否则视为内部签发失败。
10. draft 持久化后返回上传指令。

Complete upload：

1. `POST /api/posts/media/{assetId}/upload` 传 `uploadId` 和 multipart `file`。
2. application 校验 actor、assetId、uploadId 和文件内容。
3. 只能完成自己的媒体资源，且 upload session、content type 和 content length 必须与 prepare 记录一致。
4. asset 必须仍处于 `DRAFT/PREPARED`；application 以当前 `uploadOperationVersion` 条件 claim，进入 `COMPLETING` 并递增版本。
5. 通过 `PostMediaStoragePort.completeUpload(...)` 在数据库事务外完成 OSS 上传。
6. OSS complete 必须返回 versionId；content 在新事务内以 claimed version 写入 canonical metadata，状态变为 `OBJECT_COMPLETED`。
7. 最后再以同一 operation version 将状态推进到 `COMPLETED`；已完成请求幂等返回。

上传状态机为 `PREPARED -> COMPLETING -> OBJECT_COMPLETED -> COMPLETED`，终态还包括 `FAILED`。`uploadOperationVersion` 防止旧 complete 或 recovery 覆盖新 claim。

`PostMediaUploadRecoveryJob` 扫描长期停留在 `COMPLETING/OBJECT_COMPLETED` 的 asset：`OBJECT_COMPLETED` 只需补最后一次状态提交；`COMPLETING` 会查询 OSS canonical metadata，未知结果重置为 `PREPARED` 供重试，远端不存在标为 `FAILED`，远端已完成则补写 metadata 并推进到 `COMPLETED`。Nacos seed 默认启用，batch `50`、stale `300s`、delay `60s`。

绑定到帖子：

1. 发帖 / 改帖时，`PostPublishingApplicationService` 从正文 blocks 收集 media asset id。
2. 每个媒体资源必须属于当前用户、上传已完成、类型与 block 类型一致。
3. 新引用在 content 主事务内写 `BIND_PENDING`，移除或删帖写 `RELEASE_PENDING`；每次 desired-state 变化递增 `referenceOperationVersion`。
4. 同一事务通过 `OutboxPostMediaReferenceCommandPublisher` enqueue `command.content.post-media-reference`。确定性 event ID 为 `content-media-reference:<assetId>:<operationVersion>:<operation>`。
5. `PostMediaReferenceOutboxHandler` 在主事务之后调用 `PostMediaReferenceApplicationService`。handler 先比较 command version；旧版本直接 no-op，然后在事务外执行 OSS bind/release，并在新事务内以同一 version 标记 `BOUND/RELEASED`。
6. 完整引用状态机为 `UNBOUND -> BIND_PENDING -> BOUND -> RELEASE_PENDING -> RELEASED`；新的 release 可以覆盖尚未完成的 bind，版本 fencing 防止迟到 bind 回写。
7. 帖子详情读取 blocks 时，把媒体 asset 投影为展示视图；视频允许展示处理中状态。

`PostMediaReferenceReconciliationJob` 分页扫描引用状态：pending command 会重新发布；`BOUND` 但帖子已删除时按 `deleted_post` 调度 release，远端引用缺失时按 `remote_missing` 调度 bind repair，`RELEASED` 但远端仍 active 时按 `remote_active` 调度 release repair。Nacos seed 默认启用，batch `50`、delay `300s`。

## 改帖和删帖

编辑帖子：

1. 校验用户可发言。
2. 校验分类存在。
3. 读取帖子快照。
4. `PostPublishingDomainService.assertEditableByAuthor(...)` 校验作者和状态。
5. 校验并清洗新的正文 blocks。
6. 绑定新增媒体资源。
7. 更新标题、分类和 `update_time`。
8. 替换正文 blocks。
9. 释放已从正文移除的媒体资源引用。
10. 替换标签。
11. 发布 `PostUpdatedDomainEvent`。
12. `PostUpdated` 事件经 `content.events` 触发 hot-feed 投影重算。

作者删除：

1. 读取帖子快照。
2. 校验作者和可删除状态。
3. 软删除帖子。
4. 发布 `PostDeletedDomainEvent`。
5. `PostDeleted` owner event 经 `content.events` 触发 social deletion cleanup。
6. `PostDeleted` 事件经 `content.events` 从 hot feed 和读缓存移除该帖子。

治理删除由 `PostModerationApplicationService.deleteByModeration(...)` 处理，面向管理员/版主，并走同样的内容下线和事件扩散语义。

## 评论和回复

`CommentApplicationService.create(...)` 是评论写路径：

1. controller 读取当前用户、`Idempotency-Key`、路由 `postId` 和请求体。
2. application 先做参数校验，再用 `IdempotencyGuard` 包裹写入。
3. 真实写入放入显式事务模板，避免幂等外层事务污染。
4. 校验用户可发言。
5. 回源读取帖子。
6. `CommentDomainService.resolveCreateTarget(...)` 解析评论目标：
   - 对帖子评论：目标是帖子作者。
   - 对评论回复：目标评论必须 active，且归属当前帖子。
7. 如果评论者和目标用户存在拉黑关系，拒绝评论。
8. 文本清洗和敏感词处理。
9. 写入评论。
10. 增加帖子评论数。
11. 发布 `CommentCreatedDomainEvent`。
12. domain event bridge 进入 content application，将 comment contract event 与评论主事实同事务写入 `eventbus.content`。
13. owner outbox handler 发布 `content.events`，Notice、Growth 和 Wallet reward listener 进入各自 ApplicationService；失败走 Kafka retry / `.dlq`。
14. 评论事件经 `content.events` 触发 hot-feed 投影重算。

评论编辑：

- 只能作者编辑。
- 评论必须 active。
- 路由帖子必须与真实帖子归属一致。
- 编辑窗口由 domain service 控制，当前为创建后 15 分钟内。
- 编辑后更新内容、更新时间和编辑次数。

评论删除：

- 作者删除校验作者和路由帖子。
- 治理删除校验治理动作。
- 删除是软删除，并包含 active descendant replies。
- 按实际从可见变删除的数量递减帖子评论数。
- 对每条被删评论发布删除事件。
- 被删评论的点赞由 `content.events -> SocialContentDeletionKafkaListener` 异步清理，并由 social reconciliation 追平。

## 分类、标签、收藏、订阅

分类：

- `CategoryApplicationService.listCategories()` 返回分类列表。
- application 只做 domain model 到 `CategoryResult` 的转换。
- 返回字段包括 id、name、description、position 和 postCount。
- 分类排序、过滤和计数口径由 `CategoryContentRepository` 实现决定；controller 不直接访问 repository。
- 发帖和改帖必须校验分类存在。
- 分类是内容组织主事实，不由用户域持有。

标签：

- `TagApplicationService.listHotTags(limit)` 返回热门标签。
- `suggestTags(q, limit)` 按输入建议标签。
- application 只做 `HotTag` 到 `HotTagResult(name, useCount)` 的转换。
- limit、关键词规范化和结果排序由 `TagContentRepository` 承担。
- 发帖绑定标签，改帖替换标签。

收藏：

- `BookmarkApplicationService.add(userId, postId)` 收藏帖子。
- `remove(...)` 取消收藏。
- `listBookmarkedPostSummaries(...)` 回源帖子摘要。
- 收藏是用户对帖子的读偏好，不改变帖子主事实。

订阅：

- `listSubscribedCategoryIds(...)` 返回用户订阅分类。
- 订阅流要求登录态，当前运行面只保留订阅列表读取。

## 举报和审核

举报：

1. 用户提交目标类型、目标 ID、原因和详情。
2. content 校验目标存在。
3. 写举报记录。
4. 返回举报 ID。

审核：

1. 管理员/版主查询举报列表。
2. `ModerationApplicationService.takeAction(...)` 根据 action、reason、duration 决策。
3. `ModerationDecisionDomainService` 解析具体治理动作。
4. 可能执行内容下线、用户处罚、举报状态更新和动作记录。
5. 用户处罚通过 user owner action 更新。
6. 治理结果可投影通知。

帖子置顶/加精/管理删除由 `PostModerationApplicationService` 处理，要求 actor 拥有治理权限。

## 内容事件和投影

domain events：

- `PostPublishedDomainEvent`
- `PostUpdatedDomainEvent`
- `PostDeletedDomainEvent`
- `CommentCreatedDomainEvent`
- `CommentDeletedDomainEvent`

contract events：

- `PostContractEventApplicationService` 将帖子 domain event 映射到 `content.contracts.event`。
- `CommentContractEventApplicationService` 将评论 domain event 映射到 `content.contracts.event`。

下游：

- 发帖、评论、治理等 content contract event 在 owner transaction 写入 `eventbus.content`，由 owner handler 发布到 `content.events`。
- Search、Notice、Growth、Wallet reward、Hot feed 和 Social deletion cleanup 分别从 `content.events` 的 Kafka listener 进入本域 ApplicationService。
- Search 回源 content 当前状态决定 upsert/delete ES；搜索正文从 blocks 投影生成。
- `SocialContentDeletionKafkaListener` 在收到帖子/评论删除 contract event 后进入 social `LikeApplicationService` 清理点赞关系；content 不同步调用 social。

帖子 hot-feed 和社交派生刷新：

- `PostHotFeedProjectionKafkaListener` 消费 `content.events` / `social.events`，只把已识别目标事件交给 `PostHotFeedProjectionApplicationService`。
- `PostHotFeedProjectionApplicationService` 按 source event ID/version 保护投影顺序，并回源当前 post / like 状态。
- 只有 `entityType=POST` 时才会提取 postId；优先用 payload.postId，缺失时回退到 payload.entityId。
- 提取到 postId 后更新 score、summary/detail cache 和 global/board hot feed；失败进入 Kafka retry / `.dlq`，不回滚已提交的 owner 事务。

## Owner Entity Resolution

`ContentEntityResolutionApplicationService.resolve(entityType, entityId)` 是 content owner 暴露给外域的实体归属解析用例。它只支持 `POST` 和 `COMMENT`：

- `POST`：读取帖子主事实，返回帖子作者和帖子 id。
- `COMMENT`：允许读取已删除评论行以解析关系，但目标评论自身必须 active；随后沿评论父链向上追溯到根帖子，最多 12 跳。父评论缺失、非 active、链路不是 `POST/COMMENT`、或超过跳数都会返回无法解析。
- 解析到根帖子后会再次读取帖子主事实，确保帖子存在；返回评论作者和根帖子 id。

这个用例用于点赞、通知、成长等外域判断目标 owner 和根内容，不把 comment/post repository 暴露给外域，也不把 content 内部 domain model 作为跨域协作模型。

## 失败和一致性

- 发帖和评论是 HTTP 幂等写。
- 媒体上传 prepare / complete 当前不走 HTTP Idempotency-Key；重复 complete 受 asset lifecycle 约束。
- 通知、搜索、评论奖励、成长任务和 hot-feed 都通过 owner Kafka consumer retry / `.dlq` 追平；ES 和这些读模型都不是内容主事实。
- 点赞清理是 Kafka-backed 最终一致消费；失败进入 retry / `.dlq`，并由 social reconciliation 扫描遗留点赞，不回滚已提交删除。
- 用户处罚和拉黑关系需要同步回源 owner 判断。
- 删除/下线都是软删除和事件扩散，不物理删除主事实行。
- 媒体 prepare/complete 失败会阻断对应上传动作；reference 主事务只保证 desired state 与 outbox command 原子提交，远端失败由 outbox retry/DEAD 和 reconciliation 追平。

## 关键代码

- `content.controller.PostController`
- `content.controller.PostMediaController`
- `content.controller.CategoryController`
- `content.controller.TagController`
- `content.controller.BookmarkController`
- `content.controller.SubscriptionController`
- `content.controller.ReportController`
- `content.controller.ModerationController`
- `content.application.PostPublishingApplicationService`
- `content.application.PostMediaApplicationService`
- `content.application.PostMediaUploadRecoveryApplicationService`
- `content.application.PostMediaReferenceApplicationService`
- `content.application.PostMediaReferenceSchedulingApplicationService`
- `content.application.PostMediaReferenceReconciliationApplicationService`
- `content.application.PostReadApplicationService`
- `content.application.CommentApplicationService`
- `content.application.CommentReadApplicationService`
- `content.application.BookmarkApplicationService`
- `content.application.SubscriptionApplicationService`
- `content.application.ReportApplicationService`
- `content.application.ModerationApplicationService`
- `content.application.PostModerationApplicationService`
- `content.application.FeedReadApplicationService`
- `content.application.FollowFeedReadApplicationService`
- `content.application.PostHotFeedProjectionApplicationService`
- `content.application.FeedReadApplicationService`
- `content.application.FollowFeedReadApplicationService`
- `content.application.HotPathPrewarmApplicationService`
- `content.application.PostCounterApplicationService`
- `content.application.CacheTtlPolicy`
- `content.application.ContentEntityResolutionApplicationService`
- `content.domain.service.PostHotnessDomainService`
- `content.domain.service.*`
- `content.infrastructure.event.*`
- `content.infrastructure.job.PostMediaUploadRecoveryJob`
- `content.infrastructure.job.PostMediaReferenceReconciliationJob`
- `content.contracts.event.*`
