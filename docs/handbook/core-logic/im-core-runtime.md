# IM Core Runtime 运行时

本文记录 `im-common`、`im-core` 和 `im-realtime` 当前唯一运行时：严格 schema v1、显式 projection 逻辑版本、Redis presence 与 Kafka worker inbox fanout。

## Schema 和 session

IM command、event、projection 以及 WebSocket frame 都写出数值型 `schemaVersion: 1`。读取规则由 `ImContractVersions`、`ImSchemaVersion`、`ImSchemaVersionDeserializer` 和 `ImJsonContract` 统一定义：

- 只接受 JSON integer `1`。
- 字段缺失、`null`、非数值、非正数或未来版本都失败。
- v1 内未知字段会忽略。
- WebSocket 入口由 `ImFrameCodec` 转成 protocol reject。
- Kafka 反序列化失败进入 error handler，最终写源 topic 的 `.dlq`；非法 command 不产生 persisted、committed 或 rejected 业务事件。

`OpenImSessionResponse` 是 session bootstrap 的同步 HTTP helper，只返回 `sessionId`、`wsUrl`、`ticket` 和 `expiresAtEpochMillis`；它不属于上述四类版本化 wire contract。

## Projection 版本

membership、user policy 和 block relation 的 entry / delta 都要求显式正数 `version`。该值来自 owner 持久逻辑时钟：

- user policy：`user_policy_version_counter` / `user.policy_version`
- social block：`social_block_version_counter` / `social_block.version` 及删除日志
- room membership：`im_membership_version_counter` / `im_room_member.version` 及删除日志

`occurredAtEpochMillis` 只表示可观测发生时间，不能派生版本。snapshot 的 `snapshotHighWatermark` 是必填 boxed `Long`，必须非负且允许为 `0`；snapshot entry 的有效版本取显式正数 entry version 与 watermark 的较大值。realtime 只应用同一 projection key 上更大的版本。

## 私信持久化

私信 Kafka command 进入 `CommandConsumers.onPrivateText`，再调用 `PrivateMessageApplicationService.persist`：

1. `PrivateMessageDomainService.prepare` 校验 `clientMsgId`、内容长度和规范 conversation ID。
2. 按 `(conversationId, fromUserId, clientMsgId)` 查询既有事实。
3. 只在幂等 miss 时通过 `PrivateMessagePolicyVerifier` 查询 owner policy。
4. 锁定 conversation seq，分配递增 seq 并插入消息。
5. 单调推进发送方 read watermark，并更新双方 inbox projection。

## 房间消息持久化

房间 Kafka command 进入 `CommandConsumers.onRoomText`，再调用 `RoomMessageApplicationService.persist`：

1. 校验 `clientMsgId`、内容和房间存在性。
2. `RoomMembershipDomainService` 以 im-core membership authority 校验成员。
3. 锁定 room seq 并分配递增 seq。
4. 按 `(roomId, fromUserId, clientMsgId)` 幂等复用或插入事实。
5. 单调推进发送方 read watermark，并更新成员 inbox projection。

## 幂等和 outbox

幂等命中复用持久事实，不重复发布 persisted fact；每个已接受的发送尝试仍按本次 request / client message / sender 组合发布 committed result。

`ImMessageOutboxEnqueuer` 负责：

- `im.event.private-persisted`、`im.event.room-persisted`：只在新事实创建时入队。
- `im.event.private-committed`、`im.event.room-committed`：每个已接受尝试入队。
- `im.event.private-rejected`、`im.event.room-rejected`：application 内业务拒绝结果。
- `im.event.room-member-changed`：部署配置使用 Kafka publisher 发布显式 membership version。

序列化或 Kafka publish 失败按 outbox/Kafka 重试语义恢复。

## 未读和 read watermark

`UserInboxRepository` 维护未读投影：

- 发送方 outgoing message 将自己的 `last_read_seq` 推到本条 seq。
- conversation / room watermark 只能单调递增。
- mark read 先校验可见性或 membership，再更新 read state 和 inbox。
- `unread = max(0, lastSeq - lastReadSeq)`。

`UnreadApplicationService` 读取投影，不重新扫描消息事实。

## Fanout routing 和 presence

当前 room fanout 只有 Redis presence、共享 owner consumer 和 Kafka worker inbox：

1. `RoomLocalIndex` 是本进程 room -> connection ID 的权威本地状态。
2. `RoomLocalPresenceService` 在第一个本地连接加入时 activate，最后一个离开时 deactivate；Redis 操作失败会保留 pending room 并重试，本地连接与索引状态不回滚。
3. `RedisRoomPresenceDirectory` 维护分布式 room -> worker set，并用 worker liveness 过滤过期 worker。
4. `RoomPersistedOwnerConsumer` 以共享 consumer group 消费 persisted event，调用 `RoomFanoutOwnerService`、`RoomFanoutRoutingService` 和 `RoomFanoutPlanner`。
5. `KafkaRoomFanoutDispatcher` 将 state-only `RoomFanoutCommand` 写入 `im.command.room-fanout-routed` 的目标 worker partition。
6. `RoomFanoutTargetConsumer` 只消费本 worker 的固定 partition，调用 `RoomFanoutTargetService` 后进入本地 `RoomFanoutCoalescer`。

`im.room-fanout.worker-inbox-slot` 必填且必须唯一；single topology 使用 `0`，cluster 三个 worker 使用 `0/1/2`。服务发现 metadata `roomFanoutInboxSlot` 必须与配置一致，重复 worker ID 或 slot 会被拒绝。

owner route 或任一 command publish 失败会抛回 Kafka listener，触发源 persisted event 重试 / DLQ；空 target set 表示当前没有活跃 worker，只记录指标。

target delivery 是 state-idempotent at-least-once，不是跨重启 exactly-once。进程内以有界 `sourceEventId` cache 抑制重复；重启后由 `RoomFanoutCoalescer` 对 room/seq 取最大值收敛。客户端收到 `roomUpdatedBatch` 后仍按 seq 拉 history。

本地 presence 只是路由信号，不是 membership authority。权限仍由 im-core membership 和 realtime membership projection 保护。

## 关键代码

- schema / session：`ImContractVersions`、`ImSchemaVersion`、`ImSchemaVersionDeserializer`、`ImJsonContract`、`ImUnsupportedSchemaVersionException`、`OpenImSessionResponse`、`ImFrameCodec`、`ImWebSocketHandler`
- command / outbox：`CommandConsumers`、`KafkaConfig`、`ImMessageOutboxEnqueuer`、persisted / committed / rejected event、`RoomMemberChanged`
- private persist：`PrivateMessageApplicationService`、`PrivateMessageDomainService`、`PrivateMessageRepository`、`PrivateMessagePolicyVerifier`、`SeqAllocator`
- room persist：`RoomMessageApplicationService`、`RoomMessageDomainService`、`RoomMembershipDomainService`、`RoomApplicationService`、`RoomMessageRepository`
- unread：`UserInboxRepository`、`ConversationReadStateRepository`、`RoomReadStateRepository`、`UnreadApplicationService`
- fanout / presence：`RoomPersistedOwnerConsumer`、`RoomFanoutOwnerService`、`RoomFanoutRoutingService`、`RoomFanoutPlanner`、`KafkaRoomFanoutDispatcher`、`RealtimeWorkerDirectory`、`RoomFanoutTargetConsumer`、`RoomFanoutTargetService`、`RoomFanoutCoalescer`、`RoomUpdateCoalescer`、`RoomLocalIndex`、`RoomLocalPresenceService`、`RedisRoomPresenceDirectory`、`RoomPresenceHeartbeat`
