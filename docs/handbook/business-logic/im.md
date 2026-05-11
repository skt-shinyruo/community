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
- `PrivateMessageRejectedEvent`
- `RoomMessagePersistedEvent`
- `RoomMessageRejectedEvent`
- `RoomMemberChanged`
- `UserMessagingPolicyChanged`
- `UserBlockRelationChanged`

## 数据流

IM 的数据流分成 session、command、persisted event 和本地投影四层：

1. Session：浏览器先向 gateway 申请 session，拿到 ticket 和稳定 `wsUrl`，再通过 gateway 桥接到 realtime worker。这个阶段只建立连接能力，不写消息事实。
2. 私信 / 群聊 command：realtime 先用本地 policy / membership projection 做快速判定，再把合法发送转成 Kafka command。command 只是意图，最终事实仍在 im-core。
3. 持久化：im-core 按 conversationId 或 roomId + clientMsgId 做幂等，分配 seq，写消息和会话状态，然后发布 persisted / rejected event。
4. 在线推送：realtime 消费 persisted event，把消息或房间更新推给在线连接；离线或丢包的客户端必须回 HTTP history 补拉。
5. policy projection：user moderation 和 social block 变化通过主站 outbox / snapshot 更新 realtime 本地缓存，缓存只用于发送前快速判定，不是权威事实。

## Session bootstrap

`ImSessionApiController.openSession(...)`：

1. 浏览器带 Bearer access token 调 `POST /api/im/sessions`。
2. `ImSessionService.openSession(...)` 校验 JWT。
3. 解析 userId。
4. 根据 userId 或连接策略选择 realtime worker。
5. 生成 session ticket，包含 userId、workerId、过期时间等。
6. 返回 `OpenImSessionResponse`，包含稳定外部 `wsUrl` 和 ticket。

`wsUrl` 是客户端访问 gateway 的地址，不是直接访问 worker 的内部地址。

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
8. `PrivateMessageService.persist(...)` 计算 conversationId。
9. 按 `(conversationId, fromUserId, clientMsgId)` 做幂等。
10. 分配 conversation seq。
11. 写私信消息和会话状态。
12. 发布 `PrivateMessagePersistedEvent` 或 rejected event。
13. realtime 消费 persisted event，向收发双方在线连接推送消息/提交结果。
14. 离线或未收到推送的客户端通过 HTTP history 补拉。

重要语义：

- `sendAccepted` 不等于已落库。
- 消息权威状态以 im-core history 为准。
- `clientMsgId` 是消息级幂等键。

## 群聊和房间

房间管理由 im-core owning：

- `RoomMembershipService.createRoom(...)` 创建房间并把创建者加入。
- `joinRoom(...)` 加入房间。
- `leaveRoom(...)` 退出房间。
- 成员变化发布 `RoomMemberChanged`。

群消息发送：

1. 客户端发送 `sendRoomText` frame。
2. realtime 校验连接认证和基础字段。
3. 通过本地 membership projection 判断发送者是否在房间中；最终权威校验仍在 im-core。
4. 写 Kafka `SendRoomTextCommand`。
5. im-core 消费 command。
6. `RoomMessageService.persist(...)` 校验房间存在和发送者是成员。
7. 按 `(roomId, fromUserId, clientMsgId)` 做幂等。
8. 分配 room seq。
9. 写群消息。
10. 发布 `RoomMessagePersistedEvent`。
11. realtime 收到 event 后通过 `RoomFanoutCoalescer` / `RoomUpdateCoalescer` 向房间在线连接推送 state-only 更新。
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

- `UnreadService.listRoomUnread(...)` 计算房间未读。
- `listConversationUnread(...)` 计算私聊未读。
- `UnreadController.summary(...)` 返回未读汇总。

已读水位只做单调推进，不能倒退。

## Projection

Membership projection：

- im-core owns room membership。
- im-core internal endpoint 提供 membership snapshot。
- realtime 启动或刷新时拉 snapshot。
- `RoomMemberChanged` 事件增量更新 realtime 本地 `MembershipProjectionService` 和 `RoomLocalIndex`。

Policy projection：

- community-app owns user policy 和 block relation。
- realtime 通过 internal endpoint 拉 user policies 和 block relations snapshot。
- user moderation change 和 social block change 通过 Kafka 增量事件更新 realtime。
- realtime 发私信前使用本地 policy projection 做快速判定。

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
- 在线推送不是持久化保证。
- 重连后客户端应通过 HTTP history 和 read watermark 修复本地状态。

## 关键代码

IM gateway：

- `im.gateway.session.ImSessionApiController`
- `im.gateway.session.ImSessionService`
- `im.gateway.session.SessionTicketCodec`
- `im.gateway.shard.RendezvousWorkerSelector`
- `im.gateway.ws.ExternalImEdgeWebSocketHandler`
- `im.gateway.ws.ConnectTicketRouter`

Realtime：

- `im.realtime.ws.ImWebSocketHandler`
- `im.realtime.service.MessageCommandIngressService`
- `im.realtime.presence.ConnectionRegistry`
- `im.realtime.presence.WsConnection`
- `im.realtime.presence.RoomLocalIndex`
- `im.realtime.projection.PolicyProjectionService`
- `im.realtime.projection.MembershipProjectionService`
- `im.realtime.push.PrivatePushService`
- `im.realtime.push.SendResultPushService`
- `im.realtime.push.RoomFanoutCoalescer`

Core：

- `im.core.controller.ConversationController`
- `im.core.controller.RoomController`
- `im.core.controller.UnreadController`
- `im.core.controller.InternalRealtimeProjectionController`
- `im.core.service.PrivateMessageService`
- `im.core.service.RoomMessageService`
- `im.core.service.RoomMembershipService`
- `im.core.service.UnreadService`
- `im.core.kafka.CommandConsumers`
- `im.core.outbox.ImMessageOutboxEnqueuer`

Community app projection：

- `im.application.ImPolicySnapshotApplicationService`
- `im.projection.ImPolicySnapshotController`
- `im.projection.ImPolicyOutboxEnqueuer`
- `im.projection.ImPolicyKafkaOutboxHandler`
