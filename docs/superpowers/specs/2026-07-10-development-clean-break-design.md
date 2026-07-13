# 开发期兼容路径一次性收敛设计

## 状态

- 日期：2026-07-10
- 决策：已批准
- 适用范围：`community-app`、`community-im`、`community-common`、`community-gateway`、前端 IM client、部署配置与 handbook
- 前提：项目尚未上线，没有需要保留的数据库记录、Kafka 消息、浏览器客户端或旧服务实例

## 背景

当前代码保留了多条为历史数据、旧客户端和滚动迁移准备的兼容路径：IM v1 缺省版本、timestamp-derived projection version、room fanout legacy/shadow/HTTP、owner contract event 的 local/Kafka 双轨、Market 旧地址快照回查、Growth 同步 action API，以及 MDC `traceId` 旧 key。

这些路径在已有生产系统中可以降低迁移风险，但本项目没有历史状态。继续保留它们会产生错误的默认配置、不可达代码、重复消费面和额外测试矩阵。尤其是默认 `user.events.publisher=outbox-kafka` 时，`user.events` 没有进入 IM policy projection；这不是兼容能力，而是正确性断链。

本设计采用一次性 clean break。实现时同步修改所有生产者、消费者、配置、baseline SQL、测试和文档，不提供旧数据迁移或旧实例共存能力。

### 2026-07-13 补充收敛范围

第二轮审计继续按相同前提删除残留兼容面：numeric user ID tombstone、HTTP DTO 废弃字段、Like 双时间字段和缺失 relation key 回退、无 request fingerprint 的幂等接口与旧 Redis 编码、缺失 refresh-token state 的 ACTIVE 默认、旧配置键、接口静默 default 实现、IM 非 canonical event ID 重写，以及前端对旧/臆测 DTO 字段和错误码的双读。

数据库 baseline 直接声明最终字段、约束和索引，不再通过 `information_schema`、动态 `ALTER/DROP/UPDATE` 升级旧 volume。Mock Data Studio 只保留 canonical 建表与默认配置幂等 seed，不迁移旧 `ai_config`。旧开发数据卷、Redis key、Kafka 消息和浏览器状态统一清空重建。

## 目标

1. IM JSON 契约始终显式携带且严格校验 `schemaVersion: 1`。
2. IM projection 只接受 owner 分配的显式正版本，不再从时间戳推导顺序。
3. room fanout 只保留 Redis presence + Kafka owner/target 路径。
4. Content、Social、User 的跨域事件只通过 producer outbox + Kafka backbone 发布，下游只保留 Kafka ingress。
5. 修复 User/Social policy event 到 IM realtime projection 的完整可靠链路。
6. 保留 Market 下单幂等，删除旧物理订单地址快照回查。
7. 删除未使用的 Growth 同步 action surface 和旧事件中转链路。
8. MDC 只维护 `trace.id` 与 `span.id`。
9. 让代码、部署配置、ArchUnit/retirement tests 和 handbook 描述同一套当前架构。

## 非目标

- 不支持旧数据库 volume、旧 Kafka topic 内容、旧 outbox row 或旧浏览器 bundle 原地升级。
- 不设计两阶段发布、shadow 观测、自动 transport fallback 或回滚到 local publisher 的开关。
- 不删除 Market 的 `buyer + requestId` 幂等重放语义。
- 不删除统一 Market 模型中的虚拟商品、虚拟交付或预置库存能力；退休的是旧 virtual market surface，而不是当前业务能力。
- 不全局重命名 HTTP/JSON/数据库中的业务字段 `traceId`；本次只删除 MDC 旧 key。
- 不恢复 root legacy `service/entity/mapper/app`、旧 posts list route 或正文格式迁移路径。

## 方案选择

考虑过三种方案：

1. **一次性 strict clean break（采用）**：删除兼容实现，重建开发状态，同步部署所有生产者和消费者。代码和运行态最简单。
2. **保留开关但默认新路径**：仍需维护旧 bean、配置矩阵和重复测试，且可能再次被误启用，不采用。
3. **两阶段兼容发布**：先显式写新字段但继续读取旧格式，再收紧读取。适用于已有生产流量，与当前前提不符，不采用。

## 总体事件架构

跨域事件的唯一主干为：

```text
owner ApplicationService
  -> owner event publisher
  -> DB outbox eventbus.<owner>
  -> owner OutboxHandler
  -> owner dispatch ApplicationService
  -> Kafka <owner>.events
  -> consumer Kafka Listener
  -> consumer ApplicationService
  -> consumer state / projection outbox
```

规则：

- owner publisher 不再有 `local` 实现或 publisher mode selector。
- `community-app` 的正确性链路要求 outbox 开启；`events.outbox.enabled=false` 不再表示切换到 local，完整应用应 fail fast。
- `community-app` 为 `content.events`、`social.events`、`user.events` listener container 配置统一的有限重试和 `.dlq` recoverer；现有 `community.kafka-policy.retry/dlq` 不能只停留在配置对象。
- Kafka listener 只做反序列化、目标事件筛选、校验转换和调用同域 `*ApplicationService`。
- listener 不得直接调用同域 publisher、repository 或其他 infrastructure class。
- 已识别事件的坏 payload 必须抛错进入 retry/DLQ；只有与本 projection 无关的事件类型可以忽略。
- consumer-side projection 必须按稳定 source event id 或 owner version 幂等。

## 1. IM JSON 契约

### Wire contract

所有 IM command、event、projection、WebSocket frame 和内部 `RoomFanoutCommand` 都必须：

- 写出 `"schemaVersion": 1`；
- 读取时只接受数值 `1`；
- 对缺失、`null`、`0`、负数和大于 `1` 的值直接拒绝；
- 继续忽略未知字段，支持同一 schema version 内的增量字段演进；
- 不把缺失版本绑定到未来的 `CURRENT_SCHEMA_VERSION`。

`ImContractVersions.schemaVersionOrCurrent` 改为严格的 supported-version 校验。`@ImSchemaVersion` 不再通过 custom include filter 省略字段，`CurrentSchemaVersionFilter` 删除。record 构造器仍是最终校验边界，不能只依赖 Jackson 的 `required` 元数据。

### Entry behavior

- Kafka command/event 反序列化失败沿用现有 retry/DLQ 隔离。
- projection snapshot 中 schema 错误会使本次 refresh 失败，readiness/watermark 不推进。
- WebSocket codec 在 type、连接状态和 projection readiness 判断前先校验顶层 schema；非法版本统一返回 `unsupported_schema_version`，不执行命令。
- 前端 IM client 的 connect/send frame 始终写 `1`，收到 server frame 后先校验版本再派发业务事件。非法 server frame 按 WebSocket protocol error 关闭连接并报告错误，不触发业务回调。

未来增加 v2 时必须增加显式 v2 分支或 DTO，不能重新引入“缺失即当前版本”。

## 2. IM projection 显式版本

以下 delta 和 snapshot entry 的 `version` 改为必填正整数：

- `RoomMemberChanged` / `RoomMembershipEntry`
- `UserMessagingPolicyChanged` / `UserMessagingPolicyEntry`
- `UserBlockRelationChanged` / `UserBlockRelationEntry`

`occurredAtEpochMillis` 可继续用于审计和观测，但不得参与排序。删除 `ProjectionVersions` 中的 epoch shift、timestamp fallback 和 legacy resolve；只保留显式版本校验以及 snapshot watermark 合并所需的小型 helper。

三个 snapshot 保留 boxed `Long snapshotHighWatermark`，构造器要求字段非空且非负，以便区分“字段缺失”和显式 `0`。空库合法值为 `0`；非空 entry 的版本必须为正数。snapshot 应用仍使用 `max(entry.version, snapshotHighWatermark)` 防止 snapshot 之后的旧 delta 回滚，这属于正常并发排序，不是历史兼容。

room membership counter 从 `0` 开始，事务内 `SELECT ... FOR UPDATE` 后执行 `current + 1`，删除 `legacyCompatibleVersionFloor()`。User moderation、Social block 和 membership 的 producer 在发布前都校验版本为正数，不能把 `null` 转成 `0`。

baseline SQL 删除 timestamp-domain backfill：

- identity policy counter 初始化为 `0`；demo user seed 使用显式正版本并推进 counter；
- social block counter 初始化为 `0`；
- IM membership row/counter 不再乘 `4096`，counter 初始化为 `0`。

实现完成后必须同时清空旧 projection state、相关 outbox/Kafka 内容和开发数据库 volume；否则残留的巨大 timestamp version 会永久压住从 `1` 开始的新事件。

## 3. Room fanout routed-only

最终链路：

```text
im.event.room-persisted
  -> shared owner consumer group
  -> Redis RoomPresenceDirectory
  -> routes for active workers
  -> Kafka RoomFanoutCommand to worker inbox partition
  -> target consumer
  -> RoomFanoutTargetService sourceEventId dedupe
  -> local RoomFanoutCoalescer
  -> roomUpdatedBatch
```

删除：

- `RoomPersistedLegacyConsumer`
- `HttpRoomFanoutDispatcher`
- `RoomFanoutTargetController` 及其 internal HTTP route/security rule
- `NoopRoomPresenceDirectory`
- `mode`、`transport`、`owner-flush-interval`、`target-path` 和 HTTP target timeout 配置
- shadow 专用 ticker、pending queue、flush/retry coalescing 代码和 legacy metrics

保留 Kafka topic `im.command.room-fanout-routed`，避免没有收益的命名变更。owner consumer 无条件执行 route + dispatch；Kafka dispatcher 是唯一 `RoomFanoutDispatcher`。它等待 broker ack，任一 target 失败时向 owner listener 抛错以触发原 persisted event 重试。当前进程内已成功的 target 通过稳定 `sourceEventId` 抑制重复；跨重启重复由 state-only seq 幂等收敛。

Redis room presence 是强依赖，不再有 disabled/no-op 运行态。Kafka-only worker directory 只解析 `workerId` 和 `roomFanoutInboxSlot`，不再要求 HTTP URI metadata。

target delivery 的语义是 state-only at-least-once，不宣称跨进程重启 exactly-once。`RoomFanoutTargetService` 的有界内存 `sourceEventId` 集合只抑制当前进程内重复；`RoomFanoutCoalescer` 按 room/seq 取最大值保证跨重启重复仍幂等。target 必须在本地 enqueue 成功后才完成去重记录；若先预留 source id，则 enqueue 异常时必须撤销预留，使 Kafka retry 能重新应用。

### Presence consistency

local connection/room state 是当前 worker 的事实，Redis presence 是可重建的派生目录。membership delta 无论是首次应用、重复还是 stale delivery，都要根据 `MembershipProjectionService` 的当前状态重新协调该用户的现有连接，不能因 projection version 已推进就跳过本地修复。

`RoomLocalPresenceService` 在每个 room 的串行临界区内更新 `WsConnection`/`RoomLocalIndex` 的期望状态，再尝试发布 Redis presence。Redis 失败不回滚或阻断已经正确的本地状态，而是把 room 记入 pending reconciliation。heartbeat 对 active rooms 和 pending deactivate rooms 逐 room 重试，单个 room 失败不能中止后续 room；每次重试都重新读取当前本地连接状态，避免 heartbeat 与最后一个 disconnect 竞争后重新发布 ghost presence。

Redis 目录使用单 key 原子表示每个 room 的 worker lease，例如以 workerId 为 member、过期 epoch millis 为 score 的 sorted set。activate 为单次 upsert，deactivate 为单次 remove，读取时清理过期 score 后返回未过期 members；不再使用可能部分成功的 `SADD + SET` / `SREM + DEL` 多 key 转换。

部署必须给每个并存 worker 唯一 inbox slot：single 为 `0`，三节点 cluster 为 `0/1/2`。删除配置和 annotation 中所有 `:0` fallback；本地 slot 缺失或越界时启动失败。service discovery 发现重复或缺失的远端 slot 时 routing fail closed。compose 静态测试在发布前保证已知拓扑 slot 唯一。

topic partition 数继续为 64。HTTP `target-timeout` 删除，但 Kafka broker ack 仍必须有界；属性改名为 `im.room-fanout.publish-timeout`，默认 `PT1S`。部署脚本显式创建 routed command/DLQ，并为 `im-realtime` 同一 error handler 消费的每个 IM event source topic 创建同 partition 数的 `.dlq`；其中 `im.event.room-persisted.dlq` 明确为 12 partitions。

## 4. 单一事件 backbone 与 IM policy

### Owner producers

Content、Social、User 各保留唯一 outbox publisher：

- `eventbus.content -> content.events`
- `eventbus.social -> social.events`
- `eventbus.user -> user.events`

删除三个域的 local contract-event publisher、`*.events.publisher` 配置、Nacos 的 local override 和 rollback 注释。明确包括 `LocalContentEventPublisher`、`LocalSocialDomainEventPublisher`、`LocalUserPolicyEventPublisher`，以及没有调用者的 `LocalUserEventPublisher` / `UserEventPublisher`。owner 内部 domain event 到本域 ApplicationService 的 Spring event bridge不属于跨域兼容路径，可以保留。

### Downstream consumers

Search 只保留 `content.events -> SearchPostProjectionApplicationService`，删除 `projection.search.post` enqueuer/handler，并把 `ProjectPostOutboxCommand` 等名称改为不含旧 transport 语义的 application command。

删除只由 local publisher 喂入、在 Kafka backbone 下重复或不可达的中转组件：

- Search post projection outbox path
- Growth post/comment/like projection outbox、二次 Kafka topic、sender 和旧 listener
- User comment reward projection outbox path
- Hot-feed local contract-event listener

Search、Growth、User reward、Hot feed、Notice 均通过 owner Kafka contract event 进入各自同域 ApplicationService。producer outbox 负责“owner transaction 到 Kafka”的可靠投递；Kafka retry/DLQ 与 consumer 幂等负责下游失败，不再额外复制迁移期路径。

### IM policy convergence

User 与 Social 的 canonical flow 为：

```text
eventbus.user/social
  -> user.events/social.events
  -> ImPolicyBackboneKafkaListener
  -> ImPolicyProjectionApplicationService
  -> application-owned projection outbox port
  -> projection.im.policy
  -> ImPolicyKafkaOutboxHandler
  -> ImPolicyEventDispatchApplicationService
  -> im.event.user-messaging-policy-changed / im.event.user-block-relation-changed
```

`ImPolicyBackboneKafkaListener` 增加 user topic ingress，同时保留 social topic ingress，但只依赖新建的同域 `ImPolicyProjectionApplicationService`。当前 listener 直调 `ImPolicyChangePublisher` 的结构删除；outbox writer 改为 application-owned port 的 infrastructure adapter。

投影 command 必须携带 source domain、source event id、owner version 和原始 occurredAt。Block 路径不能再用本机当前时间替换 source time。projection outbox event id 使用不超过 64 字符的确定性摘要派生，确保 Kafka 在 DB commit 后、offset commit 前重放时只落一条 row。

部署脚本显式创建 `content.events`、`social.events`、`user.events` 及对应的 `.dlq` topics，不依赖 broker auto-create。DLQ partition 数与 source topic 一致，recoverer 保留 source topic/partition/offset 和异常信息 headers。

## 5. Market 下单语义

保留并强化现有双层幂等：

- HTTP `Idempotency-Key` guard；
- 订单表 `(buyer_user_id, request_id)` 唯一键及 application replay lookup。

同一 buyer/requestId、相同 listing/quantity/address 的重试必须在 listing 状态和库存校验前返回原订单。锁后竞态和 duplicate insert 也返回同一订单。参数不一致仍返回 replay conflict。这是业务正确性，不是历史兼容。

删除旧物理订单 `addressIdSnapshot == null` 时回查当前地址并逐字段比较的逻辑。新物理订单必须持久化包含 `addressId` 和所有领域必填地址字段的快照；邮编等领域可选字段仍可为空。replay 直接比较请求 `addressId` 与订单 `addressIdSnapshot`。既有物理订单缺失 snapshot 时返回 `REQUEST_REPLAY_CONFLICT`，不访问 address repository。虚拟订单不要求地址快照。

## 6. Growth surface

删除：

- `GrowthTaskProgressActionApi`
- `GrowthTaskProgressActionApiAdapter`
- 仅供该 API 使用的 comment/like request model
- 第 4 节列出的旧 Growth projection outbox 与二次 Kafka pipeline

唯一入口是 content/social contract event 的 Kafka listener，再调用 `TaskProgressApplicationService`。保留 post/comment/like-created/like-removed 的稳定 source id、任务事件日志去重和 rollback 语义。

`UserLevelQueryApi` 等仍有真实同步查询用途的 owner API 不在删除范围内。

## 7. Trace MDC

`TraceContext`、`TraceContextScope`、gateway access log 和 IM WebSocket event logger 只写入、保存、恢复和清理：

- `trace.id`
- `span.id`
- 既有 event category/action/outcome 等非 trace 字段

删除 `MDC_KEY_LEGACY_TRACE_ID` 及所有 `MDC.put/remove/restore("traceId")`。`ImWebSocketHandler` 的 raw `MDC_TRACE_ID = "traceId"` 改用 canonical constant。logback 中仅为旧 key 存在的 `excludeMdcKeyName=traceId` 同步删除；结构化日志继续只输出 `trace.id`。

HTTP error response、outbox envelope、数据库列、Java result field 或日志 message 参数中名为 `traceId` 的业务/传输字段不属于 MDC 兼容，不做全局替换。

## DDD 与架构守卫

`community-app` 的新增和修改必须满足仓库 tactical layering：

- Kafka listener -> 同域 `*ApplicationService`；
- application service -> application-owned port；
- infrastructure adapter -> `JdbcOutboxEventStore` / Kafka sender；
- listener 不得直接依赖同域 infrastructure publisher。

加强 `ListenerBoundaryArchTest`，覆盖当前 `Listener/Handler/Bridge/Enqueuer/Job -> same-domain infrastructure` 的 publisher 命名漏网。增加 retirement/structure tests，禁止重新出现：

- local owner contract-event publisher selector；
- Search/Growth/User reward 旧 projection 中转链；
- Growth 同步 task progress action API；
- room fanout legacy/shadow/HTTP 类和配置；
- `CurrentSchemaVersionFilter` 与 MDC legacy key。

现有 root legacy package、旧 virtual market class/table、旧 posts list route 和旧 growth surface retirement tests 继续保留。

## 错误处理

- 非法 schema/version：fail closed，不补默认值，不从 timestamp 推导。
- Kafka recognized bad event：抛错，由现有 retry/DLQ 处理；不得提交为成功并静默丢弃。
- `community-app` source backbone 在有限重试耗尽后发布到对应 `<source-topic>.dlq` 并提交原 offset；DLQ publish 失败时不得吞掉原消费异常。
- 非目标 event type：安全忽略。
- projection snapshot 非法：保持旧 projection/readiness 状态，本次 refresh 失败并可重试。
- room fanout 无 presence、无 worker slot、重复 slot 或 dispatch 失败：启动或消费失败，不回退 legacy/HTTP。
- membership local reconciliation 不因 Redis 暂时失败而回滚；失败 room 留在 pending set，由 heartbeat 逐 room 修复。owner/target Kafka 异常耗尽重试后必须有已创建的同 partition `.dlq` 可接收。
- Market replay 参数不一致或物理 snapshot 缺失：返回 replay conflict，不查询当前地址来猜测旧订单语义。

## 测试策略

### IM contracts

- 反射枚举全部 versioned records，覆盖 command/event/projection/frame/`RoomFanoutCommand`。
- 对每个 record 断言写出 schema `1`，缺失/`null`/`0`/负数/`2` 均拒绝，未知字段仍可忽略。
- Kafka command/event、projection WebClient 和 WebSocket 入口分别增加真实反序列化失败测试。
- 前端断言三个 outbound frame 显式写 `1`，非法 inbound frame 不触发业务回调。

### Projection

- delta/entry 缺失或非正 version 失败。
- occurredAt 更晚也不能覆盖显式更高 version。
- snapshot watermark 缺失失败，显式空库 watermark `0` 成功。
- membership counter 从 `0` 产生 `1` 并单调增长；SQL 不含 `* 4096` seed。

### Fanout

- ApplicationContext 只存在 owner consumer、Kafka dispatcher、target consumer 和 Redis presence。
- owner 对多个 active worker 发送到正确 partition；partial failure 触发重试；相同 sourceEventId 在当前 target 进程内只 enqueue 一次，enqueue fail-once 后重试可以成功。
- membership duplicate/stale event 会按当前 projection 修复本地连接；Redis activate/deactivate fail-once 后 heartbeat 修复，单 room 失败不阻断其他 room。
- Redis sorted-set lease 的 activate/deactivate 是单 key 原子转换，过期 worker 在读取时清理。
- single/cluster compose 测试锁定 required/unique slots；HTTP endpoint 断言退休；Kafka bootstrap 覆盖 `im.event.room-persisted.dlq` 和所有 realtime source DLQ。

### Event backbone

- 默认上下文每个 owner 只有一个 outbox publisher，不存在 publisher selector/local bean。
- Content event 经 outbox/Kafka 只触发一次 Search/Growth/User reward/Hot-feed/Notice 对应 application boundary。
- User 与 Social policy event 都写入同一 `projection.im.policy`；source time/version 保真；重复 source event 不增加 row。
- recognized bad owner event 经配置的有限重试进入 source topic 对应 `.dlq`，headers 可定位原 topic/partition/offset。
- 全部 inbound adapters 通过 ArchUnit 验证只进入同域 ApplicationService。

### Market/Growth/Trace/Docs

- Market 覆盖售罄后 replay、锁后 replay、duplicate insert、参数冲突、物理 addressId 匹配和 replay 不访问 address repository。
- Growth 覆盖唯一 Kafka 入口和 like rollback，并断言同步 action/API model 类不存在。
- TraceContext scope 与 gateway access log 断言只维护 `trace.id`/`span.id`。
- route/config/class retirement tests 和 handbook link/search checks 防止旧描述回流。

## 文档和配置同步

同步以下 handbook SSOT：

- `architecture.md`、`system-design.md`
- `core-logic/async-event-backbone.md`、`core-logic/im-core-runtime.md`
- `business-flows.md`、`integration-contracts.md`、`reliability.md`、`data-and-storage.md`
- `business-logic/im.md`、`growth.md`、`content.md` 及对应 core classes/workflows
- `business-logic/notice-search-analytics-ops.md`、`operations.md`、`security.md`、`testing.md`
- `core-logic-index.md`、`core-logic-coverage-audit.md`

2026-07-07 reliability P2 spec 中关于 legacy Search/Growth projection path 的文字保留为历史记录，但在文件顶部增加本设计的 superseded 链接，避免被当作当前架构。

删除 `GET /api/posts` 作为 feed list route 的残留描述，但保留合法的 post detail/write 子路由。文档明确旧 virtual market implementation 已退休、统一 Market 仍支持 virtual goods，Content 不提供正文格式迁移。

应用和 Nacos 配置删除 publisher mode、fanout mode/transport、presence enabled 和已退休的内部 projection topics。compose 显式设置 worker inbox slot；Kafka bootstrap 显式创建 canonical owner topics 和 IM routed topic。

## 实施顺序

1. 先写失败测试和 retirement/ArchUnit guardrails。
2. 收紧 IM schema 与 projection version，并同步前端和 baseline SQL。
3. 将 room fanout 收敛为 routed-only，修正 presence 和 compose slots。
4. 收敛 owner event publisher 与下游 Kafka listeners，修复 IM policy application boundary和幂等 outbox。
5. 删除 Market address fallback、Growth sync/旧 pipeline 和 Trace MDC key。
6. 更新所有配置、部署脚本和 handbook。
7. 运行各模块 focused tests、`community-app` 全部 `*ArchTest`、前端 IM tests，再运行 backend 全量测试。

## 验收条件

- 仓库中不存在上述兼容类、selector、mode、fallback 和旧 MDC key。
- 所有 IM wire payload 显式 schema `1`，所有 projection payload 使用显式 owner version。
- room fanout 在 single 和三节点配置中只有 Kafka routed path，worker slot 唯一。
- local membership/presence 在 Redis 短暂失败、重复 Kafka delivery、heartbeat/disconnect 竞争和 target enqueue fail-once 后均可收敛。
- Content/Social/User 各只有一条 producer outbox -> Kafka 路径；Search/Growth/User reward/Hot-feed/Notice 各只有 canonical Kafka ingress。
- owner backbone 的 retry/DLQ 策略已实际接入 listener container，source/DLQ topics 均由 bootstrap 创建。
- User 和 Social policy changes 都能可靠进入 IM realtime projection，重复投递幂等。
- Market request replay 的既有正确性测试继续通过，地址 legacy fallback 测试改为 strict conflict。
- Growth task progress 没有同步 action surface。
- MDC 不再出现 legacy `traceId` key。
- handbook、Nacos、compose、Kafka bootstrap 与代码一致。
- focused tests、ArchUnit、前端 tests 和 backend 全量 Maven tests 全部通过。
