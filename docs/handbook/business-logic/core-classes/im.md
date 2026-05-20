# IM 核心类细分

本文是 [../im.md](../im.md) 的类级补充。IM 不是一个包，而是 gateway、realtime、core 和 policy projection 的协作面。

## 先读顺序

1. `ImSessionService` / `PublicWsUrlFactory`
2. `ExternalImEdgeWebSocketHandler` / `InternalWorkerBridgeFactory`
3. `ImWebSocketHandler` / `ProjectionSyncCoordinator`
4. `MessageCommandIngressService`
5. `PrivateMessageService` / `RoomMessageService` / `RoomMembershipService`
6. `ImPolicySnapshotApplicationService`

## Gateway

| 类 | 核心职责 |
| --- | --- |
| `im.gateway.session.ImSessionService` | JWT 校验、worker 选择和 session ticket 签发。 |
| `im.gateway.session.PublicWsUrlFactory` | 对外公开 wsUrl 的显式校验。 |
| `im.gateway.session.SessionTicketCodec` | session ticket 编解码。 |
| `im.gateway.shard.RendezvousWorkerSelector` | 稳定 worker 选择。 |
| `im.gateway.shard.WorkerRegistry` | 健康 worker 注册表。 |
| `im.gateway.ws.ConnectTicketRouter` | 首帧 ticket 路由到 worker。 |
| `im.gateway.ws.ExternalImEdgeWebSocketHandler` | `/ws/im` 的外部桥接和首帧控制。 |
| `im.gateway.ws.InternalWorkerBridgeFactory` | 内部 worker bridge 创建和 traceparent 透传。 |
| `im.gateway.ws.ImGatewayFrameCodec` | gateway connect frame 编解码。 |

## Realtime

| 类 | 核心职责 |
| --- | --- |
| `im.realtime.ws.ImWebSocketHandler` | worker WebSocket auth、frame handling 和 connection lifecycle。 |
| `im.realtime.ws.ImFrameCodec` | realtime frame JSON codec。 |
| `im.realtime.presence.ConnectionRegistry` | 在线连接注册。 |
| `im.realtime.service.MessageCommandIngressService` | 发送 command 入 Kafka 前的校验和封装。 |
| `im.realtime.projection.ProjectionSyncCoordinator` | membership / policy snapshot bootstrap 和 ready gate。 |
| `im.realtime.projection.PolicyProjectionService` | 私信权限判断用的 policy projection。 |
| `im.realtime.projection.MembershipProjectionService` | 房间成员 projection。 |
| `im.realtime.projection.PolicySnapshotClient` | 拉 user policy snapshot。 |
| `im.realtime.projection.MembershipSnapshotClient` | 拉 room membership snapshot。 |
| `im.realtime.presence.RoomLocalIndex` | 本进程 room -> connectionId 索引。 |
| `im.realtime.presence.RedisRoomPresenceDirectory` | routed fanout 使用的分布式 room -> worker presence。 |
| `im.realtime.fanout.RoomFanoutOwnerCoalescer` | routed owner 侧房间更新合并、目标 dispatch 和 pending retry。 |
| `im.realtime.fanout.RoomFanoutTargetService` | routed target 校验、本地 fanout 触发和 sourceEventId 去重。 |
| `im.realtime.session.SessionTicketCodec` | realtime 侧 session ticket 校验。 |
| `im.realtime.push.PrivatePushService` | 私信在线 fanout。 |
| `im.realtime.push.SendResultPushService` | accepted / committed / rejected push。 |
| `im.realtime.push.RoomFanoutCoalescer` | 房间状态更新 fanout。 |
| `im.realtime.push.RoomUpdateCoalescer` | 房间 state-only update 合并。 |

## Core

| 类 | 核心职责 |
| --- | --- |
| `im.core.controller.ConversationController` | 私聊 HTTP 入口。 |
| `im.core.controller.RoomController` | 房间 HTTP 入口。 |
| `im.core.controller.UnreadController` | 未读 HTTP 入口。 |
| `im.core.controller.InternalRealtimeProjectionController` | membership snapshot HTTP 入口。 |
| `im.core.service.PrivateMessageService` | 私信幂等、seq 分配和会话状态。 |
| `im.core.service.RoomMessageService` | 群聊幂等、成员校验和 seq 分配。 |
| `im.core.service.RoomMembershipService` | 房间创建、加入、离开和成员事件。 |
| `im.core.service.UnreadService` | 私聊 / 群聊未读汇总和 watermark。 |
| `im.core.kafka.CommandConsumers` | 消费 IM command 并发布 persisted / committed / rejected event。 |
| `im.core.kafka.KafkaRoomMemberChangePublisher` | 通过 outbox 发布成员变化。 |
| `im.core.service.RoomMemberChangePublisher` | 房间成员事件发布端口。 |
| `im.core.service.NoopRoomMemberChangePublisher` | Kafka/outbox 关闭时的空实现。 |
| `im.core.outbox.ImMessageOutboxEnqueuer` | persisted fact、committed/rejected send-result、member 事件入 outbox。 |
| `im.core.outbox.ImKafkaOutboxHandler` | IM outbox 到 Kafka topic 的分发。 |

## Community-app policy projection

| 类 | 核心职责 |
| --- | --- |
| `im.application.ImPolicySnapshotApplicationService` | 给 realtime 拉 user policy / block relation snapshot。 |
| `im.projection.ImPolicySnapshotController` | internal snapshot HTTP 入口。 |
| `im.projection.ImPolicySnapshotService` | snapshot 装配。 |
| `im.projection.ImPolicyChangePublisher` | IM policy Kafka delta publisher。 |
| `im.projection.ImPolicyOutboxEnqueuer` | user / social policy event 到 projection outbox。 |
| `im.projection.ImPolicyKafkaOutboxHandler` | projection outbox 到 IM Kafka policy event。 |

## 关键语义

- connect accepted 不等于 persisted。
- realtime 的 policy / membership 只是快速判定，不是权威事实。
- private / room 消息都靠 clientMsgId 做消息级幂等；发送结果回执按 requestId + clientMsgId + fromUserId 区分尝试。
- room 推送是 state-only update，历史仍要靠 HTTP 拉。
- policy / membership projection 的 `version` 必须来自 owner 事实侧持久逻辑时钟：user 用 `user_policy_version_counter` + `user.policy_version`，social block 用 `social_block_version_counter` + `social_block.version` / 删除日志，room membership 用 `im_membership_version_counter` + `im_room_member.version` / 删除日志。
