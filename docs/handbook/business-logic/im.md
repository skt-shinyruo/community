# IM 消息业务逻辑

IM 业务不是 `community-app` 内的普通包，而是拆成 gateway、realtime、core 三个 deployable/模块协作。理解 IM 时要区分 session 接入、在线连接、消息权威状态和投影状态。

## Owner / SSOT

- `community-im-gateway` owns 外部 IM session bootstrap、JWT 校验、session ticket、worker 选择和 `/ws/im` 外部桥接。
- `im-realtime` owns WebSocket 连接态、在线用户连接、房间本地索引、Kafka command 生产、在线推送和本地 policy/membership projection。
- `im-core` owns 私聊会话、私聊消息、房间、房间成员、群消息、顺序号、已读水位和未读查询。
- `community-app` owns 用户处罚和拉黑主事实，并给 realtime 提供 user policy/block relation snapshot 和增量事件。

## 入口

IM gateway：

- `POST /api/im/sessions`
- 外部 WebSocket `/ws/im`

IM core HTTP：

- `GET /api/im/conversations`
- `GET /api/im/conversations/{conversationId}/messages`
- `POST /api/im/conversations/{conversationId}/read`
- `POST /api/im/rooms`
- `POST /api/im/rooms/{roomId}/join`
- `POST /api/im/rooms/{roomId}/leave`
- `GET /api/im/rooms/{roomId}/messages`
- `POST /api/im/rooms/{roomId}/read`
- `GET /api/im/unread/summary`
- `GET /internal/im/realtime/projections/room-memberships`

community-app internal snapshot：

- `GET /internal/im/realtime/projections/user-policies`
- `GET /internal/im/realtime/projections/block-relations`

Kafka command/event：

- `SendPrivateTextCommand`
- `SendRoomTextCommand`
- `PrivateMessagePersistedEvent`
- `PrivateMessageCommittedEvent`
- `PrivateMessageRejectedEvent`
- `RoomMessagePersistedEvent`
- `RoomMessageCommittedEvent`
- `RoomMessageRejectedEvent`
- `RoomMemberChanged`
- `UserMessagingPolicyChanged`
- `UserBlockRelationChanged`

契约版本：

- `im-common` 下 command、event、projection snapshot 和 WebSocket frame 都是跨 deployable JSON contract。
- command、event、projection 和 WebSocket frame 都显式写出数值型 `schemaVersion: 1`。
- 读取时只接受 JSON integer `1`；字段缺失、`null`、非数值、非正数或未来版本都会失败。
- v1 consumer 忽略未知 JSON 字段；必填字段、字段名、字段类型或语义变化必须设计新的显式契约。
- Kafka topic 与 WebSocket `type` 是路由契约，不兼容语义不能复用原 topic/type。

## 数据流

IM 的数据流分成 session、command、消息事实 event、发送结果 event 和本地投影五层：

1. Session：浏览器先向 gateway 申请 session，拿到 ticket 和稳定 `wsUrl`，再通过 gateway 桥接到 realtime worker。这个阶段只建立连接能力，不写消息事实。
2. 私信 / 群聊 command：realtime 先用本地 policy / membership projection 做快速判定，再把合法发送转成 Kafka command。command 只是意图，最终事实仍在 im-core。
3. 消息事实：im-core 按 conversationId 或 roomId + clientMsgId 做消息级幂等，分配 seq，写消息和会话状态。新消息只发布一次 persisted event；事件身份来自 `messageId` 或 `roomId + seq`。
4. 发送结果：每个进入 core 的发送尝试都按 `requestId + clientMsgId + fromUserId` 发布 committed 或 rejected event，用于给发送端回执。
5. policy projection：user moderation 和 social block 变化通过主站 outbox / snapshot 更新 realtime 本地缓存，缓存只用于发送前快速判定，不是权威事实。

## Session bootstrap

`ImSessionApiController.openSession(...)`：

1. 浏览器带 Bearer access token 调 `POST /api/im/sessions`。
2. `ImSessionService.openSession(...)` 校验 JWT。
3. 解析 userId。
4. 根据 userId 或连接策略选择 realtime worker。
5. 生成 session ticket，包含 userId、workerId、过期时间等。
6. 返回 `OpenImSessionResponse`，包含稳定外部 `wsUrl` 和 ticket。

`wsUrl` 是客户端访问 gateway 的地址，不是直接访问 worker 的内部地址。`PublicWsUrlFactory` 只接受显式配置的绝对 `ws` / `wss` URI 且必须带 authority；它不会根据请求 Host 或 `X-Forwarded-*` 拼接 URL，避免 Host header 注入把 ticket 引到非预期域名。

## WebSocket 连接

1. 客户端连接 `/ws/im`。
2. `community-im-gateway` 接收首帧 connect 和 ticket。
3. `ConnectTicketRouter` 根据 ticket 找到 worker。
4. gateway 建立到 worker 的内部 WebSocket bridge。
5. `im-realtime` 的 `ImWebSocketHandler` 校验 ticket。
6. 创建 `WsConnection` 并注册到 `ConnectionRegistry`。
7. 连接绑定 userId、sessionId、traceId、workerId。
8. realtime 从 projection 中绑定该用户已加入的房间到本地 `RoomLocalIndex`。

连接成功只代表 realtime 接入完成，不代表任何消息已发送或持久化。

桥接语义：

- `ExternalImEdgeWebSocketHandler` 要求首帧在 `firstFrameTimeoutMs` 内到达，且首帧必须是文本 connect frame；缺失、超时、格式错误、ticket 无效或 worker 不可用都会返回 reject frame 后关闭连接。
- `InternalWorkerBridgeFactory` 用 Reactor Netty 连接选中的 worker，并把外部握手里的 `traceparent` 透传到内部 worker 连接。IM gateway 只转发文本帧；worker 返回非文本帧会被当作内部 bridge 错误并关闭外部连接。

## 私信发送

客户端发送 `sendPrivateText` frame：

1. realtime 校验连接已认证。
2. 校验 toUserId、clientMsgId、content。
3. `PolicyProjectionService.canSendPrivateMessage(...)` 判断发送权限：
   - 发送者未被禁言/封禁。
   - 目标用户存在且可接收。
   - 双方不存在拉黑关系。
4. 判定失败时，realtime 直接推 reject。
5. 判定通过后，`MessageCommandIngressService.sendPrivate(...)` 写 Kafka `SendPrivateTextCommand`。
6. realtime 可向发送端返回 accepted/ack，表示 command 已接收。
7. im-core `CommandConsumers.onPrivateText(...)` 消费 command。
8. `PrivateMessageApplicationService.persist(...)` 计算 conversationId 并先按 `(conversationId, fromUserId, clientMsgId)` 查幂等。
9. 幂等命中时返回既有消息事实，不重复发布消息事实 event，只发布当前 request 的 `PrivateMessageCommittedEvent`。
10. 幂等未命中时，im-core 回源 `community-app` private message owner decision 做最终校验。
11. owner decision 拒绝时发布 `PrivateMessageRejectedEvent`，不写私信表，Kafka command 视为业务完成。
12. owner decision 允许时，分配 conversation seq，写私信消息和会话状态。
13. 发布 `PrivateMessagePersistedEvent` 和当前 request 的 `PrivateMessageCommittedEvent`。
14. realtime 消费 persisted event 向收发双方在线连接推送消息，消费 committed / rejected event 向发送端推送提交结果。
15. 离线或未收到推送的客户端通过 HTTP history 补拉。

重要语义：

- `sendAccepted` 不等于已落库。
- 消息权威状态以 im-core history 为准。
- `clientMsgId` 是消息级幂等键。
- `requestId` 是发送尝试标识的一部分，用于 committed / rejected 回执；同一 `clientMsgId` 的不同 request 不会创建重复消息事实，但会得到各自的发送结果。
- realtime policy projection 只做快速拒绝；未落库的新私信必须由 im-core 回源 owner decision 做最终校验。

## 群聊和房间

房间管理由 im-core owning：

- `RoomApplicationService.createRoom(...)` 创建房间并把创建者加入，房间成员规则由 `RoomMembershipDomainService` 承担。
- `joinRoom(...)` 加入房间。
- `leaveRoom(...)` 退出房间。
- 成员变化发布 `RoomMemberChanged`。

群消息发送：

1. 客户端发送 `sendRoomText` frame。
2. realtime 校验连接认证和基础字段。
3. 通过本地 membership projection 判断发送者是否在房间中；最终权威校验仍在 im-core。
4. 写 Kafka `SendRoomTextCommand`。
5. im-core 消费 command。
6. `RoomMessageApplicationService.persist(...)` 校验房间存在和发送者是成员；成员和 seq 规则由 `RoomMessageDomainService` 承担。
7. 按 `(roomId, fromUserId, clientMsgId)` 做幂等。
8. 分配 room seq。
9. 写群消息。
10. 新消息发布 `RoomMessagePersistedEvent`，当前 request 发布 `RoomMessageCommittedEvent`；幂等命中时只发布当前 request 的 committed event。
11. realtime 收到 persisted event 后通过 `RoomFanoutCoalescer` / `RoomUpdateCoalescer` 向房间在线连接推送 state-only 更新，同时把 committed / rejected 结果推给发送端。
12. 客户端收到 room updated 后通过 HTTP 拉取消息。

群聊推送不一定携带完整消息；恢复依赖 im-core history。

## 会话、历史、已读和未读

私聊 HTTP：

- `ConversationController.listConversations(...)` 查询会话列表。
- `listMessages(...)` 按 conversationId 和 seq 分页读取历史。
- `markRead(...)` 推进私聊 read watermark。

群聊 HTTP：

- `RoomController.listMessages(...)` 读取房间消息。
- `markRead(...)` 推进房间 read watermark。

未读：

- `UnreadApplicationService.listRoomUnread(...)` 计算房间未读。
- `UnreadApplicationService.listConversationUnread(...)` 计算私聊未读。
- `UnreadController.summary(...)` 返回未读汇总。
- 未读 limit 规范化和 repository 委托规则由 `UnreadDomainService` 承担。

已读水位只做单调推进，不能倒退。

## Projection

Membership projection：

- im-core owns room membership。
- im-core internal endpoint 提供 membership snapshot。
- `ProjectionSyncCoordinator` 在 realtime `ApplicationReadyEvent` 后拉 membership 和 policy snapshot；刷新期间把 readiness 置为 false，两个 snapshot 都成功后才恢复 ready。
- connect、私信发送和群消息发送都会先检查 projection ready。未 ready 时，connect 返回 `projection_not_ready` 并关闭，发送 frame 返回 reject。
- `RoomMemberChanged` 事件增量更新 realtime 本地 `MembershipProjectionService` 和 `RoomLocalIndex`。
- `RoomLocalIndex` 只保存当前 worker 进程内的 roomId -> connectionId 集合，用于房间在线 fanout 和 room 连接数指标；它不是 membership 权威状态。
- membership snapshot 的 boxed `snapshotHighWatermark` 必填、非负并允许为 `0`；entry / delta 的 `version` 必须是显式正数。`occurredAtEpochMillis` 只用于观测，不能派生版本。realtime 只接受同一 `(roomId,userId)` 上版本更大的状态；旧 snapshot 或乱序 `RoomMemberChanged` 不会回滚 membership 或本机 room index。
- membership `version` 是 im-core owner 的持久逻辑时钟：`im_membership_version_counter` 分配版本，active fact 写入 `im_room_member.version`，离开房间写入 `im_membership_version_log` 并推进 counter；snapshot entry、`RoomMemberChanged.version` 和 `snapshotHighWatermark` 都来自这个同一版本域。
- 房间 fanout 只使用 Redis-backed distributed presence、共享 `RoomPersistedOwnerConsumer` 和 Kafka fixed-partition worker inbox。`RoomFanoutOwnerService` 在 listener 调用栈内规划 route，并把 state-only command 同步写入目标 worker partition。
- `RoomLocalIndex` 和连接对象是本进程权威状态；Redis activate/deactivate 失败时 `RoomLocalPresenceService` 保留 pending room 重试，不回滚本地 join/leave。
- route planning 或任一 target publish 失败会抛回 listener，让原始 room persisted event 重试或进入 DLQ；空 target set 表示当前没有活跃 worker，不重试。
- 每个 worker 必须配置唯一 `im.room-fanout.worker-inbox-slot` 并通过 discovery metadata `roomFanoutInboxSlot` 暴露。single 使用 `0`，cluster 使用 `0/1/2`。
- target delivery 是 state-idempotent at-least-once，不是跨重启 exactly-once：进程内以有界 `sourceEventId` cache 抑制重复，重启后由 room/seq max coalescing 收敛，客户端最终按 seq 和 history backfill 修复状态。

Policy projection：

- community-app owns user policy 和 block relation。
- realtime 通过 internal endpoint 拉 user policies 和 block relations snapshot。
- user moderation change 和 social block change 通过 Kafka 增量事件更新 realtime。
- realtime 发私信前使用本地 policy projection 做快速判定。
- user policy 以 `userId` 为 projection key，block relation 以 `(blockerUserId,blockedUserId)` 为 projection key。snapshot watermark 必填且非负，entry / delta version 必须是正数 owner version；refresh 与 Kafka delta 并发时按 key 版本决胜。
- user policy `version` 是 user owner 的持久逻辑时钟：`user_policy_version_counter` 分配版本，`user.policy_version` 保存当前用户治理事实版本，`UserPolicyChangedPayload.version` 和 user policy snapshot entry / high-watermark 使用同一 counter 域。
- block relation `version` 是 social owner 的持久逻辑时钟：`social_block_version_counter` 分配版本，active fact 写入 `social_block.version`，取消拉黑写入 `social_block_version_log` 并推进 counter；`BlockPayload.version`、block snapshot entry 和 high-watermark 使用同一 counter 域。
- `occurredAtEpochMillis` 不参与版本决策；版本只能由上述 owner 持久 counter 单调推进，不能使用 snapshot time 或进程内 counter 生成。

projection 不是权威事实；启动和异常恢复依赖 snapshot 重新构建。

## 网关和边缘

`community-gateway` 是浏览器默认入口：

- 路由 `/api/**` 到主业务或 IM HTTP。
- 路由 `/api/im/sessions` 和 `/ws/im` 到 IM gateway。
- 处理 CORS、安全、访问日志、限流和流量策略。
- 对 WebSocket 做透明代理和 worker bridge。

`community-im-gateway` 专注 IM session 与 WS edge，避免浏览器直接接触 realtime worker 内部地址。

## 失败语义

- session ticket 过期或 worker 不可用会导致 WS 连接失败。
- realtime policy projection 不允许发送时，私信在进入 Kafka 前被拒绝。
- im-core 校验失败时，发布 rejected event，realtime 推送发送失败。
- Kafka command accepted 后，客户端仍需等待 committed/rejected 或通过 history 回查。
- persisted event 是消息事实事件，event id 分别形如 `im:pf:<messageId>` 和 `im:rf:<roomId>:<seq>`。
- committed / rejected 是发送结果事件，event id 分别形如 `im:psr:<attemptHash>` 和 `im:rsr:<attemptHash>`，attemptHash 来自 `fromUserId + requestId + clientMsgId`。
- 在线推送不是持久化保证。
- 重连后客户端应通过 HTTP history 和 read watermark 修复本地状态。

## 关键代码

IM gateway：

- `im.gateway.session.ImSessionApiController`
- `im.gateway.session.ImSessionService`
- `im.gateway.session.PublicWsUrlFactory`
- `im.gateway.session.SessionTicketCodec`
- `im.gateway.shard.RendezvousWorkerSelector`
- `im.gateway.ws.ExternalImEdgeWebSocketHandler`
- `im.gateway.ws.ConnectTicketRouter`
- `im.gateway.ws.InternalWorkerBridgeFactory`
- `im.gateway.ws.ImGatewayFrameCodec`

Realtime：

- `im.realtime.ws.ImWebSocketHandler`
- `im.realtime.service.MessageCommandIngressService`
- `im.realtime.presence.ConnectionRegistry`
- `im.realtime.presence.WsConnection`
- `im.realtime.presence.RoomLocalIndex`
- `im.realtime.presence.RoomLocalPresenceService`
- `im.realtime.presence.RedisRoomPresenceDirectory`
- `im.realtime.fanout.RoomPersistedOwnerConsumer`
- `im.realtime.fanout.RoomFanoutOwnerService`
- `im.realtime.fanout.RoomFanoutRoutingService`
- `im.realtime.fanout.KafkaRoomFanoutDispatcher`
- `im.realtime.fanout.RoomFanoutTargetConsumer`
- `im.realtime.fanout.RoomFanoutTargetService`
- `im.realtime.projection.ProjectionSyncCoordinator`
- `im.realtime.projection.PolicyProjectionService`
- `im.realtime.projection.MembershipProjectionService`
- `im.realtime.push.PrivatePushService`
- `im.realtime.push.SendResultPushService`
- `im.realtime.push.RoomFanoutCoalescer`
- `im.realtime.push.RoomUpdateCoalescer`

Core：

- `im.core.controller.ConversationController`
- `im.core.controller.RoomController`
- `im.core.controller.UnreadController`
- `im.core.controller.InternalRealtimeProjectionController`
- `im.core.application.ConversationApplicationService`
- `im.core.application.PrivateMessageApplicationService`
- `im.core.application.RoomApplicationService`
- `im.core.application.RoomMessageApplicationService`
- `im.core.application.UnreadApplicationService`
- `im.core.domain.service.PrivateMessageDomainService`
- `im.core.domain.service.RoomMessageDomainService`
- `im.core.domain.service.RoomMembershipDomainService`
- `im.core.kafka.CommandConsumers`
- `im.core.outbox.ImMessageOutboxEnqueuer`

Community app projection：

- `im.application.ImPolicySnapshotApplicationService`
- `im.controller.ImPolicySnapshotController`
- `im.application.ImPolicyProjectionApplicationService`
- `im.application.ImPolicyProjectionOutboxPort`
- `im.infrastructure.event.ImPolicyBackboneKafkaListener`
- `im.infrastructure.event.JdbcImPolicyProjectionOutboxAdapter`
- `im.infrastructure.event.ImPolicyKafkaOutboxHandler`
- `im.application.ImPolicyEventDispatchApplicationService`
- `im.infrastructure.event.ImPolicyEventKafkaSenderAdapter`
