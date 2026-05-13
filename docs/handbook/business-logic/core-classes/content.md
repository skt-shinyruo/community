# Content 核心类细分

本文是 [../content.md](../content.md) 的类级补充。内容域是主写路径的事件源，很多下游只是在消费它的事实。

## 先读顺序

1. `PostPublishingApplicationService`
2. `PostReadApplicationService`
3. `CommentApplicationService`
4. `PostMediaApplicationService`
5. `PostModerationApplicationService`
6. `ModerationApplicationService` / `ReportApplicationService`

## 写路径

| 类 | 核心职责 | 读代码时重点看什么 |
| --- | --- | --- |
| `content.application.PostPublishingApplicationService` | 发帖、改帖、删帖主路径。 | 看 idempotency、user 回源、正文 block 和事件发布顺序。 |
| `content.application.PostMediaApplicationService` | 帖子媒体 upload session、complete 和 asset draft 状态。 | 看 draft asset 如何在发帖时被绑定。 |
| `content.application.CommentApplicationService` | 评论创建、编辑、删除和事件。 | 看 target 解析、用户发言资格和点赞/通知联动。 |
| `content.application.PostModerationApplicationService` | 帖子治理下线和状态变更。 | 看治理动作如何改主事实并驱动事件扩散。 |
| `content.application.ModerationApplicationService` | 举报处理和治理动作编排。 | 看它如何把审核决策落成主事实。 |
| `content.application.ReportApplicationService` | 举报创建和查询。 | 看 report 和 moderation_action 的边界。 |

## 读路径与查询装配

| 类 | 核心职责 |
| --- | --- |
| `content.application.PostReadApplicationService` | 帖子列表、详情、摘要查询。 |
| `content.application.CommentReadApplicationService` | 评论列表和用户最近评论查询。 |
| `content.application.CategoryApplicationService` | 分类列表。 |
| `content.application.TagApplicationService` | 热门标签和标签建议。 |
| `content.application.BookmarkApplicationService` | 收藏关系。 |
| `content.application.SubscriptionApplicationService` | 分类订阅关系。 |
| `content.application.ContentPostPayloadAssembler` | 内容事件 payload 的装配。 |
| `content.application.PostDetailAssembler` / `PostSummaryAssembler` / `RecentUserCommentAssembler` | 视图装配辅助。 |
| `content.application.PostContentBlockTextProjector` | 正文 block 到可搜索/可展示文本的投影。 |

## 事件、投影和副作用

| 类 | 核心职责 |
| --- | --- |
| `content.application.PostContractEventApplicationService` | post domain event 到 contract event 映射。 |
| `content.application.CommentContractEventApplicationService` | comment domain event 到 contract event 映射。 |
| `content.application.SocialInteractionProjectionApplicationService` | 被删内容的社交关系清理协作。 |
| `content.application.ContentEventPublisher` | content contract event 发布端口。 |
| `content.application.ModerationNoticePublisher` | moderation result notice 发布端口。 |
| `content.application.PostWriteSideEffectScheduler` | after-commit 的 post score refresh 调度。 |
| `content.application.UserModerationGuard` | 发帖 / 评论前同步回源 user 处罚状态。 |
| `content.application.PostBusinessEventLogger` | 写路径业务事件日志。 |

## 领域服务

| 类 | 核心职责 |
| --- | --- |
| `content.domain.service.PostPublishingDomainService` | 发帖 draft 和发布规则。 |
| `content.domain.service.PostContentBlockPolicy` | content block type、length、metadata 和 media reference 规则。 |
| `content.domain.service.CommentDomainService` | 评论目标解析、编辑和删除规则。 |
| `content.domain.service.ModerationDecisionDomainService` | 内容治理决策规则。 |
| `content.domain.service.PostModerationDomainService` | 帖子治理状态规则。 |

## 基础设施

| 类 | 核心职责 |
| --- | --- |
| `content.infrastructure.api.ContentEntityQueryService` | owner entity resolve API。 |
| `content.infrastructure.api.PostScanService` | search 投影扫描 API。 |
| `content.infrastructure.text.SensitiveFilter` | 敏感词 trie sanitizer 和 fail-fast 字典加载。 |
| `content.infrastructure.oss.OssPostMediaStorageAdapter` | content 到 OSS 的媒体适配。 |
| `content.infrastructure.event.LocalContentEventPublisher` | content contract event 的本地发布。 |
| `content.infrastructure.event.SpringPostDomainEventPublisher` / `SpringCommentDomainEventPublisher` | Spring 领域事件发布。 |
| `content.infrastructure.event.PostDomainEventBridge` / `CommentDomainEventBridge` | domain event 到 contract event 的桥接。 |
| `content.infrastructure.event.SocialInteractionProjectionListener` | 删除内容后清理 social projection。 |
| `content.infrastructure.persistence.*` | post、comment、block、media、tag、bookmark、subscription、report、moderation 的持久化实现。 |
| `content.infrastructure.persistence.RedisPostScoreQueue` | 帖子热度刷新队列。 |

## 关键语义

- content 是主写路径事件源，search / notice / social cleanup 都是下游。
- 删除和治理是软删除 + 事件扩散，不是物理删主事实。
- 发帖和评论都是 HTTP 幂等写。
- 媒体绑定必须先过 OSS draft / complete / reference 约束。

