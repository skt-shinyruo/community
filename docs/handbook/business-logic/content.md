# Content 内容业务逻辑

内容域是主社区链路的核心 owner，拥有帖子、评论、分类、标签、收藏、订阅、举报和内容治理状态。它还负责把内容变更转成事件，驱动搜索、通知、成长任务、积分和社交清理。

## Owner / SSOT

- content owns 帖子、评论、回复、分类、标签、帖子标签关系、收藏关系、分类订阅关系、举报记录和内容治理动作。
- user owns 作者账号、处罚状态和角色。
- social owns 点赞、关注和拉黑关系。
- search owns Elasticsearch 索引读模型。
- notice owns 站内通知读模型。
- growth/wallet owns 任务进度、奖励和余额事实。

## 入口

HTTP：

- `GET /api/posts`
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

- content domain event bridge 发布 content contract event。
- search outbox enqueuer 订阅帖子事件。
- notice projection listener 订阅 content/social/moderation contract event。
- social interaction projection listener 在内容删除后清理社交关系。

## 数据流

内容域是社区主写路径的事件源，核心数据流如下：

1. 读帖：controller 进入 `PostReadApplicationService`，content 先读帖子主事实、正文 blocks、媒体资源、标签和评论活动，再按 viewer 组合点赞、收藏等外部状态。帖子摘要和详情都以 content repository 为主，不从 search 或 notice 反查。
2. 发帖：`PostPublishingApplicationService.create(...)` 用 `IdempotencyGuard` 包住写操作，先回源 user 判断能否发言，再写帖子元信息、正文 blocks、媒体绑定和 tag 关系。提交前同步触发 user points 与 growth task，提交后通过 domain event 驱动 search outbox、notice projection 和 score refresh。
3. 媒体：帖子媒体先在 content 保存 draft asset，再通过 OSS upload session 完成 blob 上传。发帖或改帖时只允许绑定当前用户已上传且类型匹配的 asset，旧引用会释放 OSS reference。
4. 评论：`CommentApplicationService` 校验帖子、目标评论、作者发言资格和拉黑关系后写 `comment`，同步更新帖子评论数并触发积分、成长任务和评论事件。
5. 删除和治理：作者删除、治理删除和帖子下线都改变 content 主事实，再发布事件让 search 删除或更新 ES 文档，让 notice 生成治理通知，并在提交后调用 social owner 清理失效实体上的点赞关系。

## 帖子读取

`PostReadApplicationService` 负责帖子读模型装配：

1. `listPosts(...)` 根据 `order` 选择最新或热门排序。
2. 可按 `categoryId` 和 `tag` 筛选。
3. `subscribed=true` 时必须登录，先读取用户订阅分类，再查订阅流。
4. 读出帖子后批量加载最新评论活动、标签和正文 blocks。
5. 摘要装配不把点赞状态放在 content 主事实里。
6. 摘要 `preview` 从 blocks 投影生成，不读取旧正文内容字段。
7. `getPostDetail(...)` 读取帖子详情、正文 blocks、媒体资源、标签、点赞数、当前用户点赞状态、收藏状态。
8. 帖子详情返回 `blocks`，正文由 `paragraph`、`code`、`image`、`video`、`file` 组成。
9. 媒体 block 会带出可展示的媒体视图；视频允许以处理中状态展示。
10. `listPostsByUser(...)` 用于用户主页最近帖子。
11. `listPostsByIds(...)` 用于批量摘要。

匿名用户可读公开帖子，但订阅流和收藏状态需要当前用户。

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
11. 同步触发 `UserPointsAwardActionApi.awardPostPublished(...)`。
12. 同步触发 `GrowthTaskProgressActionApi.triggerPostPublished(...)`。
13. 发布 `PostPublishedDomainEvent`。
14. 安排帖子热度分刷新。
15. 写业务事件日志。

幂等语义按 operation + userId + key 去重；当前发帖没有请求指纹，因此同 key 重放会返回首次结果。

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
7. application 创建 `PostMediaAsset` draft，生命周期为 `DRAFT`，视频状态初始为 `NONE`。
8. 通过 `PostMediaStoragePort.prepareUpload(...)` 创建 OSS 上传会话。
9. OSS 返回的 assetId、objectId、versionId 和 uploadId 必须可回填到 draft，否则视为内部签发失败。
10. draft 持久化后返回上传指令。

Complete upload：

1. `POST /api/posts/media/{assetId}/upload` 传 `uploadId` 和 multipart `file`。
2. application 校验 actor、assetId、uploadId 和文件内容。
3. 只能完成自己的媒体资源。
4. asset 必须仍处于 `DRAFT`；已经绑定到帖子或处于其他 lifecycle 时拒绝。
5. 通过 `PostMediaStoragePort.completeUpload(...)` 完成 OSS 上传。
6. OSS complete 必须返回 versionId。
7. content 把 asset 标记为 uploaded，并保存 public URL。

绑定到帖子：

1. 发帖 / 改帖时，`PostPublishingApplicationService` 从正文 blocks 收集 media asset id。
2. 每个媒体资源必须属于当前用户、已上传、类型与 block 类型一致。
3. 新引用通过 `PostMediaStoragePort.bindReference(...)` 绑定到 postId。
4. 改帖时不再引用的媒体通过 `releaseReference(...)` 释放 OSS reference。
5. 帖子详情读取 blocks 时，把媒体 asset 投影为展示视图；视频允许展示处理中状态。

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
12. 安排分数刷新。

作者删除：

1. 读取帖子快照。
2. 校验作者和可删除状态。
3. 软删除帖子。
4. 发布 `PostDeletedDomainEvent`。
5. 提交后清理帖子点赞。
6. 安排分数刷新。

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
11. 触发评论积分和成长任务。
12. 发布 `CommentCreatedDomainEvent`。
13. 安排帖子分数刷新。

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
- 事务提交后清理被删评论点赞。

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

- search outbox 根据帖子事件回源 content 当前状态，决定 upsert 或 delete ES 文档；搜索正文从 blocks 投影生成。
- notice listener after-commit best-effort 生成站内通知。
- growth/task 和 user points 在发帖/评论主用例内同步调用。
- social cleanup 在内容删除后清理点赞关系。

帖子分数和社交派生刷新：

- `PostScoreUpdateApplicationService.updateScore(postId, score)` 要求 postId 非空。
- 分数更新和 `PostUpdated` domain event 发布处于同一事务，避免分数写入和后续投影出现裂缝。
- `SocialInteractionProjectionListener` 在 social contract event 提交后监听，属于 best-effort 本地 listener。
- listener 只把事件交给 `SocialInteractionProjectionApplicationService`，不直接碰 repository 或 foreign API。
- `SocialInteractionProjectionApplicationService` 只处理 `LIKE_CREATED` / `LIKE_REMOVED` 且 payload 为 `LikePayload` 的事件。
- 只有 `entityType=POST` 时才会提取 postId；优先用 payload.postId，缺失时回退到 payload.entityId。
- 提取到 postId 后写入 `PostScoreQueue`，由帖子分数刷新任务后续重算。
- 队列写入失败只记录 warn，不回滚 social 主事务，也不阻断点赞/取消点赞。

## 失败和一致性

- 发帖和评论是 HTTP 幂等写。
- 媒体上传 prepare / complete 当前不走 HTTP Idempotency-Key；重复 complete 受 asset lifecycle 约束。
- 通知投影失败不回滚内容写入。
- 搜索投影通过 outbox 重试，ES 不是内容主事实。
- 点赞清理是提交后副作用，失败不回滚已提交删除。
- 用户处罚和拉黑关系需要同步回源 owner 判断。
- 删除/下线都是软删除和事件扩散，不物理删除主事实行。
- 媒体 OSS prepare / complete / reference 失败会阻断对应媒体或发帖/改帖动作，避免帖子引用不可用对象。

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
- `content.application.PostReadApplicationService`
- `content.application.CommentApplicationService`
- `content.application.CommentReadApplicationService`
- `content.application.BookmarkApplicationService`
- `content.application.SubscriptionApplicationService`
- `content.application.ReportApplicationService`
- `content.application.ModerationApplicationService`
- `content.application.PostModerationApplicationService`
- `content.application.PostScoreRefreshApplicationService`
- `content.domain.service.*`
- `content.infrastructure.event.*`
- `content.contracts.event.*`
