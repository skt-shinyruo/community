# IM Core Runtime 运行时

本文记录当前 IM 核心实时链路的已实现行为。这个主题跨 `im-common`、`im-core`、`im-realtime`，用于替代按旧 service name 导航的零散入口：读者应从运行时机制理解消息写入、事件、未读和房间 fanout，而不是按过期服务名猜测代码位置。

## Schema 和会话兼容

IM contract 当前版本是 `ImContractVersions.CURRENT_SCHEMA_VERSION = 1`。命令、事件、projection 和 WebSocket frame 的 `schemaVersion` 都走同一套规则：

- `schemaVersion <= 0` 按当前 v1 读取，兼容没有版本字段的旧消息。
- 写出当前 v1 时，`@ImSchemaVersion` 通过 `CurrentSchemaVersionFilter` 省略字段。
- `schemaVersion > 1` 会抛出 `ImUnsupportedSchemaVersionException`。
- WebSocket 入口用 `ImFrameCodec` 把未来版本转成 protocol reject。
- Kafka 命令反序列化失败进入 `KafkaConfig` 的 error handler；当前 DLQ topic 是原 topic 加 `.dlq`。future command 不会产生 persisted、committed 或 rejected IM 事件。

`@ImJsonContract` 仍会忽略未知字段，所以同版本的新增旁路字段不会破坏读取。

`OpenImSessionResponse` 当前只返回 `sessionId`、`wsUrl`、`ticket`、`expiresAtEpochMillis`，不携带 `schemaVersion`；连接建立后的 WebSocket frame 才进入上述版本规则。

## 私信持久化

私信 Kafka 命令进入 `CommandConsumers.onPrivateText`，再调用 `PrivateMessageApplicationService.persist`。

写入顺序是：

1. `PrivateMessageDomainService.prepare` 校验 `clientMsgId`、非空内容、长度，并用 `ConversationIdSupport.conversationId(fromUserId, toUserId)` 推导会话 id；传入 id 不匹配会失败。
2. 先按 `(conversationId, fromUserId, clientMsgId)` 查询既有事实。
3. 只有 idempotency miss 时才调用 `PrivateMessagePolicyVerifier`；当前实现是 `OwnerApiPrivateMessagePolicyVerifier`，通过 owner API 查询私信策略。
4. 确保 conversation 存在并锁定当前 seq，`SeqAllocator.nextConversationSeq` 分配递增 seq。
5. 插入 `PrivateMessageRecord`。
6. 通过 `ConversationReadStateRepository.updateLastReadSeqMax` 把发送方 read watermark 推到本条 seq。
7. `UserInboxRepository.applyPrivateMessage` 刷新双方 inbox 投影。

## 房间消息持久化

房间消息 Kafka 命令进入 `CommandConsumers.onRoomText`，再调用 `RoomMessageApplicationService.persist`。

写入顺序是：

1. `RoomMessageDomainService.persist` 校验 `clientMsgId`、非空内容和长度。
2. `RoomRepository.exists(roomId)` 校验房间存在。
3. `RoomMembershipDomainService.isMember(roomId, fromUserId)` 做成员权限判断；这里依赖 `im-core` 的 membership authority，不依赖 realtime 本地 presence。
4. 锁定 room seq，`SeqAllocator.nextRoomSeq` 分配递增 seq。
5. 按 `(roomId, fromUserId, clientMsgId)` 做幂等查询；命中则返回既有事实。
6. 插入 `RoomMessageRecord`。
7. 通过 `RoomReadStateRepository.updateLastReadSeqMax` 把发送方 read watermark 推到本条 seq。
8. `UserInboxRepository.applyRoomMessage` 刷新房间成员 inbox 投影。

## 幂等和 owner 决策

私信幂等 key 是 `(conversationId, fromUserId, clientMsgId)`；房间幂等 key 是 `(roomId, fromUserId, clientMsgId)`。

幂等命中时复用已持久化事实，不重新发布 persisted fact。已接受的发送尝试仍会发布 committed 事件，用本次 `requestId`、`clientMsgId`、`fromUserId` 归一化 send-result event id，让发送端能收到本次尝试的结果。

私信的 owner policy 只在 idempotency miss 后调用；重复请求不会再次访问 owner policy。房间消息不调用 owner policy，权限来自 `im-core` 的 room membership。

## Outbox 事件

`ImMessageOutboxEnqueuer` 写当前这些 outbox topic：

- persisted fact：`im.event.private-persisted`、`im.event.room-persisted`，只在新事实创建时写入。
- committed result：`im.event.private-committed`、`im.event.room-committed`，每个已接受发送尝试都会写入。
- rejected result：`im.event.private-rejected`、`im.event.room-rejected`，由 `CommandConsumers` 在命令进入 application 后发生业务失败时写入。
- membership：`im.event.room-member-changed`，由 `RoomApplicationService` 通过 `AfterCommitExecutor` 在事务提交后发布；当前可由 `KafkaRoomMemberChangePublisher` 写 outbox，也可由默认 `NoopRoomMemberChangePublisher` 跳过发布。

序列化失败会让 outbox 写入失败。Kafka listener 的业务失败会交给 `KafkaConfig` 重试 / DLQ；私信 owner policy denial 当前写 rejected 后直接返回，不再抛给 DLQ。

## 未读和 read watermark

未读投影在 `UserInboxRepository` 下维护，MyBatis 实现是 `MyBatisUserInboxRepository`。

- 私信和房间发送方的 outgoing message 会把自己的 `last_read_seq` 推到消息 seq，因此发送方不因自己发出的消息增加未读。
- `ConversationReadStateRepository` 和 `RoomReadStateRepository` 的 MyBatis 实现使用 `updateLastReadSeqMax`，只允许 watermark 单调递增。
- 用户显式 mark read 时，application 先校验可见性 / membership，再更新 read state 和 inbox 投影。
- 当前未读计算语义是 `unread = lastSeq - lastReadSeq`，落库时用 `greatest(0, ...)` 防止负数。

`UnreadApplicationService` 只读取已投影的 conversation / room unread 列表，不重新扫描消息事实。

## Fanout routing 和 presence

`im-realtime` 的 room 推送分三层：

1. `RoomLocalIndex` 记录本进程 room -> connection id，用于本机推送。
2. `RoomLocalPresenceService` 在本机第一个连接加入某 room 时调用 `RoomPresenceDirectory.activate`，最后一个连接离开时调用 `deactivate`。
3. `RedisRoomPresenceDirectory` 维护分布式 room -> worker set，并用 worker liveness key 过滤过期 worker；没有 Redis 或未启用 presence 时回退 `NoopRoomPresenceDirectory`。

本地 presence 只是路由信号，不是 membership authority。成员权限仍由 `im-core` 的 `RoomMembershipDomainService` 和 realtime 的 membership projection 共同保护入口；presence 不决定用户是否是房间成员。

room fanout 当前模式：

- `legacy`：`RoomPersistedLegacyConsumer` 消费 room persisted，直接让本 worker 的 `RoomFanoutCoalescer` 按本地连接推送。
- `shadow`：legacy 仍真实推送；`RoomPersistedOwnerConsumer` 只做 owner-side 路由规划和指标，不向目标 worker 发命令。
- `routed`：owner consumer 通过 `RoomFanoutRoutingService`、`RoomFanoutPlanner` 和 distributed presence 生成每个活跃 worker 的 route，再由 `KafkaRoomFanoutDispatcher` 或 `HttpRoomFanoutDispatcher` 发 `RoomFanoutCommand`。

routed 模式启动时要求 distributed presence；否则 `RoomFanoutConfiguration.RoomFanoutRoutedPresenceGuard` fail fast。Kafka transport 还会校验 `worker-inbox-slot`。`RealtimeWorkerDirectory` 从服务发现 metadata 解析 worker endpoint，并拒绝重复 worker id 或重复 inbox slot。

target 侧用 `RoomFanoutTargetService` 校验目标 worker、去重 `sourceEventId`，再把 room/seq 交给本地 `RoomFanoutCoalescer`。Kafka target consumer 只对 `ACCEPTED` / `DUPLICATE` ack；`INVALID` 或 `WRONG_TARGET` 抛异常，交给 Kafka retry / DLQ。HTTP target controller 对 accepted / duplicate 返回 202，wrong target 返回 409，invalid 返回 400。owner flush 中路由或 dispatch 失败会保留最新 room update 待下轮重试；空 target set 只记录指标。

实际 WebSocket 推送由 `RoomFanoutCoalescer` 和 `RoomUpdateCoalescer` 合并：room persisted 事件只携带 `roomId` / `seq` 状态，客户端收到 `roomUpdatedBatch` 后按 seq 拉历史。私信 persisted 事件则通过 `PrivatePushService` 直接推送含内容的 `privateMessage` frame。committed / rejected 结果由 `SendResultPushService` 推给发送用户。

## 关键代码

- schema / session: `ImContractVersions`, `CurrentSchemaVersionFilter`, `ImSchemaVersion`, `ImJsonContract`, `ImUnsupportedSchemaVersionException`, `OpenImSessionResponse`, `ImFrameCodec`, `ImWebSocketHandler`.
- command / outbox: `CommandConsumers`, `KafkaConfig`, `ImMessageOutboxEnqueuer`, `PrivateMessageCommittedEvent`, `RoomMessageCommittedEvent`, `PrivateMessageRejectedEvent`, `RoomMessageRejectedEvent`, `RoomMemberChanged`.
- private persist: `PrivateMessageApplicationService`, `PrivateMessageDomainService`, `PrivateMessageRepository`, `ConversationIdSupport`, `PrivateMessagePolicyVerifier`, `OwnerApiPrivateMessagePolicyVerifier`, `SeqAllocator`.
- room persist: `RoomMessageApplicationService`, `RoomMessageDomainService`, `RoomMembershipDomainService`, `RoomApplicationService`, `RoomMessageRepository`, `KafkaRoomMemberChangePublisher`, `NoopRoomMemberChangePublisher`, `AfterCommitExecutor`.
- unread: `UserInboxRepository`, `MyBatisUserInboxRepository`, `ConversationReadStateRepository`, `RoomReadStateRepository`, `MyBatisConversationReadStateRepository`, `MyBatisRoomReadStateRepository`, `UnreadApplicationService`, `UnreadDomainService`.
- realtime fanout / presence: `EventConsumers`, `RoomPersistedLegacyConsumer`, `RoomPersistedOwnerConsumer`, `RoomFanoutConfiguration`, `RoomFanoutOwnerCoalescer`, `RoomFanoutRoutingService`, `RoomFanoutPlanner`, `KafkaRoomFanoutDispatcher`, `HttpRoomFanoutDispatcher`, `RealtimeWorkerDirectory`, `RoomFanoutTargetConsumer`, `RoomFanoutTargetController`, `RoomFanoutTargetService`, `RoomFanoutTargetResult`, `RoomFanoutCoalescer`, `RoomUpdateCoalescer`, `RoomLocalIndex`, `RoomLocalPresenceService`, `RoomPresenceDirectory`, `RedisRoomPresenceDirectory`, `NoopRoomPresenceDirectory`, `RoomPresenceHeartbeat`, `SendResultPushService`, `PrivatePushService`.
