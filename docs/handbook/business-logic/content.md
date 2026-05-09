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

事件/内部：

- content domain event bridge 发布 content contract event。
- search outbox enqueuer 订阅帖子事件。
- notice projection listener 订阅 content/social/moderation contract event。
- social interaction projection listener 在内容删除后清理社交关系。

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

帖子写接口不接收旧 `content` 字符串。项目尚未部署，没有历史帖子数据，当前实现不保留旧正文兼容路径。

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
- 发帖和改帖必须校验分类存在。
- 分类是内容组织主事实，不由用户域持有。

标签：

- `TagApplicationService.listHotTags(limit)` 返回热门标签。
- `suggestTags(q, limit)` 按输入建议标签。
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

## 失败和一致性

- 发帖和评论是 HTTP 幂等写。
- 通知投影失败不回滚内容写入。
- 搜索投影通过 outbox 重试，ES 不是内容主事实。
- 点赞清理是提交后副作用，失败不回滚已提交删除。
- 用户处罚和拉黑关系需要同步回源 owner 判断。
- 删除/下线都是软删除和事件扩散，不物理删除主事实行。

## 关键代码

- `content.controller.PostController`
- `content.controller.CategoryController`
- `content.controller.TagController`
- `content.controller.BookmarkController`
- `content.controller.SubscriptionController`
- `content.controller.ReportController`
- `content.controller.ModerationController`
- `content.application.PostPublishingApplicationService`
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
