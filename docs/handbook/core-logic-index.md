# 核心逻辑覆盖索引

本文档是代码到 handbook 文档的索引。它不替代业务说明，只回答“某个核心类的当前行为应到哪里读”。业务域详解见 [business-logic/README.md](business-logic/README.md)，业务链路总览见 [business-flows.md](business-flows.md)，架构规则见 [architecture.md](architecture.md)，可靠性机制见 [reliability.md](reliability.md)。

覆盖状态：

- `Covered`：handbook 已说明入口、主路径和关键失败 / 一致性语义。
- `Partial`：handbook 已说明域级行为，但类级细节主要还要读代码或测试。
- `IndexOnly`：当前只作为薄包装、DTO 转换或适配入口列入索引，不单独展开业务语义。

## Auth

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `auth.application.AuthApplicationService` | me / session 聚合入口 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.LoginApplicationService` | 登录、验证码要求、JWT / refresh token 签发 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RefreshTokenApplicationService` | refresh / logout / refresh family reuse 处理 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RegistrationApplicationService` | Verify-First registration start; creates registration draft and code after user-domain preparation | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.RegistrationVerificationApplicationService` | resolves registration drafts, resends codes, consumes verification codes, and asks user domain to create the active user | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.PasswordResetApplicationService` | 找回密码 token、邮件、密码更新和 session 撤销 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.application.CaptchaApplicationService` | 验证码发放和校验 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.application.LoginRateLimitApplicationService` | 登录失败计数和验证码触发 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.service.AuthDomainService` | token / credential 基础规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.service.CaptchaDomainService` | 验证码规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.service.LoginRateLimitDomainService` | 登录风控规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.service.PasswordResetDomainService` | reset token 和重置规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.service.RefreshTokenDomainService` | refresh token 旋转 / family 规则 | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.domain.service.RegistrationDomainService` | registration input and Verify-First draft/code rules | [Auth 认证业务逻辑](business-logic/auth.md) | Partial |
| `auth.domain.repository.RegistrationDraftRepository` | opaque `registrationToken` to prepared registration draft store with TTL | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | refresh session 清理 job | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.infrastructure.job.PendingRegistrationUserCleanupJob` | 待激活用户清理 job | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |

Registration migration note：

- Removed legacy registration-token-to-user-id session repository: registration tokens no longer resolve to user ids; they resolve to full registration drafts.

## User

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `user.application.RefreshTokenSessionApplicationService` | DB refresh token session 存储、消费、撤销和过期清理 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRegistrationApplicationService#prepareRegistrationUser` | validates and prepares registration material without database writes or events | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserRegistrationApplicationService#createVerifiedRegistrationUser` | inserts the active user and publishes user policy existence | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserCredentialApplicationService` | 密码校验、密码策略和密码更新 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserReadApplicationService` | 用户摘要、批量读取、跨域 user 查询 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserProfileApplicationService` | 用户资料聚合、最近内容读取 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserAvatarApplicationService` | 头像上传 token / confirm | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserFileApplicationService` | `/files/**` 文件读取 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserModerationApplicationService` | 禁言 / 封禁状态和 policy event | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.AdminUserApplicationService` | 管理员用户搜索和角色修改 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.application.UserPointsApplicationService` | 用户积分视图 / wallet 协作兼容面 | [User 用户业务逻辑](business-logic/user.md) | Partial |
| `user.domain.service.PasswordPolicyDomainService` | 密码复杂度规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserCredentialDomainService` | 凭证校验和密码更新规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserModerationDomainService` | 用户处罚状态规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserReadDomainService` | 用户读取参数规范化 | [User 用户业务逻辑](business-logic/user.md) | Partial |
| `user.domain.service.UserRegistrationDomainService` | user registration fact rules | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.domain.service.UserRoleDomainService` | 管理员角色修改规则 | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `user.infrastructure.event.LocalUserEventPublisher` | user 本地事件发布 | [User 用户业务逻辑](business-logic/user.md) | Partial |
| `user.infrastructure.event.LocalUserPolicyEventPublisher` | user policy 事件发布 | [User 用户业务逻辑](business-logic/user.md) | Covered |

## Content

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.application.PostPublishingApplicationService` | 发帖、改帖、删帖写路径 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostReadApplicationService` | 帖子列表、详情、摘要查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentApplicationService` | 评论创建、编辑、删除和事件 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentReadApplicationService` | 评论列表和用户最近评论查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.BookmarkApplicationService` | 收藏关系 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.SubscriptionApplicationService` | 分类订阅关系 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CategoryApplicationService` | 分类列表 | [Content 内容业务逻辑](business-logic/content.md) | Partial |
| `content.application.TagApplicationService` | 热门标签和标签建议 | [Content 内容业务逻辑](business-logic/content.md) | Partial |
| `content.application.ReportApplicationService` | 举报创建和查询 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.ModerationApplicationService` | 内容治理动作和处罚协作 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostModerationApplicationService` | 帖子治理下线 / 状态变更 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostScoreRefreshApplicationService` | 帖子热度刷新调度入口 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.PostScoreUpdateApplicationService` | 单帖分数更新 | [Content 内容业务逻辑](business-logic/content.md) | Partial |
| `content.application.PostContractEventApplicationService` | post contract event 映射 / 发布 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.CommentContractEventApplicationService` | comment contract event 映射 / 发布 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.application.SocialInteractionProjectionApplicationService` | 被删内容的社交关系清理协作 | [Content 内容业务逻辑](business-logic/content.md) | Partial |
| `content.domain.service.PostPublishingDomainService` | 发帖 draft 和发布规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.CommentDomainService` | 评论目标解析、编辑和删除规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.ModerationDecisionDomainService` | 内容治理决策规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.domain.service.PostModerationDomainService` | 帖子治理状态规则 | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.infrastructure.event.PostDomainEventBridge` | post domain event 到 contract event bridge | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.infrastructure.event.CommentDomainEventBridge` | comment domain event 到 contract event bridge | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `content.infrastructure.event.SocialInteractionProjectionListener` | 内容删除后清理 social projection | [Content 内容业务逻辑](business-logic/content.md) | Partial |

## Social

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `social.application.LikeApplicationService` | 点赞关系、内容实体解析、任务 / 积分协作和事件 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.application.FollowApplicationService` | 关注关系和 follow event | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.application.BlockApplicationService` | 拉黑关系、follow 清理、IM policy outbox | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.service.LikeDomainService` | 点赞实体和计数规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.service.FollowDomainService` | 关注关系规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.domain.service.BlockDomainService` | 拉黑关系和 follow 清理规则 | [Social 社交业务逻辑](business-logic/social.md) | Covered |
| `social.infrastructure.event.LocalSocialDomainEventPublisher` | social domain event 到 contract event 映射 | [Social 社交业务逻辑](business-logic/social.md) | Covered |

## Notice

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `notice.application.NoticeApplicationService` | 通知写入、列表、未读数、批量已读 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.application.NoticeProjectionApplicationService` | content / social / moderation event 到通知读模型 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.domain.service.NoticeDomainService` | 通知分页、状态和创建校验 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.domain.service.NoticeProjectionDomainService` | 通知投影规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `notice.infrastructure.event.NoticeProjectionListener` | 本地 after-commit best-effort 通知投影 listener | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |

## Search And Ops

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `search.application.SearchApplicationService` | 搜索查询 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.application.SearchPostProjectionApplicationService` | outbox 触发后回源 content 并 upsert/delete ES | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.application.SearchReindexApplicationService` | reindex single-flight、mapping、alias 切换 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.application.ReindexJobApplicationService` | XXL reindex job 到 search owner action | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.application.SearchAdminApplicationService` | admin / ops search 管理入口 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Partial |
| `ops.application.OpsApplicationService` | `/api/ops/**` 运维入口，当前转发 search reindex owner action | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.domain.service.PostSearchDomainService` | 搜索 query 规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.domain.service.SearchReindexDomainService` | reindex 命名和执行规则 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.domain.service.KeywordHighlightSupport` | 搜索关键词高亮 | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Partial |
| `search.infrastructure.event.PostOutboxEnqueuer` | content event 到 search outbox | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `search.infrastructure.event.PostOutboxHandler` | search outbox handler，回源 content 后 upsert/delete ES | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |

## Analytics

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
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

## Market

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `market.application.MarketApplicationService` | market controller 聚合入口 | [Market 市场业务逻辑](business-logic/market.md) | IndexOnly |
| `market.application.MarketQueryApplicationService` | listing / order 查询、订单详情交付内容装配 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketListingApplicationService` | listing 创建、更新、暂停、恢复、关闭 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketInventoryApplicationService` | 预加载虚拟库存追加、查询、失效和 listing 库存联动 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketAddressApplicationService` | 收货地址簿 | [Market 市场业务逻辑](business-logic/market.md) | Partial |
| `market.application.MarketOrderApplicationService` | 下单、取消、交付、发货、确认 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.MarketDisputeApplicationService` | 买家争议、卖家处理、管理员裁决 | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.application.AdminMarketApplicationService` | admin dispute 聚合入口 | [Market 市场业务逻辑](business-logic/market.md) | IndexOnly |
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
| `wallet.application.WalletApplicationService` | wallet controller 聚合入口和 HTTP 幂等包装 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletAccountApplicationService` | 钱包账户创建、余额、状态、version 条件更新 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletLedgerApplicationService` | 总账交易、双分录、requestId replay 校验 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletRechargeApplicationService` | 充值订单和 RECHARGE 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletWithdrawApplicationService` | 提现订单、两段 WITHDRAW 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletTransferApplicationService` | 转账订单和 TRANSFER 总账 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletMarketApplicationService` | market escrow / release / refund owner action | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletRewardApplicationService` | growth / reward 入账 owner action | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.WalletAdminOpsApplicationService` | freeze / reverse 管理操作和审计 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.application.AdminWalletApplicationService` | admin wallet controller 聚合入口 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | IndexOnly |
| `wallet.domain.service.WalletAccountDomainService` | 账户类型、冻结状态和分录方向规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletLedgerDomainService` | 双分录平衡、金额上限和交易创建规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletOrderDomainService` | 充值 / 提现 / 转账订单金额和转账规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletAdminDomainService` | 管理员钱包操作 actor / reason 规则 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |
| `wallet.domain.service.WalletAmountPolicy` | 单次资金动作金额上限 | [Wallet 钱包业务逻辑](business-logic/wallet.md) | Covered |

## IM Policy Snapshot In Community App

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `im.application.ImPolicySnapshotApplicationService` | 给 im-realtime 拉取 user policy / block relation snapshot | [IM 消息业务逻辑](business-logic/im.md) | Covered |

## Scheduler / Job Entries

| Core class | Role | Handbook section | Coverage |
| --- | --- | --- | --- |
| `content.infrastructure.job.PostScoreRefresher` | 帖子热度刷新本地 job | [Content 内容业务逻辑](business-logic/content.md) | Covered |
| `search.infrastructure.job.SearchReindexHandler` | XXL `searchReindex` | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md) | Covered |
| `market.infrastructure.job.MarketOrderAutoConfirmHandler` | XXL `marketOrderAutoConfirm` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.infrastructure.job.MarketWalletActionProcessorHandler` | XXL `marketWalletActionProcessor` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `market.infrastructure.job.MarketWalletActionRecoveryHandler` | XXL `marketWalletActionRecovery` | [Market 市场业务逻辑](business-logic/market.md) | Covered |
| `user.infrastructure.job.PendingRegistrationUserCleanupHandler` | 待激活用户清理 handler | [User 用户业务逻辑](business-logic/user.md) | Covered |
| `auth.infrastructure.job.RefreshTokenCleanupJob` | 本地 refresh token cleanup | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `auth.infrastructure.job.PendingRegistrationUserCleanupJob` | 本地 pending registration cleanup | [Auth 认证业务逻辑](business-logic/auth.md) | Covered |
| `common-outbox.OutboxWorkerScheduler` | outbox 本地 worker scheduler | [可靠性机制](reliability.md) | Covered |

## Maintenance Rule

When adding or changing core backend behavior, update this index in the same change as the relevant handbook section:

1. Add the new `ApplicationService`, domain service, listener, handler, enqueuer or job.
2. Link it to the exact handbook section that describes current behavior.
3. `Partial` 只能短期使用；优先补充 [business-logic/README.md](business-logic/README.md) 下对应域文档，直到该行可以标为 `Covered`。
4. Do not point current behavior only at `docs/superpowers/specs` or `docs/superpowers/plans`; those are design and migration history unless explicitly restated in handbook.
