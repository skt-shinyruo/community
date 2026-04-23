# IM Realtime Hot Path Ownership Design Spec

## 1. 背景

当前 IM 热路径被切散在 `community-gateway`、`im-realtime`、`im-core`、`community-app` 四个边界上，导致以下问题同时存在：

- gateway 解析 websocket 首帧 `auth` 并解用户 JWT 选 shard，然后把原始 `auth` frame 转发给 worker。
- `im-realtime` 收到后再次校验 token，并在登录后回源 `im-core` 拉房间列表。
- 私信发送前，`im-realtime` 还要同步调用 `community-app` 做治理校验。
- 服务间没有共享 transport contract，而是通过“约定 JSON 形状”协作。

结果是：

- 登录和发送路径存在重复认证与重复解析。
- 私信时延和可用性被同步下游调用绑定。
- 群聊房间索引收敛依赖登录后的回源 bootstrap。
- 服务演进依赖运行时 JSON 形状兼容，边界脆弱。

本项目仍在开发阶段，没有部署、没有历史数据、也不要求兼容旧协议，因此本次设计以“直接硬切到新边界”为前提，不保留旧双栈。

## 2. 目标

### 2.1 核心目标

- 把 IM 从“跨服务编排流程”改成“热路径 ownership 清晰的系统”。
- websocket 建连与消息发送路径不再依赖任何同步跨服务 HTTP 查询。
- 用户 JWT 在 IM 热路径里只校验一次，不再在 gateway 和 worker 之间重复求值。
- 服务间 contract 从“猜 JSON 形状”改成共享 transport contract。

### 2.2 非目标

- 不在本次设计中把所有 IM 相关 domain 代码物理合并成单一服务。
- 不处理旧线上协议兼容、历史数据迁移、灰度双栈切换。
- 不在本次设计中重写消息持久化或 Kafka 基础设施，只重构热路径 ownership 与交互方式。

## 3. 选型结论

本次选择“严格版方案 2”作为终态架构，而不是临时补丁：

- `im-realtime` 成为 IM 热路径 owner。
- `im-core` 保留房间和消息写模型 ownership。
- `community-app` 保留治理数据和治理写模型 ownership。
- `im-realtime` 通过全量 snapshot + 增量 event 构建本地 projection。
- 登录、建连、私信发送、群聊发送、在线扇出全部基于本地 projection 与本地会话状态执行。

这个方案不是“多打一层缓存”，而是明确热路径 ownership：

- 真相仍可保留在上游写模型。
- 但热路径判定只能在 `im-realtime` 本地完成。
- 不允许在 projection 缺失时回退到同步 RPC。

## 4. 总体设计原则

### 4.1 热路径零同步 RPC

以下路径不允许存在同步跨服务 HTTP 查询：

- websocket 建连
- `sendPrivateText`
- `sendRoomText`

### 4.2 认证边界单一

- 用户 JWT 只在 `POST /api/im/sessions` 时验证一次。
- websocket 建连只验证 IM `ticket`。
- gateway 不再解析 IM 首帧，不再 decode JWT，不再根据 `jwt.sub` 选 shard。

### 4.3 写模型与读投影分离

- `im-core`、`community-app` 继续拥有各自写模型。
- `im-realtime` 持有热路径只读 projection。
- projection 必须可重建，不能只是“进程内缓存”。

### 4.4 contract 显式化

- HTTP API、websocket frame、snapshot、event 都必须进入共享 contract 模块。
- 不允许 client/server 各自定义 inline DTO，再靠 JSON 形状碰运气对齐。

### 4.5 无兼容包袱下直接硬切

- 不保留旧 `auth` websocket frame。
- 不保留 gateway 首帧 shard 逻辑。
- 不保留登录后回源房间 bootstrap。
- 不保留私信发送前同步治理校验。
- 不保留 feature flag 双栈切换。

## 5. 新边界

### 5.1 `community-gateway`

职责：

- 统一 HTTP / WS 对外入口。
- 按 path 或配置做纯路由转发。

不再负责：

- 解析 IM websocket 业务协议。
- 解析 `auth` frame。
- 解用户 JWT。
- 按用户身份做 shard 决策。

### 5.2 `im-realtime`

成为 IM 热路径 owner，负责：

- `POST /api/im/sessions`
- IM websocket 协议
- `ticket` 签发与校验
- `SessionContext`
- 本地 `RoomMembershipProjection`
- 本地 `PrivateMessagingPolicyProjection`
- 私信/群聊发送判定
- 命令接入与 accepted/rejected 应答
- 在线 fanout

### 5.3 `im-core`

继续负责：

- 房间写模型
- 房间成员关系变更
- 消息持久化相关写模型

对 `im-realtime` 的输出方式改为：

- 房间成员全量 snapshot
- 房间成员增量 event

不再负责：

- 登录链路上的同步房间 bootstrap API

### 5.4 `community-app`

继续负责：

- 禁言、封禁、拉黑等治理数据与规则的写真相

对 `im-realtime` 的输出方式改为：

- IM 治理全量 snapshot
- IM 治理增量 event

不再负责：

- 私信发送前在线治理校验 API

## 6. 外部协议

由于不需要兼容旧协议，本次直接引入新的两段式协议。

### 6.1 创建 IM session

接口：

- `POST /api/im/sessions`

输入：

- 用户 bearer token
- 可选设备或客户端元信息

输出：

- `wsUrl`
- `ticket`
- `sessionId`
- `expiresAt`

语义：

- 该接口验证用户 JWT。
- 该接口生成短时效 IM `ticket`。
- websocket 后续只认 `ticket`，不再接收原始用户 JWT。

### 6.2 websocket 首帧

新的首帧：

```json
{ "type": "connect", "ticket": "..." }
```

不再支持：

```json
{ "type": "auth", "accessToken": "..." }
```

### 6.3 发送帧

私信发送：

```json
{
  "type": "sendPrivateText",
  "clientMsgId": "c-123",
  "toUserId": "00000000-0000-7000-8000-000000000001",
  "content": "hello"
}
```

群聊发送：

```json
{
  "type": "sendRoomText",
  "clientMsgId": "c-124",
  "roomId": "00000000-0000-7000-8000-000000000002",
  "content": "hello room"
}
```

### 6.4 返回帧

至少包含：

- `AckFrame`
- `RejectFrame`
- `CommittedFrame`
- `PrivateMessageFrame`
- `RoomMessageFrame`
- `PongFrame`

这些 frame 的具体字段由共享 contract 定义，不允许在 handler 内拼 JSON 字符串。

## 7. 共享 contract 模块

新增共享模块，建议命名为：

- `backend/community-im/im-contracts`

该模块只放 transport contract，不放业务 service 内部模型。

### 7.1 Session API contracts

- `OpenImSessionRequest`
- `OpenImSessionResponse`

### 7.2 websocket contracts

- `ConnectFrame`
- `SendPrivateTextFrame`
- `SendRoomTextFrame`
- `AckFrame`
- `RejectFrame`
- `CommittedFrame`
- `PrivateMessageFrame`
- `RoomMessageFrame`
- `PingFrame`
- `PongFrame`

### 7.3 projection snapshot contracts

- `RoomMembershipSnapshot`
- `PrivateMessagingPolicySnapshot`

### 7.4 projection change event contracts

- `RoomMemberChanged`
- `UserMessagingPolicyChanged`
- `UserBlockRelationChanged`

### 7.5 contract 原则

- 不使用 `V1` / `V2` 后缀。
- 当前阶段没有兼容负担，直接使用干净的新命名。
- 后续如果真出现不兼容演进，再引入显式版本化。

## 8. Projection 设计

### 8.1 `RoomMembershipProjection`

由 `im-core` snapshot + event 驱动，至少维护以下索引：

- `userId -> roomIds`
- `roomId -> memberIds`

在 `im-realtime` 机器本地，还需要额外维护：

- `roomId -> connectionIds`

用途：

- 用户建连后，立即根据本地 `userId -> roomIds` 给连接挂房间 membership。
- 群聊 fanout 时，根据 `roomId -> connectionIds` 定位本机在线连接。

### 8.2 `PrivateMessagingPolicyProjection`

由 `community-app` snapshot + event 驱动，至少覆盖：

- 用户是否存在
- 用户是否被封禁
- 用户是否被禁言
- 用户间是否存在 block 关系
- 用户是否允许接收私信

用途：

- `sendPrivateText` 前本地判定是否允许发送。

### 8.3 Projection 数据来源

启动时：

- 从 `im-core` 拉全量 `RoomMembershipSnapshot`
- 从 `community-app` 拉全量 `PrivateMessagingPolicySnapshot`

运行时：

- 消费 `RoomMemberChanged`
- 消费 `UserMessagingPolicyChanged`
- 消费 `UserBlockRelationChanged`

### 8.4 Projection readiness

`im-realtime` 启动时必须满足：

1. 房间 snapshot 加载成功
2. 治理 snapshot 加载成功
3. 增量 event consumer 已启动
4. projection 已进入 ready 状态

只有在 ready 后，以下入口才允许开放：

- `POST /api/im/sessions`
- websocket `connect`
- 消息发送处理

### 8.5 禁止回退

projection 不可用时：

- 拒绝接流量
- 或返回 retryable error

禁止行为：

- fallback 到 `im-core` 同步拉房间
- fallback 到 `community-app` 同步做治理校验

## 9. `im-realtime` 内部模块拆分

### 9.1 `SessionApi` / `SessionService`

职责：

- 接收 bearer token
- 验证用户 JWT
- 生成 `sessionId`
- 生成短时效 `ticket`
- 返回 `wsUrl`

### 9.2 `WsConnectionHandler`

职责：

- 解析 websocket frame
- 处理 `connect`
- 绑定 `SessionContext`
- 处理 `ping`
- 分发 `sendPrivateText` / `sendRoomText`

不负责：

- 直接访问同步下游 HTTP
- 拼装业务字符串 JSON

### 9.3 `MembershipProjectionService`

职责：

- 加载房间 snapshot
- 消费房间成员 event
- 提供 membership 查询
- 在连接绑定时初始化该用户房间关系

### 9.4 `PolicyProjectionService`

职责：

- 加载治理 snapshot
- 消费治理 event
- 提供本地 `canSendPrivate(fromUserId, toUserId)`

### 9.5 `CommandIngressService`

职责：

- 对已通过本地判定的请求生成 command
- 发送写入命令
- 统一发 accepted / rejected

### 9.6 `PushService`

职责：

- 消费持久化结果或拒绝结果
- 根据在线索引 fanout 到 websocket 连接

### 9.7 `ProjectionSyncCoordinator`

职责：

- 统一管理 snapshot 拉取
- 统一管理 projection ready / lag 状态
- 控制系统是否允许接流量

## 10. 数据流

### 10.1 创建 session

1. 客户端调用 `POST /api/im/sessions`
2. `SessionService` 验证用户 JWT
3. `SessionService` 生成 `ticket`
4. 客户端收到 `wsUrl` + `ticket`

### 10.2 websocket 建连

1. 客户端连接 `wsUrl`
2. 首帧发送 `ConnectFrame`
3. `WsConnectionHandler` 验证 `ticket`
4. 绑定 `SessionContext`
5. 根据本地 `RoomMembershipProjection` 初始化连接房间索引
6. 返回连接成功 frame

### 10.3 私信发送

1. 客户端发送 `SendPrivateTextFrame`
2. `WsConnectionHandler` 从 `SessionContext` 取 `fromUserId`
3. `PolicyProjectionService` 本地判定
4. 允许则交给 `CommandIngressService`
5. `CommandIngressService` 发 accepted
6. 后续由持久化结果事件驱动 committed / rejected 推送

### 10.4 群聊发送

1. 客户端发送 `SendRoomTextFrame`
2. `MembershipProjectionService` 本地校验发送者是否为房间成员
3. 允许则交给 `CommandIngressService`
4. 群聊持久化完成后，由 `PushService` 基于本地房间在线索引 fanout

## 11. 删减项

本次硬切必须直接删除以下旧链路：

- gateway 首帧 `auth` 解析与 JWT decode 逻辑
- gateway 基于 `jwt.sub` 的 shard routing 逻辑
- websocket `auth` 消息分支
- 登录后从 `im-core` 同步拉房间列表
- 私信发送前同步调用治理服务
- `CommunityGovernanceClient`
- `ImGovernanceController`
- 为兼容旧流量存在的双模 edge 配置

这些旧路径不能保留为“备用开关”，否则新旧 ownership 会重新混杂。

## 12. 一次性切换计划

### 12.1 第一步：建立新 contracts

- 新建 `im-contracts`
- 把 session / websocket / snapshot / event contract 固定下来

### 12.2 第二步：重建 `im-realtime` 热路径

- 新增 `SessionApi`
- 新增 `ticket` 模型
- 新增 `MembershipProjectionService`
- 新增 `PolicyProjectionService`
- 新增 `ProjectionSyncCoordinator`

### 12.3 第三步：切发送判定

- 私信发送改为本地 policy 判定
- 群聊发送改为本地 membership 判定

### 12.4 第四步：切外部协议

- 引入 `POST /api/im/sessions`
- 引入 `ConnectFrame`
- 删除旧 `auth` frame 协议

### 12.5 第五步：删旧链路

- 删 gateway IM 首帧解析
- 删 `ImCoreClient` 登录 bootstrap
- 删 `CommunityGovernanceClient`
- 删 `ImGovernanceController`

## 13. 测试边界

### 13.1 session 测试

- bearer token 合法时返回 `ticket`
- `ticket` 过期、伪造、篡改时被拒绝

### 13.2 websocket 建连测试

- `ConnectFrame` 建连成功
- 缺失或无效 `ticket` 被拒绝
- 建连后不再需要发送 `auth`

### 13.3 projection 测试

- snapshot 加载后房间与治理索引正确
- 增量 event 正确更新 projection
- 重复 event、乱序 event 不破坏 projection
- projection not ready 时系统不接流量

### 13.4 热路径测试

- `sendPrivateText` 在本地 allow 时 accepted
- `sendPrivateText` 在本地 deny 时 rejected
- `sendRoomText` 对非成员 rejected
- 热路径过程中不发生同步 HTTP 调用

### 13.5 fanout 测试

- 私信 committed 后正确 fanout 给在线用户
- 群聊 committed 后只 fanout 给本地房间在线成员

### 13.6 结构约束测试

- `im-realtime` 不再依赖同步治理 client
- `im-realtime` 不再依赖登录 bootstrap HTTP client
- gateway 不再 import IM `auth` frame parser

## 14. 风险与控制

### 14.1 projection 初始加载成本

风险：

- 全量 snapshot 较大时，启动耗时上升。

控制：

- 允许先用 internal HTTP snapshot 起步。
- 若后续规模扩大，再演进为 compacted topic 或离线快照恢复。

### 14.2 projection 滞后

风险：

- event lag 导致本地判定滞后。

控制：

- 监控 lag
- 超阈值时拒绝新流量或返回 retryable error
- 不回退同步 RPC

### 14.3 ticket 设计不当

风险：

- 如果 `ticket` 生命周期、签名或绑定信息设计不严谨，会引入会话安全漏洞。

控制：

- `ticket` 必须短时效
- 绑定 `sessionId`、`userId`、过期时间和必要签名信息
- worker 只信任自己验证通过的 `ticket`

## 15. 结论

本次重构的本质不是减少几个 RPC，而是重建 IM 热路径 ownership：

- gateway 只路由
- `im-realtime` 拥有会话、协议、热路径判定和在线 fanout
- `im-core` 与 `community-app` 只通过 snapshot + event 提供写真相的同步结果
- IM 热路径零同步 HTTP
- contract 显式共享
- 旧链路直接删除，不做兼容双栈

重构完成后，IM 应具备以下结构特征：

- 用户 JWT 只在创建 IM session 时验证一次
- websocket 建连只验证 IM `ticket`
- 私信与群聊发送只依赖本地 projection 与本地命令接入
- 房间与治理真相不再在线参与实时裁决
