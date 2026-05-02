# 项目概览

这篇文档给第一次阅读仓库的人建立主线：项目是什么形态，请求通常怎么流动，为什么主站和 IM 分开，以及读源码时应该先抓哪些锚点。

更严格的架构规则见 [architecture.md](architecture.md)，系统协作机制见 [system-design.md](system-design.md)，具体业务链路见 [business-flows.md](business-flows.md)。

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
- IM WebSocket：`ws://localhost:12880/ws/im`
- IM HTTP：`http://localhost:12880/api/im/**`

## 先记住的规则

### 默认入口先看 gateway

浏览器或客户端默认不直接访问 `community-app`、`im-core` 或 `im-realtime`，而是经过 `community-gateway`：

```text
Browser / Client
  -> community-gateway
      -> community-app      (/api/**, /files/**, /api/ops/**)
      -> im-core            (/api/im/**)
      -> im-realtime        (/ws/im)
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
WS /ws/im
  -> im-realtime auth
  -> sendPrivateText
  -> local policy projection 判断拉黑/处罚/目标用户
  -> Kafka im.command.private_text.v1
  -> im-core 持久化、分配 seq、clientMsgId 幂等
  -> Kafka im.event.private_persisted.v1
  -> im-realtime 在线推送
  -> client HTTP backfill /api/im/**
```

### IM 群聊路径

```text
WS /ws/im
  -> im-realtime auth
  -> room membership bootstrap
  -> sendRoomText
  -> Kafka im.command.room_text.v1
  -> im-core 校验房间与成员、分配 seq、clientMsgId 幂等
  -> Kafka im.event.room_persisted.v1
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

## 推荐源码阅读顺序

1. 入口拓扑：`deploy/deployment.sh`、`deploy/compose*.yml`、`backend/community-gateway`。
2. 主站安全边界：`CommunitySecurityConfig`、`ApiSecurityRules`、各域 `*SecurityRules`。
3. DDD 守卫测试：`backend/community-app/src/test/java/com/nowcoder/community/app/arch`。
4. 代表写链路：content 发帖或评论。
5. 下游投影：search outbox、notice projection、growth task / wallet reward。
6. IM 双服务：`im-realtime` 的 WebSocket handler 和 `im-core` 的消息持久化。
7. 可靠性底座：`common-idempotency`、`common-outbox`、single-flight、scheduler。
8. 运维入口：`ops`、XXL-Job handler、`/api/ops/**`。

不要一上来扫完整个 `controller/service/mapper`。这个项目更有效的读法是先确定入口和 owner，再顺着一条真实链路看同步段、事件段和失败语义。
