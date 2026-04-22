# 项目核心逻辑导读（给初学者）

这篇文档不是替代 `docs/ARCHITECTURE.md` 或 `docs/SYSTEM_DESIGN.md`，而是给第一次读这个仓库的人建立一套稳定的理解框架：

- 这个项目到底是什么形态
- 请求通常是怎么流动的
- 为什么主站和 IM 要这样拆
- 读源码时应该先抓哪些文件，后抓哪些文件

如果你只想先抓住主线，建议先读这篇，再回到 `docs/ARCHITECTURE.md` 和 `docs/business-logic/` 的专项文档。

---

## 1. 一句话认识这个项目

这个仓库是一个 **monorepo**：

- `frontend/` 是 Vue3 SPA
- `backend/community-gateway` 是统一入口层
- `backend/community-app` 是主业务 owner，形态上是 **按包划分边界的单体**
- `backend/community-im` 是独立 IM 子系统，其中：
  - `im-realtime` 负责 WebSocket 接入、在线连接和实时推送
  - `im-core` 负责消息持久化、历史查询、未读状态和群关系
- `backend/community-common/*` 提供幂等、outbox、安全、Web 基础设施等横切能力

可以先把它理解成下面这个运行模型：

```text
浏览器 / 客户端
  -> NGINX / community-gateway
    -> community-app      (主站业务)
    -> im-core            (IM HTTP / 持久化)
    -> im-realtime        (IM WebSocket / 在线推送)
```

这不是“一个巨大单体”，也不是“每个功能都拆成微服务”。它是：

- 主站业务聚合在 `community-app`
- IM 作为高实时性子系统单独拆出
- 跨域协作靠明确的包边界和事件契约治理

---

## 2. 初学者最先要记住的 6 条规则

### 2.1 默认入口永远先看 gateway

浏览器默认不直接访问 `community-app` 或 `im-core`，而是先进入 `community-gateway`。

关键代码：

- `backend/community-gateway/src/main/resources/application.yml`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/GatewayRouteLocatorConfig.java`
- `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/security/GatewayCorsConfig.java`

最重要的路由规则是：

- `/api/**` -> `community-app`
- `/api/im/**` -> `im-core`
- `/files/**` -> `community-app`
- `/ws/im` -> IM WebSocket worker

`GatewayRouteLocatorConfig` 会按 path prefix 长度倒序注册路由，所以 `/api/im/**` 会优先于 `/api/**` 命中。

### 2.2 `community-app` 是“包级单体”，不是“任意互相调用的单体”

主站业务都在 `backend/community-app/src/main/java/com/nowcoder/community/` 下，但它不是随便跨包互相依赖的。

顶层包基本就是领域边界，例如：

- `auth`
- `user`
- `content`
- `social`
- `notice`
- `search`
- `growth`
- `market`
- `wallet`
- `analytics`

这些边界不是文档约定而已，测试会强制检查。最值得读的“活文档”是：

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`

### 2.3 跨域同步协作只走 owner-domain `api.*`

在 `community-app` 里，`api.query`、`api.action`、`api.model` 不是远程 RPC，而是 **包边界协作接口**。

也就是说：

- controller 对外暴露 HTTP
- 同域内部通常直接进 `app/use-case/service`
- 跨领域同步调用时，优先走 owner 域暴露的 `api.query` / `api.action` / `api.model`

例如：

- `content.controller.PostController` 依赖 `PostPublishingActionApi`、`PostReadQueryApi`
- `im.governance.PrivateMessageGovernanceService` 依赖 `UserLookupQueryApi`、`SocialBlockQueryApi`

不要把 `api.*` 理解成“多此一举的一层”；在这个项目里，它的职责是保护领域边界。

### 2.4 跨域异步协作只走 `contracts.event`

主站里异步协作不是“谁想监听谁就监听谁的 service/entity”，而是走 owner-domain 的事件契约。

典型例子：

- `content.contracts.event.*`
- `social.contracts.event.*`

这样下游模块关心的是“事件契约”，不是上游的内部实现细节。

### 2.5 默认写路径是“同步提交 + 异步投影”

这点是理解主站核心逻辑的关键。

默认配置在 `backend/community-app/src/main/resources/application.yml` 中：

- `content.events.publisher: local`
- `social.events.publisher: local`
- `events.outbox.enabled: true`
- `http.idempotency.enabled: true`

这意味着主站的写链路通常会分成两段：

1. 同步段：校验、鉴权、幂等、主存储写入、领域事件发布
2. 异步段：搜索、通知、积分、任务进度等投影通过 outbox 重试执行

### 2.6 IM 是独立双服务模型

IM 不是 `community-app` 里的普通一个包，而是一个单独的子系统：

- `im-realtime` 负责 WebSocket 接入、协议解析、在线推送
- `im-core` 负责 MySQL 持久化、历史消息、已读状态、群成员关系
- Kafka 负责把 realtime 和 core 串起来

所以看到 IM 的时候，要先切换思维：

- “接单”不等于“已落库”
- “在线推送”不等于“历史查询”
- “连接层”和“数据 owner”是分开的

---

## 3. 读主站代码时，先记住这个固定模板

主站大多数请求都可以用下面这个模板去理解：

1. `community-gateway` 决定请求该进哪个服务
2. `community-app` 的 `CommunitySecurityConfig` 决定这条路径是否允许访问
3. controller 负责协议适配，不做重业务编排
4. action/query API 进入 owner-domain 的应用服务或 use case
5. 领域服务写 MyBatis mapper，只有少数多后端场景才抽 repository
6. 事务内发布领域事件
7. 事件桥接成 `contracts.event`
8. outbox / projection 把变化扩散到搜索、通知、积分等下游

关键代码锚点：

- 统一安全边界：
  - `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java`
- controller 薄层：
  - `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- 写路径编排：
  - `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
- 典型“直接 Mapper”领域：
  - `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostService.java`
- 典型“抽 Repository”领域：
  - `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockService.java`

这里有个很重要的实现取舍：

- `content`、`user`、`notice` 这类单一持久化后端的领域，通常是 `Service -> Mapper`
- `social` 因为支持 `db / redis / memory` 多实现，所以是 `Service -> Repository`

也就是说，这个项目并没有机械地要求每个领域都必须有 repository。

---

## 4. 代表链路 A：发帖为什么能代表 `community-app` 的核心逻辑

如果你只能读一条主站链路，优先读“发帖”。

### 4.1 同步写入段

`POST /api/posts` 的真实路径大致如下：

1. 路由进入 `community-gateway`
2. `community-gateway` 根据 `/api/**` 路由到 `community-app`
3. `CommunitySecurityConfig` 完成 JWT 资源服务器鉴权和路径授权
4. `PostController.create(...)` 提取当前用户和 `Idempotency-Key`
5. `PostPublishingActionService.create(...)` 做文本清洗，并调用 `IdempotencyGuard.executeRequired(...)`
6. `CreatePostUseCase.createPost(...)` 在事务内完成：
   - 用户发言资格校验
   - 分类存在性校验
   - `DiscussPost` 落库
   - tag 绑定
   - 发布帖子领域事件
   - 安排分数刷新副作用

关键文件：

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostService.java`

这里要特别注意两件事：

- 对用户来说，HTTP 写接口先解决“会不会重复写”的问题，所以先看到的是 `IdempotencyGuard`
- 对系统来说，业务事务里先保证主数据正确，再考虑搜索、通知、积分这些下游投影

### 4.2 从领域事件到事件契约

`CreatePostUseCase` 不会直接去调搜索、通知、积分服务。它只做一件更稳定的事：发布领域事件。

关键文件：

- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/PostDomainEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/PostDomainEventBridge.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/assembler/PostPayloadAssembler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/event/LocalContentEventPublisher.java`

真实分层是这样的：

1. `PostDomainEventPublisher` 只发布 `PostPublishedDomainEvent`
2. `PostDomainEventBridge` 在 `BEFORE_COMMIT` 阶段把领域事件转换成对外稳定的 `PostPayload`
3. `LocalContentEventPublisher` 把它发布成 `ContentContractEvent`

这个桥接层很重要，因为它把：

- 领域内事件
- 对外事件契约
- payload 组装

明确拆开了。

### 4.3 默认的下游扩散：outbox

当前默认配置 `events.outbox.enabled=true`，所以发布 `ContentContractEvent` 后，常见下游不是直接执行，而是先入 outbox。

关键文件：

- 入箱：
  - `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxEnqueuer.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxEnqueuer.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxEnqueuer.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- 调度与分发：
  - `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorkerScheduler.java`
  - `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
- 真正消费：
  - `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeOutboxHandler.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`

要点不是“用了 outbox 很高级”，而是：

- 入箱发生在同一事务里，避免“主数据提交了，但下游完全不知道”
- 真正投影由 worker 轮询执行，失败可重试
- handler 必须按至少一次投递去设计

### 4.4 关闭 outbox 时会怎样

如果 `events.outbox.enabled=false`，项目会退回到本地 `AFTER_COMMIT` 监听器做 best-effort 投影。

关键文件：

- `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`

所以初学者要形成一个稳定认知：

- **主链路成功** 和 **所有投影都完成** 不是一个时刻
- 这正是项目采用“同步写主数据 + 异步补投影”模型的原因

---

## 5. 代表链路 B：IM 为什么要拆成 `im-realtime` 和 `im-core`

IM 子系统最容易读乱，因为它同时有 HTTP、WebSocket、Kafka、MySQL 四层。

最简单的理解方式是：

- `im-realtime` 负责“在线接入”
- `im-core` 负责“权威数据”

### 5.1 私信链路

先看私信，它最能体现 IM 的完整设计。

关键文件：

- WebSocket 接入：
  - `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
- 治理校验：
  - `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/CommunityGovernanceClient.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/im/governance/PrivateMessageGovernanceService.java`
- Kafka command：
  - `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/CommandProducer.java`
  - `backend/community-im/im-common/src/main/java/com/nowcoder/community/im/common/ImTopics.java`
- 持久化：
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/kafka/CommandConsumers.java`
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/PrivateMessageService.java`
- 历史和未读：
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/ConversationController.java`
  - `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/UnreadController.java`

把它串起来就是：

1. 客户端连到 `/ws/im`
2. `im-realtime` 在 `ImWebSocketHandler` 里要求第一帧先发 `auth`
3. 收到 `sendPrivateText` 后，先调 `community-app` 的治理接口校验“能不能发”
4. 校验通过后，`im-realtime` 只负责把 `SendPrivateTextCommandV1` 写进 Kafka
5. `im-core` 消费 command，调用 `PrivateMessageService.persist(...)`
6. `PrivateMessageService` 负责：
   - 校验 `conversationId`
   - 保证会话存在
   - 通过 `clientMsgId` 做幂等
   - 分配 `seq` 和 `messageId`
   - 写消息
   - 更新发送者自己的已读水位
7. 持久化成功后，`im-core` 再发 persisted event
8. `im-realtime` 消费 persisted event，推给在线连接
9. 历史消息和未读状态则始终从 `im-core` 的 HTTP API 回源

最关键的语义区别是：

- WebSocket `sendAccepted` 只表示 command 已被 realtime 成功写入 Kafka
- 后续的 `sendCommitted` / `sendRejected` 才表示异步权威结果
- 最终权威历史仍以 `im-core` 查询结果为准

### 5.2 群聊链路

群聊和私信很像，但多了“房间成员关系”和“在线房间索引”。

关键文件：

- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/RoomMessageService.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/RoomController.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/kafka/EventConsumers.java`
- `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/controller/InternalRealtimeBootstrapController.java`
- `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/client/ImCoreClient.java`

群聊的核心逻辑是：

1. 在线发送仍从 `sendRoomText` 进入 `im-realtime`
2. `im-core` 的 `RoomMessageService.persist(...)` 负责校验房间存在、发送者是否是成员、`clientMsgId` 幂等、顺序号分配
3. 持久化完成后发 `EVENT_ROOM_PERSISTED_V1`
4. `im-realtime` 收到事件后，不一定直接广播完整消息，而是标记房间“有更新”
5. 客户端收到更新后，再通过 `RoomController` 提供的 HTTP API 拉取历史和推进 `lastReadSeq`
6. 用户连接建立后，`im-realtime` 还会通过 `ImCoreClient` 调 `InternalRealtimeBootstrapController` 补齐当前用户加入过的房间，用于本地在线索引

这说明群聊的真实设计目标不是“把所有状态都放在 WebSocket 连接里”，而是：

- 在线层只负责快速通知和连接态
- 持久化层负责可回放、可补拉、可重连恢复的权威状态

---

## 6. 读这个项目时，要刻意区分 3 种“成功”

这是初学者最容易混淆的地方。

### 6.1 HTTP 成功

例如发帖返回 200，表示：

- 主写路径完成了
- 当前事务已经按设计提交

它不自动表示：

- 搜索索引已经刷新
- 通知已经生成
- 积分已经到账

### 6.2 command 接单成功

例如 IM 的 `sendAccepted`，表示：

- `im-realtime` 已经成功把 command 写入 Kafka

它不自动表示：

- `im-core` 已经消费
- 消息已经落库
- 接收方已经收到推送

而 `sendCommitted` / `sendRejected` 才代表异步最终态。

### 6.3 投影完成

例如搜索、通知、积分、任务进度完成，表示：

- 下游读模型已经追平主事实

它通常晚于主写路径完成，而且允许重试。

只要把这三层分清，这个项目的大部分“为什么要这样写”都会变得好理解。

---

## 7. 推荐源码阅读顺序

如果你想在最短时间内建立整体认知，建议按下面顺序读：

1. `backend/pom.xml`
   - 先看顶层模块：`community-common`（共享基础设施）、`community-im`（IM 聚合）、`community-gateway`、`community-app`
2. `backend/community-gateway/src/main/resources/application.yml`
   - 看清 `/api`、`/api/im`、`/files`、`/ws/im` 的入口分流
3. `backend/community-gateway/src/main/java/com/nowcoder/community/gateway/http/GatewayRouteLocatorConfig.java`
   - 理解 gateway 如何真正组装路由
4. `backend/community-app/src/main/java/com/nowcoder/community/app/security/CommunitySecurityConfig.java`
   - 理解主站的统一安全边界
5. `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
   - 从一个最典型的 HTTP controller 进入
6. `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostPublishingActionService.java`
   - 看清 `api.action`、幂等包装和文本清洗的位置
7. `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/CreatePostUseCase.java`
   - 看清事务内真正做了什么
8. `backend/community-app/src/main/java/com/nowcoder/community/content/domain/event/PostDomainEventBridge.java`
   - 看清领域事件如何变成跨域事件契约
9. `backend/community-common/common-outbox/src/main/java/com/nowcoder/community/common/outbox/OutboxWorker.java`
   - 看清默认异步投影模型
10. `backend/community-im/im-realtime/src/main/java/com/nowcoder/community/im/realtime/ws/ImWebSocketHandler.java`
    - 看清 IM 在线入口的职责
11. `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/service/PrivateMessageService.java`
    - 看清 IM 权威写路径的职责
12. `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
    - 最后回头看规则，验证前面的理解是不是和项目约束一致

读完这 12 个锚点，再去读下面这些专项文档，理解会快很多：

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
- `docs/business-logic/im-private-message-flow.md`
- `docs/business-logic/im-room-message-flow.md`
- `docs/business-logic/social-like-follow-outbox-flow.md`

---

## 8. 最后给初学者的一个阅读建议

不要一上来就扫完整个 `controller/service/mapper` 目录。

这个仓库更有效的读法是：

1. 先确定入口在哪个 deployable
2. 再找安全边界在哪里
3. 再找一条真实写链路
4. 再看这条写链路如何扩散到下游
5. 最后回头看边界测试，验证你的理解

只要抓住这条线，你很快就会发现：这个项目虽然模块多，但核心逻辑其实非常稳定，反复出现的就是同一套模式。
