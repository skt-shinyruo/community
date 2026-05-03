# 核心逻辑覆盖索引

本文档是代码到 handbook 文档的索引。它不替代业务说明，只回答“某个核心类的当前行为应到哪里读”。业务链路见 [business-flows.md](business-flows.md)，架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)。

覆盖状态：

- `Covered`：handbook 已说明入口、主路径和关键失败 / 一致性语义。
- `Partial`：handbook 已说明域级行为，但类级细节主要还要读代码或测试。
- `IndexOnly`：当前只作为薄包装、DTO 转换或适配入口列入索引，不单独展开业务语义。

## Auth

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `auth.application.AuthApplicationService` | me / session 聚合入口 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.LoginApplicationService` | 登录、验证码要求、JWT / refresh token 签发 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.RefreshTokenApplicationService` | refresh / logout / refresh family reuse 处理 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.RegistrationApplicationService` | 注册待激活用户和验证码发送 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.RegistrationVerificationApplicationService` | 注册验证码验证和激活 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.PasswordResetApplicationService` | 找回密码 token、邮件、密码更新和 session 撤销 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.application.CaptchaApplicationService` | 验证码发放和校验 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.application.LoginRateLimitApplicationService` | 登录失败计数和验证码触发 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.domain.service.AuthDomainService` | token / credential 基础规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.domain.service.CaptchaDomainService` | 验证码规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.domain.service.LoginRateLimitDomainService` | 登录风控规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.domain.service.PasswordResetDomainService` | reset token 和重置规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.domain.service.RefreshTokenDomainService` | refresh token 旋转 / family 规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.domain.service.RegistrationDomainService` | 注册输入和待激活用户规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Partial |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | refresh session 清理 job | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `auth.infrastructure.job.PendingRegistrationUserCleanupJob` | 待激活用户清理 job | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |

## User

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `user.application.RefreshTokenSessionApplicationService` | DB refresh token session 存储、消费、撤销和过期清理 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.application.UserRegistrationApplicationService` | 用户注册事实和待激活用户 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.application.UserCredentialApplicationService` | 密码校验、密码策略和密码更新 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.application.UserReadApplicationService` | 用户摘要、批量读取、跨域 user 查询 | [User Profile And Avatar](business-flows.md#user-profile-and-avatar) | Covered |
| `user.application.UserProfileApplicationService` | 用户资料聚合、最近内容读取 | [User Profile And Avatar](business-flows.md#user-profile-and-avatar) | Covered |
| `user.application.UserAvatarApplicationService` | 头像上传 token / confirm | [User Profile And Avatar](business-flows.md#user-profile-and-avatar) | Covered |
| `user.application.UserFileApplicationService` | `/files/**` 文件读取 | [User Profile And Avatar](business-flows.md#user-profile-and-avatar) | Covered |
| `user.application.UserModerationApplicationService` | 禁言 / 封禁状态和 policy event | [User Moderation State](business-flows.md#user-moderation-state) | Covered |
| `user.application.AdminUserApplicationService` | 管理员用户搜索和角色修改 | [Admin User Role Management](business-flows.md#admin-user-role-management) | Covered |
| `user.application.UserPointsApplicationService` | 用户积分视图 / wallet 协作兼容面 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Partial |
| `user.domain.service.PasswordPolicyDomainService` | 密码复杂度规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.domain.service.UserCredentialDomainService` | 凭证校验和密码更新规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.domain.service.UserModerationDomainService` | 用户处罚状态规则 | [User Moderation State](business-flows.md#user-moderation-state) | Covered |
| `user.domain.service.UserReadDomainService` | 用户读取参数规范化 | [User Profile And Avatar](business-flows.md#user-profile-and-avatar) | Partial |
| `user.domain.service.UserRegistrationDomainService` | 用户注册事实规则 | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `user.domain.service.UserRoleDomainService` | 管理员角色修改规则 | [Admin User Role Management](business-flows.md#admin-user-role-management) | Covered |
| `user.infrastructure.event.LocalUserEventPublisher` | user 本地事件发布 | [User Moderation State](business-flows.md#user-moderation-state) | Partial |
| `user.infrastructure.event.LocalUserPolicyEventPublisher` | user policy 事件发布 | [User Moderation State](business-flows.md#user-moderation-state) | Covered |

## Content

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.application.PostPublishingApplicationService` | 发帖、改帖、删帖写路径 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.PostReadApplicationService` | 帖子列表、详情、摘要查询 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.CommentApplicationService` | 评论创建、编辑、删除和事件 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.CommentReadApplicationService` | 评论列表和用户最近评论查询 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.BookmarkApplicationService` | 收藏关系 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.SubscriptionApplicationService` | 分类订阅关系 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.CategoryApplicationService` | 分类列表 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Partial |
| `content.application.TagApplicationService` | 热门标签和标签建议 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Partial |
| `content.application.ReportApplicationService` | 举报创建和查询 | [Report And Moderation](business-flows.md#report-and-moderation) | Covered |
| `content.application.ModerationApplicationService` | 内容治理动作和处罚协作 | [Report And Moderation](business-flows.md#report-and-moderation) | Covered |
| `content.application.PostModerationApplicationService` | 帖子治理下线 / 状态变更 | [Report And Moderation](business-flows.md#report-and-moderation) | Covered |
| `content.application.PostScoreRefreshApplicationService` | 帖子热度刷新调度入口 | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `content.application.PostScoreUpdateApplicationService` | 单帖分数更新 | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Partial |
| `content.application.PostContractEventApplicationService` | post contract event 映射 / 发布 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.CommentContractEventApplicationService` | comment contract event 映射 / 发布 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.application.SocialInteractionProjectionApplicationService` | 被删内容的社交关系清理协作 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Partial |
| `content.domain.service.PostPublishingDomainService` | 发帖 draft 和发布规则 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.domain.service.CommentDomainService` | 评论目标解析、编辑和删除规则 | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.domain.service.ModerationDecisionDomainService` | 内容治理决策规则 | [Report And Moderation](business-flows.md#report-and-moderation) | Covered |
| `content.domain.service.PostModerationDomainService` | 帖子治理状态规则 | [Report And Moderation](business-flows.md#report-and-moderation) | Covered |
| `content.infrastructure.event.PostDomainEventBridge` | post domain event 到 contract event bridge | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.infrastructure.event.CommentDomainEventBridge` | comment domain event 到 contract event bridge | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Covered |
| `content.infrastructure.event.SocialInteractionProjectionListener` | 内容删除后清理 social projection | [Content Post Comment Bookmark And Subscription](business-flows.md#content-post-comment-bookmark-and-subscription) | Partial |

## Social

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `social.application.LikeApplicationService` | 点赞关系、内容实体解析、任务 / 积分协作和事件 | [Social Like Follow And Events](business-flows.md#social-like-follow-and-events) | Covered |
| `social.application.FollowApplicationService` | 关注关系和 follow event | [Social Like Follow And Events](business-flows.md#social-like-follow-and-events) | Covered |
| `social.application.BlockApplicationService` | 拉黑关系、follow 清理、IM policy outbox | [Social Block And IM Governance](business-flows.md#social-block-and-im-governance) | Covered |
| `social.domain.service.LikeDomainService` | 点赞实体和计数规则 | [Social Like Follow And Events](business-flows.md#social-like-follow-and-events) | Covered |
| `social.domain.service.FollowDomainService` | 关注关系规则 | [Social Like Follow And Events](business-flows.md#social-like-follow-and-events) | Covered |
| `social.domain.service.BlockDomainService` | 拉黑关系和 follow 清理规则 | [Social Block And IM Governance](business-flows.md#social-block-and-im-governance) | Covered |
| `social.infrastructure.event.LocalSocialDomainEventPublisher` | social domain event 到 contract event 映射 | [Social Like Follow And Events](business-flows.md#social-like-follow-and-events) | Covered |

## Notice

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `notice.application.NoticeApplicationService` | 通知写入、列表、未读数、批量已读 | [Notice Projection And Read Model](business-flows.md#notice-projection-and-read-model) | Covered |
| `notice.application.NoticeProjectionApplicationService` | content / social / moderation event 到通知读模型 | [Notice Projection And Read Model](business-flows.md#notice-projection-and-read-model) | Covered |
| `notice.domain.service.NoticeDomainService` | 通知分页、状态和创建校验 | [Notice Projection And Read Model](business-flows.md#notice-projection-and-read-model) | Covered |
| `notice.domain.service.NoticeProjectionDomainService` | 通知投影规则 | [Notice Projection And Read Model](business-flows.md#notice-projection-and-read-model) | Covered |
| `notice.infrastructure.event.NoticeProjectionListener` | 本地 after-commit best-effort 通知投影 listener | [Notice Projection And Read Model](business-flows.md#notice-projection-and-read-model) | Covered |

## Search And Ops

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `search.application.SearchApplicationService` | 搜索查询 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.application.SearchPostProjectionApplicationService` | outbox 触发后回源 content 并 upsert/delete ES | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.application.SearchReindexApplicationService` | reindex single-flight、mapping、alias 切换 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.application.ReindexJobApplicationService` | XXL reindex job 到 search owner action | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `search.application.SearchAdminApplicationService` | admin / ops search 管理入口 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Partial |
| `ops.application.OpsApplicationService` | `/api/ops/**` 运维入口，当前转发 search reindex owner action | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `search.domain.service.PostSearchDomainService` | 搜索 query 规则 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.domain.service.SearchReindexDomainService` | reindex 命名和执行规则 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.domain.service.KeywordHighlightSupport` | 搜索关键词高亮 | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Partial |
| `search.infrastructure.event.PostOutboxEnqueuer` | content event 到 search outbox | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `search.infrastructure.event.PostOutboxHandler` | search outbox handler，回源 content 后 upsert/delete ES | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |

## Analytics

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `analytics.application.AnalyticsApplicationService` | UV / DAU 查询和区间校验 | [Analytics](business-flows.md#analytics) | Covered |
| `analytics.application.AnalyticsIngestApplicationService` | 请求 / 登录成功采集写入，失败节流日志 | [Analytics](business-flows.md#analytics) | Covered |
| `analytics.domain.service.AnalyticsDomainService` | UV / DAU 查询区间规则 | [Analytics](business-flows.md#analytics) | Covered |
| `analytics.domain.service.AnalyticsIngestDomainService` | UV / DAU 是否记录规则 | [Analytics](business-flows.md#analytics) | Covered |
| `analytics.infrastructure.web.AnalyticsRequestCaptureFilter` | 请求完成后的 analytics 采集过滤器 | [Analytics](business-flows.md#analytics) | Covered |
| `analytics.infrastructure.web.AnalyticsRequestClassifier` | include / exclude / status / method 采集判定 | [Analytics](business-flows.md#analytics) | Covered |

## Growth

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `growth.application.TaskProgressApplicationService` | 任务模板匹配、事件去重、进度推进、自动发奖 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |
| `growth.application.UserLevelApplicationService` | 等级规则配置和等级计算 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |
| `growth.domain.service.TaskPeriodKeyResolver` | 任务 periodKey 解析 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |
| `growth.domain.service.TaskProgressDomainService` | 任务进度推进和达成规则 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |
| `growth.domain.service.RewardGrantDomainService` | 奖励发放幂等规则 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |
| `growth.domain.service.UserLevelDomainService` | 等级计算和配置规则 | [Growth Task Reward Level And Retired Check In Surface](business-flows.md#growth-task-reward-level-and-retired-check-in-surface) | Covered |

## Market

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `market.application.MarketApplicationService` | market controller 聚合入口 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | IndexOnly |
| `market.application.MarketQueryApplicationService` | listing / order 查询、订单详情交付内容装配 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketListingApplicationService` | listing 创建、更新、暂停、恢复、关闭 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketInventoryApplicationService` | 预加载虚拟库存追加、查询、失效和 listing 库存联动 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketAddressApplicationService` | 收货地址簿 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Partial |
| `market.application.MarketOrderApplicationService` | 下单、取消、交付、发货、确认 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketDisputeApplicationService` | 买家争议、卖家处理、管理员裁决 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.AdminMarketApplicationService` | admin dispute 聚合入口 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | IndexOnly |
| `market.application.MarketWalletActionApplicationService` | escrow / release / refund durable command 写入 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketWalletActionProcessorApplicationService` | due action claim、调用 wallet、推进 saga | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketWalletActionRecoveryApplicationService` | lease 恢复、缺失 command 补写、已有 wallet 结果应用 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketOrderSagaApplicationService` | wallet action 后的订单 / 争议条件状态推进 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketOrderAutoConfirmApplicationService` | 自动确认批任务入口 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.application.MarketOrderAutoConfirmSingleOrderApplicationService` | 单订单锁定和自动确认 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.domain.service.MarketListingDomainService` | listing 发布和库存规则 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.domain.service.MarketOrderDomainService` | 订单状态、购买数量和金额规则 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.domain.service.MarketDisputeDomainService` | 争议发起和裁决规则 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.domain.service.MarketWalletActionDomainService` | market wallet action requestId 和终态规则 | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |

## Wallet

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `wallet.application.WalletApplicationService` | wallet controller 聚合入口和 HTTP 幂等包装 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletAccountApplicationService` | 钱包账户创建、余额、状态、version 条件更新 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletLedgerApplicationService` | 总账交易、双分录、requestId replay 校验 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletRechargeApplicationService` | 充值订单和 RECHARGE 总账 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletWithdrawApplicationService` | 提现订单、两段 WITHDRAW 总账 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletTransferApplicationService` | 转账订单和 TRANSFER 总账 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletMarketApplicationService` | market escrow / release / refund owner action | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletRewardApplicationService` | growth / reward 入账 owner action | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.WalletAdminOpsApplicationService` | freeze / reverse 管理操作和审计 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.application.AdminWalletApplicationService` | admin wallet controller 聚合入口 | [Wallet Ledger](business-flows.md#wallet-ledger) | IndexOnly |
| `wallet.domain.service.WalletAccountDomainService` | 账户类型、冻结状态和分录方向规则 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.domain.service.WalletLedgerDomainService` | 双分录平衡、金额上限和交易创建规则 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.domain.service.WalletOrderDomainService` | 充值 / 提现 / 转账订单金额和转账规则 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.domain.service.WalletAdminDomainService` | 管理员钱包操作 actor / reason 规则 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |
| `wallet.domain.service.WalletAmountPolicy` | 单次资金动作金额上限 | [Wallet Ledger](business-flows.md#wallet-ledger) | Covered |

## IM Policy Snapshot In Community App

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `im.application.ImPolicySnapshotApplicationService` | 给 im-realtime 拉取 user policy / block relation snapshot | [Social Block And IM Governance](business-flows.md#social-block-and-im-governance) | Covered |

## Scheduler / Job Entries

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.infrastructure.job.PostScoreRefresher` | 帖子热度刷新本地 job | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `search.infrastructure.job.SearchReindexHandler` | XXL `searchReindex` | [Search Projection And Reindex](business-flows.md#search-projection-and-reindex) | Covered |
| `market.infrastructure.job.MarketOrderAutoConfirmHandler` | XXL `marketOrderAutoConfirm` | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.infrastructure.job.MarketWalletActionProcessorHandler` | XXL `marketWalletActionProcessor` | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `market.infrastructure.job.MarketWalletActionRecoveryHandler` | XXL `marketWalletActionRecovery` | [Market Order And Dispute](business-flows.md#market-order-and-dispute) | Covered |
| `user.infrastructure.job.PendingRegistrationUserCleanupHandler` | 待激活用户清理 handler | [Auth Registration Login And Session](business-flows.md#auth-registration-login-and-session) | Covered |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | 本地 refresh token cleanup | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `auth.infrastructure.job.PendingRegistrationUserCleanupJob` | 本地 pending registration cleanup | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |
| `common-outbox.OutboxWorkerScheduler` | outbox 本地 worker scheduler | [Ops Scheduler And Compensation](business-flows.md#ops-scheduler-and-compensation) | Covered |

## Maintenance Rule

When adding or changing core backend behavior, update this index in the same change as the relevant handbook section:

1. Add the new `ApplicationService`, domain service, listener, handler, enqueuer or job.
2. Link it to the exact handbook section that describes current behavior.
3. Use `Partial` only temporarily; prefer improving [business-flows.md](business-flows.md) until the row can be marked `Covered`.
4. Do not point current behavior only at `docs/superpowers/specs` or `docs/superpowers/plans`; those are design and migration history unless explicitly restated in handbook.
