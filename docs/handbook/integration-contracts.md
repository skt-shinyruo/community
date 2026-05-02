# 集成契约

本文档集中说明系统内外的协作契约：owner-domain 同步 API、异步 `contracts.event`、HTTP 写接口契约、IM Kafka contract 和新增协作时的约束。

## 契约分层

本项目区分四类契约：

| 类型 | 包 / 位置 | 用途 |
| --- | --- | --- |
| HTTP DTO | controller 层 DTO | 对外浏览器 / 客户端协议 |
| `api.query` / `api.action` / `api.model` | owner-domain `api` 包 | 跨域同步协作 |
| `contracts.event` | owner-domain `contracts` 包 | 跨域异步协作 |
| Kafka / outbox payload | infrastructure adapter | 技术投递载荷 |

这些类型不能随意复用。特别是 `api.model` 和 `contracts.event` 是两套 public contract，即使字段相同，也必须分别定义并在 adapter / listener 边界显式映射。

## 同步 Owner API

跨域同步协作统一通过 owner-domain API：

```text
caller ApplicationService
  -> foreign owner api.query / api.action
  -> owner adapter
  -> owner ApplicationService
  -> owner domain
```

命名：

- 查询：`<Domain><Capability>QueryApi`
- 动作：`<Domain><Capability>ActionApi`
- 模型：`api.model.*`

规则：

- 只给 foreign domain 调用。
- same-domain 不得使用 same-domain `api.*` 作为内部入口。
- owner API 不暴露 domain model、mapper dataobject、HTTP DTO、`contracts.event`。
- owner API adapter 位于 infrastructure 侧，不把实现细节泄漏给调用方。
- 调用方 application service 负责根据业务语义决定 fail-open / fail-closed。

典型例子：

- social 解析内容实体：`content.api.query.ContentEntityQueryApi`。
- content / social 推进成长任务：`growth.api.action.*`。
- content / social 发放积分或奖励：wallet / growth owner action。
- market 资金托管、放款、退款：`wallet.api.action.WalletMarketActionApi`。
- ops 触发 search reindex：`search.api.action.SearchReindexActionApi`。

## 异步 Event Contract

跨域异步协作统一通过 owner-domain `contracts.event`：

```text
owner domain event
  -> infrastructure event publisher / bridge
  -> owner contracts.event
  -> listener / outbox enqueuer
  -> consumer ApplicationService / outbox handler
```

规则：

- 事件语义由生产方 owner。
- consumer 不依赖 producer domain model。
- consumer 不直接监听 producer 内部 service/entity/mapper。
- 事件 payload 是事实或状态变化，不是远程命令。
- 新事件需要明确版本、未知类型处理、重试和幂等点。

当前主站 contract 位置：

- `content.contracts.event.*`
- `social.contracts.event.*`

典型消费：

- notice 监听 content / social / moderation 事件，生成站内通知。
- search outbox enqueuer 监听 content 事件，写 `projection.search.post` outbox。
- growth task 处理内容 / 社交事件推进任务。
- IM policy projection 处理 user punishment / social block 变化。

## Outbox Topic Contract

Outbox topic 是内部可靠投递键，不是对外业务 API。

当前主要 topic：

- `projection.search.post`
- IM policy projection 相关 topic，名称以代码常量为准。

约束：

- topic handler 必须幂等。
- payload schema 变化要兼容旧事件或显式进入失败路径。
- handler 遇到未知/坏 payload 不应 silent drop，应失败、重试或进入 `DEAD`。
- `DEAD` 需要人工排查口径。

Search 的 `projection.search.post` 只用事件触发投影，handler 会回源 content owner 当前状态，再决定 upsert/delete ES。

## IM Kafka Contract

IM 使用 Kafka 连接 realtime 与 core：

Command topics：

- `im.command.private_text.v1`
- `im.command.room_text.v1`

Event topics：

- `im.event.private_persisted.v1`
- `im.event.room_persisted.v1`
- `im.event.private_rejected.v1`
- `im.event.room_rejected.v1`
- `im.event.room_member_changed.v1`

DLQ：

- `im.command.private_text.v1.dlq`
- `im.command.room_text.v1.dlq`

语义：

- `im-realtime` 写 command 表示请求被接单，不表示消息已落库。
- `im-core` 是持久化、顺序号和已读状态 owner。
- persisted event 表示消息已由 `im-core` 持久化。
- rejected event 表示 core 拒绝 command，客户端需要根据错误语义处理。
- room member changed event 用于 `im-realtime` 维护本机在线房间索引。
- unknown version / unsupported payload 应进入失败路径或 DLQ，不能静默丢弃。

幂等：

- 私信：`(conversationId, fromUserId, clientMsgId)`。
- 群聊：`(roomId, fromUserId, clientMsgId)`。

## HTTP 写接口契约

高风险写接口使用 `Idempotency-Key`：

```http
Idempotency-Key: <unique-key>
```

当前覆盖：

- `POST /api/posts`
- `POST /api/posts/{postId}/comments`
- `POST /api/wallet/recharges`
- `POST /api/wallet/withdrawals`
- `POST /api/wallet/transfers`
- `POST /api/market/orders`

客户端必须：

- 同一次业务尝试复用同一个 key。
- 新业务尝试生成新 key。
- 不把每次 HTTP retry 当作新业务尝试。

钱包和市场兼容旧 body `requestId`，但 header 优先；header/body 不一致返回 `400`。

完整执行语义见 [reliability.md](reliability.md)。

## 对外 HTTP 错误契约

对外 HTTP 统一返回 `Result<T>`，并用 HTTP status 表达类别：

- `400`：参数或契约错误。
- `401`：未认证。
- `403`：无权限。
- `404`：目标不存在。
- `409`：幂等并发、replay conflict、业务并发冲突。
- `503`：关键依赖不可用或 fail-closed。
- `500`：未预期服务端错误。

`Result.code` 表达业务细分，客户端不能只看 HTTP status。

## 新增同步协作清单

新增跨域同步调用时：

1. 确认被调用能力的 owner。
2. 在 owner `api.model` 定义稳定入参 / 出参。
3. 暴露 `api.query` 或 `api.action`。
4. owner adapter 进入 owner `ApplicationService`。
5. caller 只在 application 层调用 foreign API。
6. 不暴露 domain model、mapper、dataobject、HTTP DTO、`contracts.event`。
7. 补 ArchUnit 或边界测试，避免 same-domain 滥用 `api.*`。

## 新增异步协作清单

新增跨域事件时：

1. 由生产方 owner 定义 `contracts.event`。
2. 明确事件类型、版本和 payload 字段含义。
3. 明确生产时机：事务内、`BEFORE_COMMIT`、`AFTER_COMMIT` 或 outbox。
4. 明确 consumer 幂等点。
5. 明确失败处理：best-effort、重试、DLQ、DEAD 或人工补偿。
6. 不复用同步 `api.model`。
7. 不让 consumer import producer domain / infrastructure。

## 禁止事项

- 同步 API import 或返回 `contracts.event`。
- 异步事件 payload 使用 owner domain model。
- controller 调 same-domain `api.*`。
- listener / job 绕过 owner `ApplicationService`。
- application service 直接依赖 foreign infrastructure。
- domain 调 foreign `api.*`。
- 使用 root legacy `service/entity/mapper` 作为新协作入口。
