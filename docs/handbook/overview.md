# 项目概览

这篇文档给第一次阅读仓库的人建立主线：项目是什么形态，请求通常怎么流动，为什么主站和 IM 分开，以及读源码时应该先抓哪些锚点。

更严格的架构规则见 [architecture.md](architecture.md)，系统协作机制见 [system-design.md](system-design.md)，具体业务链路总览见 [business-flows.md](business-flows.md)，详细业务逻辑按域见 [business-logic/README.md](business-logic/README.md)。

## 一句话认识项目

本仓库是一个前后端分离的 monorepo：

- `frontend/`：Vue3 SPA。
- `backend/community-gateway`：统一 HTTP / WebSocket 入口，浏览器默认先到这里。
- `backend/community-app`：主业务 owner，形态是按包治理边界的 package-scoped monolith。
- `backend/community-im`：独立 IM 子系统，包含：
  - `im-realtime`：WebSocket 接入、在线连接、Kafka command 生产、在线推送。
  - `im-core`：消息持久化、历史查询、未读状态、房间和成员权威状态。
- `backend/community-common/*`：共享错误协议、trace、JWT、安全、HTTP 幂等、outbox、Web 基础设施。
- `deploy/`：single / cluster 两套本地拓扑和可选 observability overlay。

默认本地浏览器入口：

- 前端：`http://localhost:12881`
- API / files / WebSocket：`http://localhost:12880`
- IM session bootstrap：`POST http://localhost:12880/api/im/sessions`，由 `community-im-gateway` 处理
- IM WebSocket：使用 session response `wsUrl`，稳定为 `ws://localhost:12880/ws/im`
- IM HTTP：`http://localhost:12880/api/im/**`

## 先记住的规则

### 默认入口先看 gateway

浏览器或客户端默认不直接访问 `community-app`、`im-core` 或 `im-realtime`，而是经过 `community-gateway`：

```text
Browser / Client
  -> community-gateway
      -> community-app      (/api/**, /files/**, /api/ops/**)
      -> im-core            (/api/im/** history / read state)
      -> community-im-gateway (/api/im/sessions, /ws/im)
```

如果调试接口行为，先确认请求落在哪个 deployable，再看对应服务的 controller / handler。

### community-app 是包级单体

`community-app` 不是“所有包互相调用”的普通单体。业务域按顶层包组织，例如：

- `auth`
- `user`
- `content`
- `social`
- `notice`
- `search`
- `analytics`
- `growth`
- `market`
- `wallet`
- `ops`

同域入口统一进入 `application.*ApplicationService`；跨域同步协作只走 owner-domain `api.query` / `api.action` / `api.model`；跨域异步协作只走 owner-domain `contracts.event`。

### 写路径通常是同步提交加异步投影

主站写路径先保证主事实正确，再让下游读模型或外部投影追平：

```text
HTTP write
  -> controller
  -> ApplicationService
  -> domain rules + repository interface
  -> infrastructure persistence
  -> domain event / contract event
  -> notice / search / growth / IM policy 等下游
```

当前默认配置里，发帖相关真正走 outbox 可靠投递的是搜索投影；通知是本地 `AFTER_COMMIT` best-effort 投影；积分和任务进度已经收敛为当前用例内同步 owner-domain API 协作。

### 资金写路径可能先进入 pending

钱包充值、提现、转账是 wallet owner 的同步写能力；市场下单、确认、取消和争议裁决则先写 market 主事实和 `market_wallet_action` durable command，再由后台 processor 调 wallet owner API 完成 escrow / release / refund。

这意味着市场订单返回成功时，可能只是“订单请求已接受并进入资金处理中”，例如 `ESCROW_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`。客户端和后台页面应把这些状态显示为处理中，不能把 HTTP 200 当成资金已经落账。

### IM 是双服务模型

IM 不是 `community-app` 里的普通包，而是独立子系统：

- `im-realtime` 接收 WebSocket 请求，做连接态、本地 policy 判定、Kafka command 写入和在线推送。
- `im-core` 是消息权威状态 owner，负责落库、顺序号、幂等、历史查询、已读水位和房间成员。
- Kafka 是 realtime 与 core 之间的 backplane。

这意味着：

- `sendAccepted` 只表示 command 已被 `im-realtime` 接收并写入 Kafka，不等于消息已落库。
- 在线推送不等于历史权威状态；断线或收到更新后，客户端仍要通过 HTTP 补拉历史。
- 群聊在线推送是 state-only update，客户端根据 `roomUpdatedBatch` 再回拉消息。

## 典型读写路径

### 读路径：帖子列表

```text
GET /api/posts
  -> community-gateway
  -> community-app
  -> CommunitySecurityConfig 路径授权
  -> content controller
  -> content application service
  -> content repository / cache
  -> Result<T>
```

读接口多数允许匿名，但具体路径以 `ApiSecurityRules` 和各域 `*SecurityRules` 为准。

### 写路径：发帖

```text
POST /api/posts + Authorization + Idempotency-Key
  -> community-gateway
  -> community-app
  -> PostController
  -> PostPublishingApplicationService
  -> IdempotencyGuard.executeRequired(...)
  -> content domain rules + repository
  -> DiscussPost / tag 绑定 / 分类校验
  -> 同步调用 growth / wallet 相关 owner API
  -> 发布 content domain event
  -> search outbox / notice after-commit / score refresh
```

这个链路代表了主站大多数写能力的共同形态：controller 不拼业务规则，应用层负责用例编排和事务，domain 放业务规则，MyBatis / Redis / ES 细节留在 infrastructure。

### IM 私信路径

```text
POST /api/im/sessions -> WS /ws/im
  -> community-im-gateway selects worker
  -> im-realtime auth
  -> sendPrivateText
  -> local policy projection 判断拉黑/处罚/目标用户
  -> Kafka im.command.private-text
  -> im-core 持久化、分配 seq、clientMsgId 幂等
  -> Kafka im.event.private-persisted
  -> im-realtime 在线推送
  -> client HTTP backfill /api/im/**
```

### IM 群聊路径

```text
POST /api/im/sessions -> WS /ws/im
  -> community-im-gateway selects worker
  -> im-realtime auth
  -> room membership bootstrap
  -> sendRoomText
  -> Kafka im.command.room-text
  -> im-core 校验房间与成员、分配 seq、clientMsgId 幂等
  -> Kafka im.event.room-persisted
  -> im-realtime 推送 roomUpdatedBatch
  -> client HTTP 拉取房间消息并推进 lastReadSeq
```

## 三种成功不要混淆

### HTTP 成功

例如发帖返回 200，表示主写路径和当前事务已按设计完成。它不自动表示搜索索引刷新、通知生成、积分到账或下游投影全部完成。

### Command 接单成功

例如 IM 的 `sendAccepted`，表示 `im-realtime` 已经接受请求并写入 Kafka command。它不自动表示 `im-core` 已落库，也不表示所有在线端都已收到推送。

### 投影完成

搜索、通知、IM policy、analytics 等读模型或投影有各自的交付语义：

- search：outbox worker 可靠追平，可重试。
- notice：after-commit best-effort，失败只记录日志。
- IM policy：`community-app` snapshot + outbox -> Kafka 增量事件。
- analytics：Redis HyperLogLog / Bitmap 写入，具体采集面以当前 filter 和配置为准。

### Saga command 完成

市场资金动作的 `market_wallet_action` 有自己的状态机。HTTP 成功只表示 command 已写入；真正完成要看 action 进入 `SUCCEEDED` / `CANCELLED`，以及订单或争议状态从 pending 状态被条件推进。

## 前端和客户端口径

- Vue3 SPA 默认通过 gateway 访问 `/api/**`、`/files/**` 和 IM `wsUrl`；本地 Vite / Nginx 场景会推断 `localhost:12880` 作为 API base。
- access token 存在前端内存；refresh token 由 HttpOnly cookie 自动携带。普通业务请求 `401` 后，前端会调用 `/api/auth/refresh`，成功后重试原请求。
- HTTP 返回统一 `Result<T>`；客户端要同时看 HTTP status 和 `Result.code` / `message`，排障时保留 `traceId`。
- 高风险写接口应使用稳定的 `Idempotency-Key` 或兼容 body `requestId`，同一次业务尝试重试时复用同一个值。
- IM 发送侧使用 `clientMsgId` 做消息级幂等；`ack` / `sendAccepted` 是接单语义，最终消息状态仍以 `im-core` history 为准。

## 推荐源码阅读顺序

1. 入口拓扑：`deploy/deployment.sh`、`deploy/compose*.yml`、`backend/community-gateway`。
2. 主站安全边界：`CommunitySecurityConfig`、`ApiSecurityRules`、各域 `*SecurityRules`。
3. DDD 守卫测试：`backend/community-app/src/test/java/com/nowcoder/community/app/arch`。
4. 代表写链路：content 发帖或评论。
5. 业务域详解：[business-logic/README.md](business-logic/README.md) 下的 auth、user、content、social、growth、wallet、market、notice/search/analytics/ops、IM 文档。
6. 下游投影：search outbox、notice projection、growth task / wallet reward。
7. IM 双服务：`im-realtime` 的 WebSocket handler 和 `im-core` 的消息持久化。
8. 可靠性底座：`common-idempotency`、`common-outbox`、single-flight、scheduler。
9. 运维入口：`ops`、XXL-Job handler、`/api/ops/**`。

不要一上来扫完整个 `controller/service/mapper`。这个项目更有效的读法是先确定入口和 owner，再顺着一条真实链路看同步段、事件段和失败语义。
