# 核心逻辑覆盖审计

本文档记录 handbook 覆盖如何对齐生产代码。它补充 [core-logic-index.md](core-logic-index.md)：索引用于导航，本文档记录扫描方法、分类规则、排除口径、当前缺口和修复顺序。

## 目标

核心运行时逻辑文档闭环指：生产代码里的每个核心入口和关键行为，都能从 [core-logic-index.md](core-logic-index.md) 追到 handbook 文档，并且该文档说明 owner、入口、主路径、状态、幂等、一致性、失败语义和关键代码。

前端按收窄口径纳入：业务状态、API 编排、路由到页面能力映射属于范围；纯展示组件、样式和无业务语义 helper 不属于范围。

## 分类

- `Covered`：handbook 已说明当前行为，而不只是出现类名。
- `IndexOnly`：薄绑定、DTO 转换或适配器入口，需要可导航，但不需要单独展开业务语义。
- `Excluded`：不是核心运行时逻辑，例如配置属性、简单类型转换、纯测试辅助或无业务语义的技术 glue。

`IndexOnly` 默认不需要链接专题文档。只有当适配器本身承载非显然的安全、幂等、路由或失败语义时，才升级为 `Covered` 或链接到专题页。`Excluded` 不复制到核心索引；只有为了让审计可重复时，才在本文档记录。

扫描过程中可以暂记“未分类”，但审计完成时它不是合法终态。每个复核后的候选必须是 `Covered`、`IndexOnly`，或在本文给出理由的 `Excluded`；本轮不使用 `Partial`。

## 候选扫描规则

先用命名约定做第一层候选，再用代码图谱和人工复核降噪。生产代码候选类包括以下后缀：

- `ApplicationService`
- `DomainService`
- `Policy`
- `Controller`
- `Listener`
- `Handler`
- `Enqueuer`
- `Job`
- `Scheduler`
- `Filter`
- `WebSocketHandler`
- 核心 `Service`
- 三个数据库迁移模块中的 `MigrationRunner`、`SchemaCatalog`、`SchemaVerifier`

第一层扫描后，按行为分类：是否被 controller、listener、job 或 handler 调用；是否参与事务、事件、outbox、恢复或投影；是否承载业务规则；或者是否只是配置、类型转换或技术 glue。

普通 `*Service` 要保守处理。只有它是真实用例入口、历史命名下承载业务规则、被外部运行时入口直接调用，或 handbook 已把它作为关键代码引用但索引名称漂移时，才作为核心候选。否则分类为 `Excluded`，或不进入候选集。

## 可重复扫描

最近一次生产源码基线扫描：2026-07-15。扫描范围是 `backend/**/src/main/java/**` 和前端收窄候选；测试源码不作为生产候选。

优先用代码图谱发现：

```cypher
MATCH (c:Class)
WHERE c.file_path CONTAINS '/src/main/java/'
  AND (
    c.name ENDS WITH 'ApplicationService'
    OR c.name ENDS WITH 'DomainService'
    OR c.name ENDS WITH 'Policy'
    OR c.name ENDS WITH 'Controller'
    OR c.name ENDS WITH 'Listener'
    OR c.name ENDS WITH 'Handler'
    OR c.name ENDS WITH 'Enqueuer'
    OR c.name ENDS WITH 'Job'
    OR c.name ENDS WITH 'Scheduler'
    OR c.name ENDS WITH 'Filter'
    OR c.name ENDS WITH 'WebSocketHandler'
    OR c.name ENDS WITH 'Service'
  )
RETURN c.name AS name, c.file_path AS path
ORDER BY path
```

数据库 migration 模块另外扫描 `*MigrationRunner.java`、`*SchemaCatalog.java`、`*SchemaVerifier.java`。该规则只针对 `community-db-migrations`、`community-oss-db-migrations`、`community-im-db-migrations`；普通业务包里名字包含 `Catalog` / `Verifier` 的端口或 bean discovery helper 仍需按行为人工判断。

图谱不可用时，用 shell 扫描兜底：

```bash
find backend -path '*/src/main/java/*' -name '*.java' | sort
```

将候选类名与 [core-logic-index.md](core-logic-index.md) 里的反引号条目比较，再按上面的规则分类未匹配候选。fallback 扫描有意放宽范围；没有经过图谱和人工复核前，不要把输出直接视为缺文档。

## 当前策略

本轮只做 handbook 文档审计和校准，不新增 CI guard、检查脚本或 workflow。以后若要自动阻止新增未分类候选，应作为独立变更评审，不能由本文档隐式授权。

## 文档形态

修复时可以为稳定运行时主题新增专题文档，但不要按类逐个建文档。专题文档放在 [core-logic/](core-logic/)，解释一组相关类共同形成的行为，再从 [core-logic-index.md](core-logic-index.md) 回链。专题可以扩散到初始缺口之外，但必须覆盖至少两个核心类或一个跨域 / 跨模块机制，补足只读领域文档会造成的误解，说明状态 / 失败 / 一致性 / 补偿，并且不是单类源码复述。

handbook 行为文档使用中文说明，类名、状态名、topic、配置键和命令保留英文原文。`docs/superpowers/plans` 和 `docs/superpowers/specs` 可中英混用。

## 修复优先级

1. 安全和身份新鲜度逻辑。
2. 异步一致性骨干：Kafka listener、outbox handler、enqueuer 和 dispatch application service。
3. IM core / realtime 的代码与索引命名漂移。
4. 恢复和补偿 job。
5. runtime、observability、gateway，以及收窄后的前端业务状态 / API 编排缺口。

## 当前分类基线

本表记录本轮已复核候选的当前分类。`Covered` 表示已进入核心索引并可追到 handbook 行为说明；`IndexOnly` 表示只需要导航，不单独展开业务语义。

| 候选 | 分类 | Handbook 落点 |
| --- | --- | --- |
| `auth.application.TokenFreshnessApplicationService` | `Covered` | [Token Freshness 与高风险请求安全](core-logic/security-token-freshness.md) |
| `auth.infrastructure.web.TokenFreshnessFilter` | `Covered` | [Token Freshness 与高风险请求安全](core-logic/security-token-freshness.md) |
| `auth.domain.repository.RefreshTokenRepository` | `Covered` | [登录与会话链路](auth-login-session-flow.md) |
| `auth.infrastructure.persistence.MyBatisRefreshTokenRepository` | `Covered` | [登录与会话链路](auth-login-session-flow.md) |
| `profile.controller.UserProfileController` | `IndexOnly` | [Profile 用户主页聚合逻辑](business-logic/profile.md) |
| `profile.application.UserProfileQueryApplicationService` | `Covered` | [Profile 用户主页聚合逻辑](business-logic/profile.md) |
| `interaction.controller.LikeInteractionController` | `IndexOnly` | [Interaction 互动写入编排逻辑](business-logic/interaction.md) |
| `interaction.application.LikeInteractionApplicationService` | `Covered` | [Interaction 互动写入编排逻辑](business-logic/interaction.md) |
| `content.application.ContentEntityResolutionApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#owner-entity-resolution) |
| `content.application.ContentEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `content.controller.FeedController` | `IndexOnly` | [Content 内容业务逻辑](business-logic/content.md#帖子读取) |
| `content.application.FeedReadApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#feed缓存与降级) |
| `content.application.FollowFeedReadApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#feed缓存与降级) |
| `content.application.CacheTtlPolicy` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#feed缓存与降级) |
| `content.application.HotPathPrewarmApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#热度预热与-counter) |
| `content.application.PostCounterApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#热度预热与-counter) |
| `content.domain.service.PostHotnessDomainService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#热度预热与-counter) |
| `content.application.PostMediaUploadRecoveryApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#帖子媒体上传) |
| `content.application.PostMediaReferenceApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#帖子媒体上传) |
| `content.application.PostMediaReferenceSchedulingApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#帖子媒体上传) |
| `content.application.PostMediaReferenceReconciliationApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#帖子媒体上传) |
| `content.infrastructure.event.ContentEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `content.infrastructure.event.PostHotFeedProjectionKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `content.infrastructure.event.PostMediaReferenceOutboxHandler` | `Covered` | [可靠性机制](reliability.md#content-media-upload-and-reference-recovery) |
| `content.infrastructure.job.PostMediaUploadRecoveryJob` | `Covered` | [可靠性机制](reliability.md#content-media-upload-and-reference-recovery) |
| `content.infrastructure.job.PostMediaReferenceReconciliationJob` | `Covered` | [可靠性机制](reliability.md#content-media-upload-and-reference-recovery) |
| `social.application.LikeCleanupReconciliationApplicationService` | `Covered` | [Social 社交业务逻辑](business-logic/social.md#一致性和补偿) |
| `social.infrastructure.event.SocialContentDeletionKafkaListener` | `Covered` | [可靠性机制](reliability.md#social-deleted-content-like-cleanup) |
| `social.infrastructure.job.SocialLikeCleanupReconciliationJob` | `Covered` | [可靠性机制](reliability.md#social-deleted-content-like-cleanup) |
| `community-oss.application.ObjectUploadRecoveryApplicationService` | `Covered` | [OSS 对象存储业务逻辑](business-logic/oss.md) |
| `community-oss.infrastructure.job.ObjectUploadRecoveryJob` | `Covered` | [可靠性机制](reliability.md#oss-upload-claim-recovery) |
| `analytics.application.AnalyticsRequestCaptureApplicationService` | `Covered` | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md#analytics-分析) |
| `analytics.infrastructure.event.AnalyticsRequestKafkaListener` | `Covered` | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md#analytics-分析) |
| `ops.controller.ProjectionOpsController` | `IndexOnly` | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md#ops-投影治理) |
| `ops.application.ProjectionGovernanceApplicationService` | `Covered` | [Notice / Search / Analytics / Ops 业务逻辑](business-logic/notice-search-analytics-ops.md#ops-投影治理) |
| `ops.application.OutboxGovernanceApplicationService` | `Covered` | [可靠性机制](reliability.md#outbox-governance) |
| `ops.application.CompensationGovernanceApplicationService` | `Covered` | [可靠性机制](reliability.md#compensation-governance) |
| `ops.application.HotCacheGovernanceApplicationService` | `Covered` | [可靠性机制](reliability.md#hot-cache-governance) |
| `wallet.application.WalletRewardProjectionApplicationService` | `Covered` | [Wallet 钱包业务逻辑](business-logic/wallet.md#奖励) |
| `wallet.infrastructure.event.WalletRewardKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `drive.infrastructure.job.DriveUploadRecoveryJob` | `Covered` | [网盘业务逻辑](business-logic/drive.md#详细链路) |
| `growth.infrastructure.event.TaskProgressEventBackboneKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.application.ImPolicyProjectionApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.application.ImPolicyEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.infrastructure.event.ImPolicyBackboneKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.infrastructure.event.JdbcImPolicyProjectionOutboxAdapter` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `notice.infrastructure.event.NoticeProjectionKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `runtime.application.RuntimeConfigApplicationService` | `Covered` | [Runtime Configuration](core-logic/runtime-configuration.md) |
| `runtime.controller.RuntimeConfigController` | `IndexOnly` | [Runtime Configuration](core-logic/runtime-configuration.md) |
| `search.infrastructure.event.SearchPostProjectionKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `social.application.SocialEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `social.infrastructure.event.SocialEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.application.UserEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.infrastructure.event.UserEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `community-db-migrations.CommunityMigrationRunner` / `CommunitySchemaCatalog` / `CommunitySchemaVerifier` | `Covered` | [数据与存储](data-and-storage.md#flyway-migration-deployables) |
| `community-oss-db-migrations.OssMigrationRunner` / `OssSchemaCatalog` / `OssSchemaVerifier` | `Covered` | [数据与存储](data-and-storage.md#flyway-migration-deployables) |
| `community-im-db-migrations.ImMigrationRunner` / `ImSchemaCatalog` / `ImSchemaVerifier` | `Covered` | [数据与存储](data-and-storage.md#flyway-migration-deployables) |
| `im.core.infrastructure.persistence.typehandler.UuidBinaryTypeHandler` | `IndexOnly` | [数据与存储](data-and-storage.md#mysql) |
| `common.observability.app.RuntimeApplicationLifecycleListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.autoconfig.RuntimeSnapshotScheduler` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.http.ServletAccessRuntimeLogFilter` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.kafka.RuntimeKafkaProducerListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.kafka.RuntimeKafkaRebalanceListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `gateway.canary.CanaryInstanceFilter` | `Covered` | [Gateway Runtime](core-logic/gateway-runtime.md) |
| `gateway.config.GatewayConfigRefreshListener` | `Covered` | [Gateway Runtime](core-logic/gateway-runtime.md) |
| `im.common.ImContractVersions` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.common.ImSchemaVersionDeserializer` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.application.ConversationApplicationService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.application.PrivateMessageApplicationService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.application.RoomApplicationService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.application.RoomMessageApplicationService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.application.UnreadApplicationService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.domain.service.PrivateMessageDomainService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.domain.service.RoomMembershipDomainService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.domain.service.RoomMessageDomainService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.core.domain.service.UnreadDomainService` | `IndexOnly` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.fanout.RoomFanoutRoutingService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.fanout.RoomFanoutOwnerService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.fanout.KafkaRoomFanoutDispatcher` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.fanout.RoomFanoutTargetConsumer` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.presence.RoomLocalPresenceService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `frontend.views.postsFeedState` | `Covered` | [前端业务状态与 API 编排](core-logic/frontend-business-state.md) |

Frontend 收窄范围的 API service 和 `*State.js` 文件已经进入 [core-logic-index.md](core-logic-index.md#frontend-business-state-and-api-orchestration)，行为说明见 [前端业务状态与 API 编排](core-logic/frontend-business-state.md)。

## 审计备注

- IM policy 的 user/social 事件统一由 `ImPolicyBackboneKafkaListener` 从 owner Kafka topic 进入 `ImPolicyProjectionApplicationService`，再通过 application-owned port 写 `projection.im.policy`。
- refresh session 归 auth 所有；`RefreshTokenSessionApplicationService` 已退役，当前 DB adapter 是 `MyBatisRefreshTokenRepository`。
- 用户主页聚合已从 user 移到 profile；点赞 POST 的可信目标解析已从 social 移到 interaction。
- 标准内容/点赞奖励由 wallet listener/projection 消费 owner event；`UserRewardApplicationService` 和 `UserRewardKafkaListener` 已退役。
- 按上述候选规则复核后，本轮没有剩余未分类或 `Partial` 候选。`docs/superpowers/**` 不在本轮允许修改范围内，不能据此宣称其已同步。

## Excluded Examples

本表只记录容易被重复发现或争议的排除项。普通配置属性、生成类 dataobject、mapper row type 和纯展示前端组件，不需要逐个列入。

| 候选 | 排除理由 |
| --- | --- |
| `ops.application.OutboxHandlerCatalog` / `ops.infrastructure.outbox.SpringOutboxHandlerCatalog` | handler bean discovery 的技术目录；独立业务语义在 `ProjectionGovernanceApplicationService` 与 `OutboxProjectionLagAdapter`，目录本身不构成用例。 |
| `content.application.ContentHotPathProperties` | 纯配置绑定；默认值和运行语义已由 content 文档覆盖，不是独立核心入口。 |
| `analytics.application.AnalyticsRequestCapturePort` | application-owned 发布端口；行为由 capture application 与 Kafka adapter 覆盖。 |
| `common.trace.TraceJobRunner` | 为 job 建 trace 的横切包装，没有 owner 业务规则。 |
| `*SchemaMismatchException` | migration 精确校验的异常载体；核心判断在对应 `SchemaCatalog` / `SchemaVerifier`。 |
| `frontend.ui.toastService` | 只保存并调用 toast handler，不解释业务状态、权限、路由或 API 结果。 |
