# 核心逻辑覆盖索引

本文档是代码到 handbook 文档的索引。它不替代业务说明，只回答“某个核心类的当前行为应到哪里读”。业务域详解见 [business-logic/README.md](business-logic/README.md)，类级补充见 [business-logic/core-classes/README.md](business-logic/core-classes/README.md)，业务链路总览见 [business-flows.md](business-flows.md)，架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)。

核心运行时逻辑文档闭环指：生产代码中的核心入口和关键行为，都能从本文档追到对应 handbook 文档；文档必须说明 owner、入口、主路径、状态、幂等、一致性、失败语义和关键代码。前端按收窄口径纳入：只覆盖业务状态、API 编排和路由到页面能力映射，不要求展示型组件、样式或无业务语义 helper 逐个入索引。

覆盖判定以源码扫描驱动，本文档只是结果。先从生产代码抽取候选核心类，再人工分类为 `Covered`、`IndexOnly`、`Excluded` 或待补文档项，避免索引落后于代码。`Covered` 不等于类名出现过，而是读者能从链接文档理解该类参与的当前行为、关键状态、幂等 / 一致性和失败语义；多个类可以共用一个域级或流程段落，只有承担独立规则或非显然补偿逻辑的类才需要类级说明。`Excluded` 不逐条写入本文档正文，排除理由应记录在覆盖审计文档中；本文档只保留需要导航阅读的 `Covered` 和 `IndexOnly` 核心候选。

覆盖状态：

- `Covered`：handbook 已说明入口、主路径和关键失败 / 一致性语义。
- `Partial`：handbook 已说明域级行为，但类级细节主要还要读代码或测试。
- `IndexOnly`：当前只作为薄包装、DTO 转换或适配入口列入索引，不单独展开业务语义。
- `Excluded`：经人工确认不是核心运行时逻辑，例如配置属性、简单类型转换、纯测试辅助或无业务语义的技术 glue。

## Auth

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `auth.controller.AuthController` | `/api/auth/**` HTTP binding | [Auth 认证业务逻辑](business-logic/auth.md) | IndexOnly |
| `auth.application.LoginApplicationService` | 登录、验证码要求、JWT / refresh token 签发 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RefreshTokenApplicationService` | refresh / logout / refresh family reuse 处理 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RegistrationApplicationService` | Verify-First registration start; creates registration draft and code after user-domain preparation | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RegistrationVerificationApplicationService` | resolves registration drafts, resends codes through pending replacement, consumes verification codes, and asks user domain to create the active user | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.PasswordResetApplicationService` | 找回密码 token、邮件、密码更新和 session 撤销 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.CaptchaApplicationService` | 验证码发放和校验 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.LoginRateLimitApplicationService` | 登录失败计数和验证码触发 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.TokenFreshnessApplicationService` | 高风险入口 access token `security_version` 新鲜度校验 | [Token Freshness 与高风险请求安全](core-logic/security-token-freshness.md) | Covered |
| `auth.domain.service.AuthDomainService` | token / credential 基础规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.AuthSecretGenerator` | 256-bit opaque token 和安全随机数字验证码生成 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.CaptchaDomainService` | 验证码规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.LoginRateLimitDomainService` | 登录风控规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.PasswordResetDomainService` | reset token 和重置规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.RefreshTokenDomainService` | refresh token 旋转 / family 规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.RegistrationDomainService` | registration input and Verify-First draft/code rules | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.repository.RegistrationDraftRepository` | opaque `registrationToken` to prepared registration draft store with TTL | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.infrastructure.jwt.JwtTokenService` | HS256 access token 签发和 claim 组装 | [安全模型](security.md#jwt-和-refresh-cookie) | Covered |
| `auth.infrastructure.web.AuthOriginGuardFilter` | `community-app` unsafe HTTP method OriginGuard | [安全模型](security.md#cors-和-originguard) | Covered |
| `auth.infrastructure.web.TokenFreshnessFilter` | 高风险 URI prefix 的 token freshness enforcement | [Token Freshness 与高风险请求安全](core-logic/security-token-freshness.md) | Covered |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | refresh session 清理 job | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |

## User

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `user.controller.UserController` | `/api/users/**` HTTP binding | [User 用户业务逻辑](business-logic/user.md) | IndexOnly |
| `user.controller.AdminUserController` | `/api/admin/users/**` HTTP binding | [User 用户业务逻辑](business-logic/user.md) | IndexOnly |
| `user.application.RefreshTokenSessionApplicationService` | DB refresh token session 存储、begin/finish/rollback rotation、撤销和过期清理 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRegistrationApplicationService` | registration preparation and verified active user creation aggregate | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRegistrationApplicationService#prepareRegistrationUser` | validates and prepares registration material without database writes or events | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRegistrationApplicationService#createVerifiedRegistrationUser` | inserts the active user and publishes user policy existence | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserCredentialApplicationService` | 密码校验、密码策略和密码更新 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserReadApplicationService` | 用户摘要、批量读取、跨域 user 查询 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserProfileApplicationService` | 用户资料聚合、最近内容读取 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserAvatarApplicationService` | 头像 upload session / confirm | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserModerationApplicationService` | 禁言 / 封禁状态和 policy event | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.AdminUserApplicationService` | 管理员用户搜索和角色修改 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRewardApplicationService` | 用户奖励语义 / wallet 奖励协作入口 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserEventDispatchApplicationService` | user owner contract event outbox 到 Kafka dispatch | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `user.domain.service.PasswordPolicyDomainService` | 密码复杂度规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserCredentialDomainService` | 凭证校验和密码更新规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserModerationDomainService` | 用户处罚状态规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserReadDomainService` | 用户读取参数规范化 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserRegistrationDomainService` | user registration fact rules | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserRoleDomainService` | 管理员角色修改规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.event.UserPolicyEventPublisher` | user policy domain event 发布端口 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.infrastructure.event.OutboxUserPolicyEventPublisher` | user policy event 写 `eventbus.user` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `user.infrastructure.event.UserEventKafkaOutboxHandler` | `eventbus.user` outbox handler | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `user.infrastructure.event.UserEventKafkaSenderAdapter` | user owner event 发布到 `user.events` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `user.infrastructure.event.UserRewardKafkaListener` | user reward Kafka projection listener | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## OSS

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `oss.application.ObjectUploadApplicationService` | OSS upload session / complete / metadata | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.application.ObjectQueryApplicationService` | metadata lookup and public file resolve | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.application.ObjectAccessApplicationService` | signed URL issuance | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.application.ObjectReferenceApplicationService` | object reference bind / release | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.application.ObjectPermissionApplicationService` | grant / revoke object access | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.application.ObjectLifecycleApplicationService` | delete pending / purge lifecycle | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectUploadApplicationService` | OSS deployable upload session / complete / metadata | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectQueryApplicationService` | OSS deployable metadata lookup and public file resolve | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectAccessApplicationService` | OSS deployable signed URL issuance | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectReferenceApplicationService` | OSS deployable object reference bind / release | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectPermissionApplicationService` | OSS deployable grant / revoke object access | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.application.ObjectLifecycleApplicationService` | OSS deployable delete pending / purge lifecycle | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss.domain.model.OssUsagePolicy` | usage max size, MIME, TTL, cache and lifecycle policy | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `oss.controller.OssObjectController` | `/api/oss/**` HTTP binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |
| `oss.controller.PublicFileController` | `/files/**` public file binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |
| `oss.controller.InternalOssObjectController` | internal OSS metadata / signed URL HTTP binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |
| `community-oss.controller.OssObjectController` | OSS deployable `/api/oss/**` HTTP binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |
| `community-oss.controller.PublicFileController` | OSS deployable `/files/**` public file binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |
| `community-oss.controller.InternalOssObjectController` | OSS deployable internal metadata / signed URL HTTP binding | [OSS 对象存储业务逻辑](business-logic/oss.md) | IndexOnly |

## Content

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.controller.PostController` | posts and comments HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.PostMediaController` | post media upload HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.CategoryController` | category HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.TagController` | tag HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.BookmarkController` | bookmark HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.SubscriptionController` | category subscription HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.ReportController` | report HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.controller.ModerationController` | moderation HTTP binding | [Content 内容业务逻辑](business-logic/content.md) | IndexOnly |
| `content.application.PostPublishingApplicationService` | 发帖、改帖、删帖写路径 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostReadApplicationService` | 帖子详情、批量摘要和 owner query 查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostMediaApplicationService` | 帖子媒体上传会话、complete 和 OSS asset draft 状态 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentApplicationService` | 评论创建、编辑、删除和事件 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentReadApplicationService` | 评论列表和用户最近评论查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.BookmarkApplicationService` | 收藏关系 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.SubscriptionApplicationService` | 分类订阅关系 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CategoryApplicationService` | 分类列表 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.TagApplicationService` | 热门标签和标签建议 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.ReportApplicationService` | 举报创建和查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.ModerationApplicationService` | 内容治理动作和处罚协作 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostModerationApplicationService` | 帖子治理下线 / 状态变更 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostContractEventApplicationService` | post contract event 映射 / 发布 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentContractEventApplicationService` | comment contract event 映射 / 发布 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.ContentEntityResolutionApplicationService` | POST / COMMENT owner entity resolution | [Content 内容业务逻辑](business-logic/content.md#owner-entity-resolution) | Covered |
| `content.application.ContentEventDispatchApplicationService` | content owner contract event outbox 到 Kafka dispatch | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `content.application.PostHotFeedProjectionApplicationService` | owner event 到帖子 score/cache/hot-feed 投影 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.ContentEventPublisher` | content contract event 发布端口 | [Content 内容业务逻辑](business-logic/content.md#内容事件和投影) | Covered |
| `content.application.ModerationNoticePublisher` | moderation result notice 发布端口 | [Content 内容业务逻辑](business-logic/content.md#举报和审核) | Covered |
| `content.application.UserModerationGuard` | 发帖 / 评论前同步回源 user 处罚状态 | [Content 内容业务逻辑](business-logic/content.md#发帖) | Covered |
| `content.application.ContentTextCodec` | 内容文本边界处理 | [Content 内容业务逻辑](business-logic/content.md#发帖) | Covered |
| `content.domain.service.PostPublishingDomainService` | 发帖 draft 和发布规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.PostContentBlockPolicy` | content block type, length, metadata and media reference rules | [Content 内容业务逻辑](business-logic/content.md#发帖) | Covered |
| `content.domain.service.CommentDomainService` | 评论目标解析、编辑和删除规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.ModerationDecisionDomainService` | 内容治理决策规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.PostModerationDomainService` | 帖子治理状态规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.event.PostDomainEventPublisher` | post domain event 发布端口 | [Content 内容业务逻辑](business-logic/content.md#内容事件和投影) | Covered |
| `content.domain.event.CommentDomainEventPublisher` | comment domain event 发布端口 | [Content 内容业务逻辑](business-logic/content.md#内容事件和投影) | Covered |
| `content.infrastructure.api.ContentEntityQueryApiAdapter` | content owner entity resolve API 实现 | [集成契约](integration-contracts.md#同步-owner-api) | Covered |
| `content.infrastructure.api.PostScanQueryApiAdapter` | search 投影扫描 API 实现 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `content.infrastructure.text.SensitiveFilter` | sensitive word trie sanitizer and fail-fast dictionary loading | [Content 内容业务逻辑](business-logic/content.md#发帖) | Covered |
| `content.infrastructure.event.OutboxContentEventPublisher` | content contract event 写 `eventbus.content` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `content.infrastructure.event.SpringPostDomainEventPublisher` | post domain event Spring 发布实现 | [Content 内容业务逻辑](business-logic/content.md#内容事件和投影) | Covered |
| `content.infrastructure.event.SpringCommentDomainEventPublisher` | comment domain event Spring 发布实现 | [Content 内容业务逻辑](business-logic/content.md#内容事件和投影) | Covered |
| `content.infrastructure.event.PostDomainEventBridge` | post domain event 到 contract event bridge | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.infrastructure.event.CommentDomainEventBridge` | comment domain event 到 contract event bridge | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.infrastructure.event.ContentEventKafkaOutboxHandler` | `eventbus.content` outbox handler | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `content.infrastructure.event.ContentEventKafkaSenderAdapter` | content owner event 发布到 `content.events` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `content.infrastructure.event.PostHotFeedProjectionKafkaListener` | content/social Kafka event 到 hot-feed application | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## Social

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `social.controller.LikeController` | like HTTP binding | [Social 社交业务逻辑](business-logic/social.md) | IndexOnly |
| `social.controller.FollowController` | follow HTTP binding | [Social 社交业务逻辑](business-logic/social.md) | IndexOnly |
| `social.controller.BlockController` | block HTTP binding | [Social 社交业务逻辑](business-logic/social.md) | IndexOnly |
| `social.application.LikeApplicationService` | 点赞关系、内容实体解析、任务 / 奖励协作和事件 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.application.FollowApplicationService` | 关注关系和 follow event | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.application.BlockApplicationService` | 拉黑关系、follow 清理和 owner event | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.application.SocialEventDispatchApplicationService` | social owner contract event outbox 到 Kafka dispatch | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `social.domain.service.LikeDomainService` | 点赞实体和计数规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.service.FollowDomainService` | 关注关系规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.service.BlockDomainService` | 拉黑关系和 follow 清理规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.event.SocialDomainEventPublisher` | social domain event 发布端口 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.infrastructure.event.OutboxSocialDomainEventPublisher` | social domain event 映射后写 `eventbus.social` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `social.infrastructure.event.SocialEventKafkaOutboxHandler` | `eventbus.social` outbox handler | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `social.infrastructure.event.SocialEventKafkaSenderAdapter` | social owner event 发布到 `social.events` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## Notice

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `notice.controller.NoticeController` | notice HTTP binding | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | IndexOnly |
| `notice.application.NoticeApplicationService` | 通知写入、列表、未读数、批量已读 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.application.NoticeProjectionApplicationService` | content / social / moderation event 到通知读模型 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.domain.service.NoticeDomainService` | 通知分页、状态和创建校验 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.domain.service.NoticeProjectionDomainService` | 通知投影规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.infrastructure.event.NoticeProjectionKafkaListener` | content / social Kafka event 到通知可靠投影 listener | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## Search

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `search.controller.SearchController` | search HTTP binding | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | IndexOnly |
| `search.application.SearchApplicationService` | 搜索查询 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.application.SearchPostProjectionApplicationService` | Kafka event 触发后回源 content 并 upsert/delete ES | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.domain.service.PostSearchDomainService` | 搜索 query 规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.domain.service.KeywordHighlightSupport` | 搜索关键词高亮 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.infrastructure.event.SearchPostProjectionKafkaListener` | content Kafka event 到 search projection listener | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## Analytics

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `analytics.controller.AnalyticsController` | analytics HTTP binding | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | IndexOnly |
| `analytics.application.AnalyticsApplicationService` | UV / DAU 查询和区间校验 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `analytics.application.AnalyticsIngestApplicationService` | 请求 / 登录成功采集写入，失败节流日志 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `analytics.domain.service.AnalyticsDomainService` | UV / DAU 查询区间规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `analytics.domain.service.AnalyticsIngestDomainService` | UV / DAU 是否记录规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `analytics.infrastructure.web.AnalyticsRequestCaptureFilter` | 请求完成后的 analytics 采集过滤器 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `analytics.infrastructure.web.AnalyticsRequestClassifier` | include / exclude / status / method 采集判定 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |

## Growth

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `growth.application.TaskProgressApplicationService` | 任务模板匹配、事件去重、进度推进、自动发奖 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.application.UserLevelApplicationService` | 等级规则配置和等级计算 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.domain.service.TaskPeriodKeyResolver` | 任务 periodKey 解析 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.domain.service.TaskProgressDomainService` | 任务进度推进和达成规则 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.domain.service.RewardGrantDomainService` | 奖励发放幂等规则 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.domain.service.UserLevelDomainService` | 等级计算和配置规则 | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.application.GrowthBusinessTimeService` | growth daily/weekly/monthly business time source | [Growth 成长业务逻辑](business-logic/growth.md) | Covered |
| `growth.infrastructure.event.TaskProgressEventBackboneKafkaListener` | content / social Kafka event 到 growth task listener | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |

## Market

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `market.controller.MarketController` | market buyer/seller HTTP binding | [Market 市场业务逻辑](business-logic/market.md) | IndexOnly |
| `market.controller.AdminMarketController` | market admin HTTP binding | [Market 市场业务逻辑](business-logic/market.md) | IndexOnly |
| `market.application.MarketQueryApplicationService` | listing / order 查询、订单详情交付内容装配 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketListingApplicationService` | listing 创建、更新、暂停、恢复、关闭 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketInventoryApplicationService` | 预加载虚拟库存追加、查询、失效和 listing 库存联动 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketAddressApplicationService` | 收货地址簿 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketOrderApplicationService` | 下单、取消、交付、发货、确认 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketDisputeApplicationService` | 买家争议、卖家处理、管理员裁决 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketWalletActionApplicationService` | escrow / release / refund durable command 写入 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketWalletActionProcessorApplicationService` | due action claim、调用 wallet、推进 saga | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketWalletActionRecoveryApplicationService` | lease 恢复、缺失 command 补写、已有 wallet 结果应用 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketOrderSagaApplicationService` | wallet action 后的订单 / 争议条件状态推进 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketOrderAutoConfirmApplicationService` | 自动确认批任务入口 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketOrderAutoConfirmSingleOrderApplicationService` | 单订单锁定和自动确认 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.domain.service.MarketListingDomainService` | listing 发布和库存规则 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.domain.service.MarketOrderDomainService` | 订单状态、购买数量和金额规则 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.domain.service.MarketDisputeDomainService` | 争议发起和裁决规则 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.domain.service.MarketWalletActionDomainService` | market wallet action requestId 和终态规则 | [Market 市场业务逻辑](business-logic/market.md) | Covered |

## Wallet

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `wallet.controller.WalletController` | wallet HTTP binding | [Wallet 钱包业务逻辑](business-logic/wallet.md) | IndexOnly |
| `wallet.controller.AdminWalletController` | wallet admin HTTP binding | [Wallet 钱包业务逻辑](business-logic/wallet.md) | IndexOnly |
| `wallet.application.WalletAccountApplicationService` | 钱包账户创建、余额、状态、version 条件更新 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletLedgerApplicationService` | 总账交易、双分录、requestId replay 校验 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletRechargeApplicationService` | 充值订单和 RECHARGE 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletWithdrawApplicationService` | 提现订单、两段 WITHDRAW 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletTransferApplicationService` | 转账订单和 TRANSFER 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletMarketApplicationService` | market escrow / release / refund owner action | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletRewardApplicationService` | growth / reward 入账 owner action | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletAdminOpsApplicationService` | freeze / reverse 管理操作和审计 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletAccountDomainService` | 账户类型、冻结状态和分录方向规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletLedgerDomainService` | 双分录平衡、金额上限和交易创建规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletOrderDomainService` | 充值 / 提现 / 转账订单金额和转账规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletAdminDomainService` | 管理员钱包操作 actor / reason 规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletAmountPolicy` | 单次资金动作金额上限 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |

## Drive

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `drive.controller.DriveController` | authenticated drive HTTP binding | [网盘业务逻辑](business-logic/drive.md) | IndexOnly |
| `drive.controller.DrivePublicShareController` | public drive share HTTP binding | [网盘业务逻辑](business-logic/drive.md) | IndexOnly |
| `drive.application.DriveSpaceApplicationService` | 网盘空间 lazy create、quota / used / remaining 查询 | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.application.DriveEntryApplicationService` | 文件夹、列表、搜索、重命名、移动和私有下载 URL | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.application.DriveUploadApplicationService` | 上传会话、OSS prepare/complete、quota reserve 和 entry 创建 | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.application.DriveTrashApplicationService` | 回收站、恢复、彻底删除、quota 释放和 OSS 删除重试 | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.application.DriveShareApplicationService` | 分享创建、撤销、提取码校验、ticket 和分享下载 URL | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.application.port.DriveShareTicketCodec` | share download ticket 编解码端口 | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.domain.service.DriveEntryDomainService` | 文件名规范化和禁止移动到自身 / 子孙目录 | [网盘业务逻辑](business-logic/drive.md) | Covered |
| `drive.infrastructure.job.DriveUploadRecoveryJob` | stale upload finalization / failed compensation recovery | [网盘业务逻辑](business-logic/drive.md#详细链路) | Covered |
| `drive.infrastructure.security.HmacDriveShareTicketCodec` | HMAC share download ticket 实现 | [网盘业务逻辑](business-logic/drive.md) | Covered |

## IM Policy Snapshot In Community App

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `im.application.ImPolicySnapshotApplicationService` | 给 im-realtime 拉取 user policy / block relation snapshot | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.application.ImPolicyProjectionApplicationService` | 校验 owner event 并写 projection outbox port | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.application.ImPolicyProjectionOutboxPort` | application-owned IM policy projection outbox port | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.application.ImPolicyEventDispatchApplicationService` | `projection.im.policy` outbox 到 IM policy Kafka event dispatch | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.controller.ImPolicySnapshotController` | internal IM policy snapshot HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.infrastructure.event.JdbcImPolicyProjectionOutboxAdapter` | 确定性 source event ID 写 `projection.im.policy` | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.infrastructure.event.ImPolicyKafkaOutboxHandler` | `projection.im.policy` outbox handler | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.infrastructure.event.ImPolicyBackboneKafkaListener` | user/social Kafka event 到 IM policy projection application | [异步事件骨干](core-logic/async-event-backbone.md) | Covered |
| `im.infrastructure.event.ImPolicyEventKafkaSenderAdapter` | IM policy delta 发布到 IM Kafka topics | [集成契约](integration-contracts.md#im-kafka-contract) | Covered |

## Runtime Config And Observability

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `runtime.controller.RuntimeConfigController` | public `/api/runtime-config` HTTP binding | [Runtime Configuration](core-logic/runtime-configuration.md) | IndexOnly |
| `runtime.application.RuntimeConfigApplicationService` | frontend runtime config snapshot assembly | [Runtime Configuration](core-logic/runtime-configuration.md) | Covered |
| `common-observability.RuntimeApplicationLifecycleListener` | app startup / ready / shutdown lifecycle runtime events | [Runtime Observability](core-logic/runtime-observability.md) | Covered |
| `common-observability.RuntimeSnapshotScheduler` | periodic runtime resource snapshot scheduler | [Runtime Observability](core-logic/runtime-observability.md) | Covered |
| `common-observability.ServletAccessRuntimeLogFilter` | servlet slow HTTP access runtime log filter | [Runtime Observability](core-logic/runtime-observability.md) | Covered |
| `common-observability.RuntimeKafkaProducerListener` | Kafka producer error runtime hook | [Runtime Observability](core-logic/runtime-observability.md) | Covered |
| `common-observability.RuntimeKafkaRebalanceListener` | Kafka consumer rebalance runtime hook | [Runtime Observability](core-logic/runtime-observability.md) | Covered |

## Gateway And IM Gateway

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `community-gateway.config.GatewayConfigRefreshListener` | route / canary / IM edge config refresh to route rebuild | [Gateway Runtime](core-logic/gateway-runtime.md) | Covered |
| `community-gateway.canary.CanaryInstanceFilter` | canary metadata matching and `draining=true` exclusion | [Gateway Runtime](core-logic/gateway-runtime.md) | Covered |
| `community-gateway.edge.RateLimitWebFilter` | gateway edge rate limit by principal or IP | [安全模型](security.md) | Covered |
| `community-gateway.edge.AccessLogWebFilter` | gateway HTTP access log after trace id resolution | [安全模型](security.md) | Covered |
| `im.gateway.session.ImSessionApiController` | `/api/im/sessions` HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.gateway.session.ImSessionService` | JWT validation, worker selection and session ticket issuance | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.session.PublicWsUrlFactory` | configured absolute public `wsUrl` validation | [IM 消息业务逻辑](business-logic/im.md#session-bootstrap) | Covered |
| `im.gateway.session.SessionTicketCodec` | IM gateway ticket encode / decode | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.shard.RendezvousWorkerSelector` | stable worker selection by user/session key | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.shard.WorkerRegistry` | configured healthy realtime worker registry | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.ws.ConnectTicketRouter` | route first connect ticket to realtime worker | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.ws.ExternalImEdgeWebSocketHandler` | stable external `/ws/im` bridge to selected realtime worker | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.gateway.ws.InternalWorkerBridge` | IM gateway bridge port from external session to selected realtime worker | [IM 消息业务逻辑](business-logic/im.md#websocket-连接) | IndexOnly |
| `im.gateway.ws.InternalWorkerBridgeFactory` | text-frame-only worker bridge and traceparent propagation | [IM 消息业务逻辑](business-logic/im.md#websocket-连接) | Covered |
| `im.gateway.ws.ImGatewayFrameCodec` | gateway connect frame parsing / encoding | [IM 消息业务逻辑](business-logic/im.md) | Covered |

## IM Realtime

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `im.common.ImContractVersions` | 严格支持数值型 IM schema version `1` | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.common.ImSchemaVersionDeserializer` | 拒绝缺失/null/非整数/非 `1` schema version | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.ws.ImWebSocketHandler` | worker WebSocket auth, frame handling and connection lifecycle | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.ws.ImFrameCodec` | realtime frame JSON codec | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.presence.ConnectionRegistry` | online connection registry by session/user | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.service.MessageCommandIngressService` | validate inbound send commands and publish Kafka command | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.kafka.CommandProducer` | Kafka producer for IM command topics | [集成契约](integration-contracts.md#im-kafka-contract) | Covered |
| `im.realtime.kafka.EventConsumers` | consume persisted/rejected/member/policy IM events and update push/projection state | [集成契约](integration-contracts.md#im-kafka-contract) | Covered |
| `im.realtime.projection.ProjectionSyncCoordinator` | bootstrap membership/policy snapshots and gate connect/send readiness | [IM 消息业务逻辑](business-logic/im.md#projection) | Covered |
| `im.realtime.projection.PolicyProjectionService` | local user policy / block projection for private-message decisions | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.projection.MembershipProjectionService` | local room membership projection | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.projection.PolicySnapshotClient` | internal community-app policy snapshot client | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.projection.MembershipSnapshotClient` | internal im-core membership snapshot client | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.presence.RoomLocalPresenceService` | local room presence activation / refresh / release | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.presence.RoomLocalIndex` | in-process room-to-connection index for room fanout | [IM 消息业务逻辑](business-logic/im.md#projection) | Covered |
| `im.realtime.presence.RedisRoomPresenceDirectory` | distributed room-to-worker presence | [IM 消息业务逻辑](business-logic/im.md#projection) | Covered |
| `im.realtime.fanout.RoomPersistedOwnerConsumer` | shared owner consumer for room persisted events | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.RoomFanoutOwnerService` | route planning and Kafka target dispatch | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.RoomFanoutRoutingService` | Redis presence 到 worker route | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.KafkaRoomFanoutDispatcher` | target command 到固定 inbox partition | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.RealtimeWorkerDirectory` | worker ID / inbox slot discovery validation | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.RoomFanoutTargetConsumer` | consume local worker inbox partition | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.realtime.fanout.RoomFanoutTargetService` | target validation and state-idempotent local fanout | [IM 消息业务逻辑](business-logic/im.md#projection) | Covered |
| `im.realtime.session.SessionTicketCodec` | realtime ticket validation | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.push.PrivatePushService` | online private message fanout | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.push.SendResultPushService` | accepted / committed / rejected send result push | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.push.RoomFanoutCoalescer` | coalesced room update fanout | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.realtime.push.RoomUpdateCoalescer` | state-only room update coalescing | [IM 消息业务逻辑](business-logic/im.md) | Covered |

## IM Core

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `im.core.controller.ConversationController` | private conversation HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.core.controller.RoomController` | room HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.core.controller.UnreadController` | unread HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.core.controller.InternalRealtimeProjectionController` | internal membership snapshot HTTP binding | [IM 消息业务逻辑](business-logic/im.md) | IndexOnly |
| `im.core.application.ConversationApplicationService` | private conversation query / delete use cases | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.application.PrivateMessageApplicationService` | private message persist, owner policy, idempotency, outbox events | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.application.RoomApplicationService` | room creation, join, leave and membership events | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.application.RoomMessageApplicationService` | room message persist, membership authority, idempotency, outbox events | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.application.UnreadApplicationService` | private / room unread summary and watermarks | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.domain.service.PrivateMessageDomainService` | private message draft, conversation id and seq rules | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.domain.service.RoomMessageDomainService` | room message draft, membership and seq rules | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.domain.service.RoomMembershipDomainService` | room membership rule model | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.domain.service.UnreadDomainService` | unread query limit normalization | [IM Core Runtime](core-logic/im-core-runtime.md) | IndexOnly |
| `im.core.kafka.CommandConsumers` | consume IM command topics and publish persisted/rejected events | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.core.kafka.KafkaRoomMemberChangePublisher` | publish room membership changes via outbox | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.core.domain.event.RoomMemberChangePublisher` | room membership event publisher port | [IM Core Runtime](core-logic/im-core-runtime.md) | Covered |
| `im.core.infrastructure.event.NoopRoomMemberChangePublisher` | no-op membership publisher when Kafka/outbox disabled | [IM Core Runtime](core-logic/im-core-runtime.md) | IndexOnly |
| `im.core.outbox.ImMessageOutboxEnqueuer` | enqueue IM persisted/rejected/member events | [IM 消息业务逻辑](business-logic/im.md) | Covered |
| `im.core.outbox.ImKafkaOutboxHandler` | dispatch IM outbox rows to Kafka topics | [集成契约](integration-contracts.md#im-kafka-contract) | Covered |

## Shared Infrastructure And Clients

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `community-oss-client.CommunityOssClient` | typed OSS client contract for business services | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `community-oss-client.HttpCommunityOssClient` | HTTP implementation of OSS client contract | [OSS 对象存储业务逻辑](business-logic/oss.md) | Covered |
| `common-idempotency.IdempotencyGuard` | HTTP write idempotency guard | [可靠性机制](reliability.md#http-idempotency-key) | Covered |
| `common-outbox.OutboxWorkerScheduler` | outbox local scheduler | [可靠性机制](reliability.md#db-outbox) | Covered |
| `common-outbox.OutboxHandler` | outbox topic handler contract | [可靠性机制](reliability.md#db-outbox) | Covered |
| `common-core.event.BestEffortLocalEventListener` | best-effort local event listener marker | [可靠性机制](reliability.md#fail-open-fail-closed-选择) | Covered |
| `common-core.id.BinaryUuidCodec` | binary UUID conversion helper for persistence | [数据与存储](data-and-storage.md) | IndexOnly |
| `community-app.infra.persistence.mybatis.UuidBinaryTypeHandler` | MyBatis UUID binary adapter for `community` schema | [数据与存储](data-and-storage.md#mysql) | IndexOnly |
| `community-oss.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` | MyBatis UUID binary adapter for `community_oss` schema | [数据与存储](data-and-storage.md#mysql) | IndexOnly |
| `common-core.trace.TraceIdCodec` | trace id normalization helper | [安全模型](security.md) | Covered |
| `common-web.TraceIdFilter` | servlet trace id filter | [安全模型](security.md) | Covered |
| `common-web.AuditLogFilter` | servlet audit logging filter | [安全模型](security.md) | Covered |
| `common-web.GlobalExceptionHandler` | servlet error response mapping | [安全模型](security.md) | Covered |
| `common-web.SecurityExceptionHandler` | servlet security error response mapping | [安全模型](security.md) | Covered |
| `common-webflux.TraceIdWebFilter` | WebFlux trace id filter | [安全模型](security.md) | Covered |
| `common-webflux.GlobalExceptionHandler` | WebFlux error response mapping | [安全模型](security.md) | Covered |
| `common-webflux.SecurityExceptionHandler` | WebFlux security error response mapping | [安全模型](security.md) | Covered |

## Scheduler / Job Entries

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.infrastructure.job.HotPathPrewarmJob` | hot-feed / summary 缓存本地预热 | [运维与排障](operations.md#hot-cache-governance-runbook) | Covered |
| `content.infrastructure.job.PostCounterSnapshotFlushJob` | 帖子 counter snapshot 批量落库 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `market.infrastructure.job.MarketOrderAutoConfirmHandler` | XXL `marketOrderAutoConfirm` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.infrastructure.job.MarketWalletActionProcessorHandler` | XXL `marketWalletActionProcessor` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.infrastructure.job.MarketWalletActionRecoveryHandler` | XXL `marketWalletActionRecovery` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | 本地 refresh token cleanup | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `infra.scheduler.SingleFlightTaskGuard` | local single-flight scheduler guard | [可靠性机制](reliability.md#single-flight) | Covered |
| `common-outbox.OutboxWorkerScheduler` | outbox 本地 worker scheduler | [可靠性机制](reliability.md) | Covered |

## Frontend Business State And API Orchestration

| Core file | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `frontend.api.services.authService` | auth / registration / refresh / password reset API orchestration | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.userService` | user profile cache / inflight coalescing and level normalization | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.socialService` | like / follow cache and inflight coalescing | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.postService` | post / comment payload normalization and hydration-facing fields | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.postMediaService` | post media upload session orchestration | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.driveService` | drive upload / share / ticket API orchestration | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.marketService` | market listing / order / dispute / address API orchestration | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.walletService` | wallet money / admin action API orchestration | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.imCoreChatService` | IM history / read HTTP catch-up API surface | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.api.services.adminUserService` | admin user endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.moderationService` | moderation endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.noticeService` | notice endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.reportService` | report endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.blockService` | block endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.taxonomyService` | category / tag endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.subscriptionService` | subscription endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.bookmarkService` | bookmark endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.searchService` | search endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.api.services.analyticsService` | analytics endpoint mapping | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | IndexOnly |
| `frontend.views.registerFlowState` | registration pending token / code state and reset semantics | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.conversationDetailState` | canonical conversation id, message identity, seq merge and catch-up cursor | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.marketState` | order / dispute / fund lifecycle projection | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.driveState` | quota, entry capabilities and share-form validation | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.walletState` | wallet status and transaction projection | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.postsViewState` | composer tag rules and hydration id collection | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |
| `frontend.views.postDetailState` | comment / reply hydration and social state assembly | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) | Covered |

## Maintenance Rule

When adding or changing core backend or frontend runtime behavior, update this index in the same change as the relevant handbook section:

1. Add the new `ApplicationService`, domain service, listener, handler, enqueuer, job, gateway filter, WebSocket handler, IM service, shared guard/scheduler, typed client, or other core runtime entry.
2. Link it to the exact handbook section that describes current behavior.
3. `Partial` 只能短期使用；优先补充 [business-logic/README.md](business-logic/README.md) 下对应域文档，直到该行可以标为 `Covered`。
4. Do not point current behavior only at `docs/superpowers/specs` or `docs/superpowers/plans`; those are design and migration history unless explicitly restated in handbook.
