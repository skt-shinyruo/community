# 核心逻辑覆盖审计

本文档记录 handbook 覆盖如何对齐生产代码。它补充 [core-logic-index.md](core-logic-index.md)：索引用于导航，本文档记录扫描方法、分类规则、排除口径、当前缺口和修复顺序。

## 目标

核心运行时逻辑文档闭环指：生产代码里的每个核心入口和关键行为，都能从 [core-logic-index.md](core-logic-index.md) 追到 handbook 文档，并且该文档说明 owner、入口、主路径、状态、幂等、一致性、失败语义和关键代码。

前端按收窄口径纳入：业务状态、API 编排、路由到页面能力映射属于范围；纯展示组件、样式和无业务语义 helper 不属于范围。

## 分类

- `Covered`：handbook 已说明当前行为，而不只是出现类名。
- `IndexOnly`：薄绑定、DTO 转换或适配器入口，需要可导航，但不需要单独展开业务语义。
- `Excluded`：不是核心运行时逻辑，例如配置属性、简单类型转换、纯测试辅助或无业务语义的技术 glue。
- `Missing`：应该进入索引或文档，但还不能从核心索引追到足够的 handbook 覆盖。

`IndexOnly` 默认不需要链接专题文档。只有当适配器本身承载非显然的安全、幂等、路由或失败语义时，才升级为 `Covered` 或链接到专题页。`Excluded` 不复制到核心索引；只有为了让审计可重复时，才在本文档记录。

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

第一层扫描后，按行为分类：是否被 controller、listener、job 或 handler 调用；是否参与事务、事件、outbox、恢复或投影；是否承载业务规则；或者是否只是配置、类型转换或技术 glue。

普通 `*Service` 要保守处理。只有它是真实用例入口、历史命名下承载业务规则、被外部运行时入口直接调用，或 handbook 已把它作为关键代码引用但索引名称漂移时，才作为核心候选。否则分类为 `Excluded`，或不进入候选集。

## 可重复扫描

最近一次基线扫描：2026-07-04。codebase-memory project：`home-feng-code-project-community`；索引状态为 `ready`。

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

图谱不可用时，用 shell 扫描兜底：

```bash
find backend -path '*/src/main/java/*' -name '*.java' | sort
```

将候选类名与 [core-logic-index.md](core-logic-index.md) 里的反引号条目比较，再按上面的规则分类未匹配候选。fallback 扫描有意放宽范围；没有经过图谱和人工复核前，不要把输出直接视为缺文档。

## 当前策略

当前先做文档审计。现有历史缺口完成分类和修复前，不添加会让 CI 失败的守卫。基线清理后，再添加轻量检查，只阻止新增未分类核心候选。

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
| `content.application.ContentEntityResolutionApplicationService` | `Covered` | [Content 内容业务逻辑](business-logic/content.md#owner-entity-resolution) |
| `content.application.ContentEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `content.infrastructure.event.ContentEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `content.infrastructure.event.SocialInteractionBackboneKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `drive.infrastructure.job.DriveUploadRecoveryJob` | `Covered` | [网盘业务逻辑](business-logic/drive.md#详细链路) |
| `growth.application.TaskProgressOutboxDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.CommentTaskProgressKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.CommentTaskProgressOutboxEnqueuer` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.LikeTaskProgressKafkaOutboxEnqueuer` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.LikeTaskProgressKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.PostTaskProgressKafkaOutboxEnqueuer` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.PostTaskProgressKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.TaskProgressEventBackboneKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `growth.infrastructure.event.TaskProgressKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.application.ImPolicyEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `im.infrastructure.event.ImPolicyBackboneKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `notice.infrastructure.event.NoticeProjectionKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `runtime.application.RuntimeConfigApplicationService` | `Covered` | [Runtime Configuration](core-logic/runtime-configuration.md) |
| `runtime.controller.RuntimeConfigController` | `IndexOnly` | [Runtime Configuration](core-logic/runtime-configuration.md) |
| `search.infrastructure.event.SearchPostProjectionKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `social.application.SocialEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `social.infrastructure.event.SocialEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.application.UserEventDispatchApplicationService` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.infrastructure.event.CommentRewardOutboxEnqueuer` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.infrastructure.event.CommentRewardOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.infrastructure.event.UserEventKafkaOutboxHandler` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `user.infrastructure.event.UserRewardKafkaListener` | `Covered` | [异步事件骨干](core-logic/async-event-backbone.md) |
| `common.observability.app.RuntimeApplicationLifecycleListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.autoconfig.RuntimeSnapshotScheduler` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.http.ServletAccessRuntimeLogFilter` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.kafka.RuntimeKafkaProducerListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `common.observability.kafka.RuntimeKafkaRebalanceListener` | `Covered` | [Runtime Observability](core-logic/runtime-observability.md) |
| `gateway.canary.CanaryInstanceFilter` | `Covered` | [Gateway Runtime](core-logic/gateway-runtime.md) |
| `gateway.config.GatewayConfigRefreshListener` | `Covered` | [Gateway Runtime](core-logic/gateway-runtime.md) |
| `im.common.CurrentSchemaVersionFilter` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |
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
| `im.realtime.fanout.RoomFanoutTargetController` | `IndexOnly` | [IM Core Runtime](core-logic/im-core-runtime.md) |
| `im.realtime.presence.RoomLocalPresenceService` | `Covered` | [IM Core Runtime](core-logic/im-core-runtime.md) |

Frontend 收窄范围的 API service 和 `*State.js` 文件已经进入 [core-logic-index.md](core-logic-index.md#frontend-business-state-and-api-orchestration)，行为说明见 [前端业务状态与 API 编排](core-logic/frontend-business-state.md)。

## 审计备注

- IM policy 当前有一条需要持续可见的不对称路径：social block 事件可通过 `ImPolicyBackboneKafkaListener` 从 `social.events` 进入 `projection.im.policy`；user policy 事件也可通过本地 `ImPolicyOutboxEnqueuer` 从 `UserContractEvent` 进入同一个内部 topic。该备注记录现状，不按代码缺陷处理。

## Excluded Examples

本表只记录容易被重复发现或争议的排除项。普通配置属性、生成类 dataobject、mapper row type 和纯展示前端组件，不需要逐个列入。

| 候选 | 排除理由 |
| --- | --- |
