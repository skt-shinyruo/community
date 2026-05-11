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
- market 资金托管、放款、退款：由 market wallet action processor 调用 `wallet.api.action.WalletMarketActionApi`。

Market wallet action API 语义：

- market HTTP 下单、确认、取消和争议裁决不在原 market 事务内直接写 wallet ledger。
- market 先写 `market_wallet_action`，由 processor 在事务外调用 `WalletMarketActionApi`。
- wallet `requestId` 由 market 服务端派生，格式为 `market-order:<orderId>:<action>`。
- wallet API 重放必须匹配交易类型、业务 id、金额和分录语义；不匹配返回 replay conflict。
- `escrowOrder` 要求用户钱包 active，属于用户主动支出。
- `releaseOrder` / `refundOrder` 是系统履约或补偿入账，不应因收款方钱包冻结而永久失败。

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
- `projection.im.policy`

约束：

- topic handler 必须幂等。
- payload schema 变化要显式进入失败路径，避免静默丢弃坏 payload。
- handler 遇到未知/坏 payload 不应 silent drop，应失败、重试或进入 `DEAD`。
- `DEAD` 需要人工排查口径。

Search 的 `projection.search.post` 只用事件触发投影，handler 会回源 content owner 当前状态，再决定 upsert/delete ES。

IM policy 的 `projection.im.policy` 是 `community-app` 内部 outbox topic：

- `ImPolicyOutboxEnqueuer` 监听 social `BLOCK_RELATION_CHANGED` 和 user `USER_POLICY_CHANGED` contract event。
- payload `kind=BLOCK` 会转成 Kafka `UserBlockRelationChanged`。
- payload `kind=USER_POLICY` 会转成 Kafka `UserMessagingPolicyChanged`。
- outbox event key 是 primary user id；event id 形如 `im-policy:<kind>:<uuid>`。
- payload 缺少必要时间字段或 JSON 反序列化失败时，handler 抛错交给 outbox 重试 / DEAD。
- 不能把 `projection.im.policy` 当成外部 contract；跨服务稳定 contract 是下面的 IM Kafka event。

## IM Kafka Contract

IM 使用 Kafka 连接 realtime 与 core：

Command topics：

- `im.command.private-text`
- `im.command.room-text`

Event topics：

- `im.event.private-persisted`
- `im.event.room-persisted`
- `im.event.private-rejected`
- `im.event.room-rejected`
- `im.event.room-member-changed`
- `im.event.user-messaging-policy-changed`
- `im.event.user-block-relation-changed`

DLQ：

- `im.command.private-text.dlq`
- `im.command.room-text.dlq`

语义：

- `im-realtime` 写 command 表示请求被接单，不表示消息已落库。
- `im-core` 是持久化、顺序号和已读状态 owner。
- persisted event 表示消息已由 `im-core` 持久化。
- rejected event 表示 core 拒绝 command，客户端需要根据错误语义处理。
- room member changed event 用于 `im-realtime` 维护本机在线房间索引。
- user messaging policy changed / user block relation changed event 用于 `im-realtime` 维护本机私信治理投影。
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

钱包和市场只接受 header `Idempotency-Key`；body `requestId` 会按未知字段返回 `400`。

资金相关写接口补充：

- 钱包充值、提现、转账和市场下单的 HTTP `Idempotency-Key` 只保护对外请求重放。
- 钱包总账另有 `wallet_txn.request_id`，由应用层派生，例如 `wallet:transfer:<orderId>` 或 `market-order:<orderId>:<action>`。
- 市场下单成功返回时，订单可能仍处于 `ESCROW_PENDING`，不表示 wallet escrow 已落账。
- 市场确认、取消和争议裁决可能返回 pending money state；最终资金结果由 `market_wallet_action` processor / recovery 推进。
- 客户端和后台页面应把 pending 状态展示为处理中，而不是按完成态处理。

完整执行语义见 [reliability.md](reliability.md)。

## 浏览器客户端契约

当前 Vue3 SPA 的默认约定：

- API base 优先读 runtime config，其次读 Vite env，最后在本地 `5173` / `12881` / `12890` / `12888` 场景推断 `localhost:12880`。
- access token 只保存在内存；refresh token 由 HttpOnly cookie 承载。业务请求 `401` 后前端会调用 `/api/auth/refresh`，成功后重试原请求。
- 全局错误展示优先使用后端 `Result.message` 和 `traceId`。
- 当前前端通用 axios interceptor 为发帖、评论、钱包写接口和市场下单自动附加 `Idempotency-Key`。

字段约定：

- notice 批量已读：`PUT /api/notices/read` 的 `ids` 是 UUID 字符串数组。
- market 地址：创建/更新只使用 `defaultAddress`。
- market 订单：物理商品下单需要 active `addressId`，服务端保存地址快照；订单成功后可能处于资金 pending 状态。
- wallet 转账：`toUserId` 是用户 UUID 字符串，不是数字 id 或用户名。
- IM WebSocket：客户端按 `/api/im/sessions` 返回的 `wsUrl` 建连，并为每条发送消息提供 `clientMsgId`。

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
