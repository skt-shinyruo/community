# HTTP 写接口幂等（Idempotency-Key）

本文说明 `backend/community-common/common-idempotency` 模块的职责、对外契约、内部执行流程、存储模型、接入方式与边界风险。它是本仓库 HTTP 写接口幂等保护的独立说明文档。

相关代码：
- `backend/community-common/common-idempotency`
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyGuard.java`
- `backend/community-common/common-idempotency/src/main/java/com/nowcoder/community/common/idempotency/IdempotencyStore.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/IdempotencyKeyResolver.java`
- `backend/community-app/src/main/java/com/nowcoder/community/infra/idempotency/RequestFingerprint.java`

## 1. 目标

HTTP 写接口经常会遇到重复提交：
- 浏览器重复点击
- 前端或网关超时重试
- 客户端在网络不确定时手动重放
- 多实例部署下相同请求落到不同应用实例

`common-idempotency` 的目标是把这些重复请求收敛成同一次业务尝试：
- 同一 `userId + operation + Idempotency-Key` 只允许产生一次业务副作用。
- 并发同 key 请求返回 `409`，提示稍后重试。
- 已成功的同 key 请求直接复用上一次成功响应。
- 幂等存储不可用时，对必须幂等的入口 fail-closed，返回 `503`，避免绕过保护继续写入。

该模块不是业务模块，也不是全局 servlet filter。只有业务代码显式调用 `IdempotencyGuard.executeRequired(...)` 包裹的写操作才受保护。

## 2. 当前覆盖范围

当前仓库已接入以下 HTTP 写接口：

| 功能 | HTTP 接口 | 内部 operation | 请求指纹 |
| --- | --- | --- | --- |
| 发帖 | `POST /api/posts` | `content:create_post` | 无 |
| 发表评论 | `POST /api/posts/{postId}/comments` | `content:create_comment` | 无 |
| 钱包充值 | `POST /api/wallet/recharges` | `wallet:recharge` | `amount` |
| 钱包提现 | `POST /api/wallet/withdrawals` | `wallet:withdraw` | `amount` |
| 钱包转账 | `POST /api/wallet/transfers` | `wallet:transfer` | `toUserId`、`amount` |
| 市场下单 | `POST /api/market/orders` | `market:create_order` | `listingId`、`quantity`、`addressId` |

说明：
- `operation` 是服务端内部稳定字符串，客户端不需要也不能传。
- 请求指纹用于校验“同一个幂等 key 是否被拿来提交了不同语义的请求”。
- 没有在表格中的写接口不代表天然幂等，需要按业务风险单独评估是否接入。

## 3. 客户端契约

客户端通过 HTTP header 声明幂等 key：

```http
Idempotency-Key: <unique-key>
```

客户端约定：
- 同一次业务尝试只生成一个稳定的 `Idempotency-Key`。
- 超时重试、网关重试、用户手动重试都必须复用同一个 key。
- 新的一次业务尝试必须生成新的 key，避免误复用历史成功结果。
- 不要每次 HTTP 发送都生成新 key，否则服务端会把它们当作不同业务尝试。
- 建议使用 UUID、ULID、雪花 ID 等高碰撞安全的随机 key。
- 服务端会 `trim()` key，并要求长度不超过 128。

钱包充值、钱包提现、钱包转账和市场下单兼容旧请求体字段 `requestId`：
- header 存在、body `requestId` 不存在：使用 header。
- header 不存在、body `requestId` 存在：使用 body `requestId` 作为兼容 fallback。
- header 和 body 都存在且 trim 后相同：使用该值。
- header 和 body 都存在但不同：返回 `400`。
- 两者都不存在：返回 `400`。

新客户端应优先使用 `Idempotency-Key` header。body `requestId` 只是兼容路径。

示例：

```bash
curl -X POST "$BASE_URL/api/posts" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: 0194f6d7-8c2f-7f03-9f3d-0a15cb0ad001" \
  -d '{"title":"hello","content":"world"}'
```

## 4. 服务端幂等域

服务端幂等域是：

```text
operation + userId + Idempotency-Key
```

这意味着：
- 不同用户可以使用相同 `Idempotency-Key`，不会互相冲突。
- 同一用户在不同业务操作中使用相同 key，也不会互相冲突。
- 同一用户、同一 operation、同一 key 会被视为同一次业务尝试。

DB 存储通过唯一键表达这个域：

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

## 5. 请求指纹

部分接口需要防止“同一个 key 被不同参数复用”。例如同一个 `Idempotency-Key` 第一次用于充值 100，第二次用于充值 200，不能返回第一次充值 100 的缓存结果。

这类接口会传入 `requestHash`：
- `RequestFingerprint.sha256(...)` 生成 SHA-256 hash。
- 输入是服务端拼出的 canonical semantic string，不是原始 JSON body。
- JSON 字段顺序、空白、格式化差异不影响请求指纹。

当前 canonical string：

```text
wallet:recharge|amount=<amount>
wallet:withdraw|amount=<amount>
wallet:transfer|toUserId=<toUserId>|amount=<amount>
market:create_order|listingId=<listingId>|quantity=<quantity>|addressId=<addressId-or-empty>
```

指纹匹配规则：
- 相同 key、相同指纹、状态为 `SUCCESS`：返回缓存响应。
- 相同 key、相同指纹、状态为 `PROCESSING`：返回 `409`。
- 相同 key、不同指纹：返回业务域 replay-conflict 错误码。

内容类接口当前不传请求指纹，只按 `operation + userId + key` 做幂等。

## 6. 核心组件

`IdempotencyGuard`
- 幂等执行器，业务代码用它包裹真实写操作。
- 对外提供 `executeRequired(...)` 和可配置 fail-open/fail-closed 的 `execute(...)`。
- 负责参数校验、抢占 `PROCESSING`、执行 supplier、保存 `SUCCESS`、返回缓存响应、处理并发冲突和存储异常。

`IdempotencyStore`
- 幂等存储抽象。
- 负责在共享存储中维护 `PROCESSING` / `SUCCESS` 状态。
- 支持可选 `requestHash`。

`JdbcIdempotencyStore`
- DB 实现，当前 `community-app` 默认使用。
- 基于 `http_idempotency` 表。
- 通过 insert-first + 唯一键实现多实例互斥。

`RedisIdempotencyStore`
- Redis 实现，可选。
- 基于 `SETNX + TTL` 抢占 `PROCESSING`，再用 `SET` 保存 `SUCCESS` 响应。

`IdempotencyProperties`
- 配置前缀：`http.idempotency`
- 模块默认值：
  - `enabled=false`
  - `store=REDIS`
  - `processing-ttl=30s`
  - `success-ttl=24h`

`IdempotencyKeyResolver`
- 位于 `community-app`。
- 负责钱包和市场接口的 header/body key 兼容规则。

`RequestFingerprint`
- 位于 `community-app`。
- 负责对 canonical semantic string 生成 SHA-256 hash。

## 7. 执行流程

一次 `executeRequired(operation, userId, idempotencyKey, type, supplier)` 的流程如下。

1. 参数校验
- `userId` 不能为空。
- `operation` 不能为空。
- `supplier` 不能为空。
- `Idempotency-Key` 不能为空且长度不能超过 128。

2. 规范化
- `operation` 会 trim 并转小写。
- `Idempotency-Key` 会 trim。
- `requestHash` 会 trim；长度超过 128 会返回参数错误。
- TTL 使用配置值，非法值回退到默认值。

3. 抢占 `PROCESSING`
- Guard 调用 `store.tryAcquireProcessing(...)`。
- 成功表示本请求是 first-time。
- 失败表示 key 已存在，可能是并发请求，也可能是历史成功请求。

4. 首次请求执行真实副作用
- Guard 调用 `supplier.get()`。
- 如果业务抛出运行时异常，Guard 会删除 `PROCESSING`，允许客户端用同一个 key 重试。

5. 保存成功结果
- 业务成功后，Guard 将返回值序列化成 JSON。
- Guard 调用 `store.saveSuccess(...)`，状态变成 `SUCCESS`，并保存响应 JSON。
- 后续同 key 重试可直接反序列化旧响应返回。

6. 非首次请求分支
- 读取到 `SUCCESS`：直接返回缓存响应，不再执行业务 supplier。
- 读取到 `PROCESSING`：返回 `409`，提示请求处理中。
- 读取不到状态：返回 `503`，避免不确定状态下继续写入。
- 状态不合法：返回 `503`。

## 8. 返回语义

| 场景 | 行为 |
| --- | --- |
| 缺少 required key | `400` |
| key 过长 | `400` |
| 首次请求成功 | 返回真实业务结果，并保存 `SUCCESS` |
| 首次请求业务失败 | 删除 `PROCESSING`，透传业务异常，允许重试 |
| 同 key 成功重试 | 返回缓存响应，不重复执行业务副作用 |
| 同 key 并发请求 | `409`，提示请求处理中 |
| 同 key 但请求指纹不同 | replay-conflict，通常为 `409` |
| 幂等存储不可用，required 入口 | `503` |
| 业务已成功但保存 `SUCCESS` 失败 | 延长 `PROCESSING`，返回 `409`，提示结果确认中 |

## 9. 状态模型与 TTL

共享状态只有两种：

`PROCESSING`
- 表示某个请求已经抢占该幂等 key，业务仍在执行中。
- 用于并发互斥和“请求处理中”提示。
- TTL 由 `http.idempotency.processing-ttl` 控制，默认 `30s`。

`SUCCESS`
- 表示该幂等 key 已经成功执行。
- 保存成功响应 JSON。
- 后续重复请求直接复用该响应。
- TTL 由 `http.idempotency.success-ttl` 控制，默认 `24h`。

注意：
- `processing-ttl` 过短时，慢链路可能出现锁过期后二次执行的理论窗口。
- `success-ttl` 过短时，客户端在成功缓存过期后重试会被当作新请求。

## 10. 配置与自动装配

当前 `community-app` 默认配置：

```yaml
http:
  idempotency:
    enabled: true
    store: DB
```

可用配置：

```yaml
http:
  idempotency:
    enabled: true
    store: DB # DB 或 REDIS
    processing-ttl: 30s
    success-ttl: 24h
```

自动装配入口位于：
- `backend/community-common/common-idempotency/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`

自动装配类：
- `IdempotencyAutoConfiguration`：注册配置属性。
- `JdbcIdempotencyAutoConfiguration`：当 `http.idempotency.enabled=true` 且 `store=DB` 时创建 `JdbcIdempotencyStore`。
- `RedisIdempotencyAutoConfiguration`：当 `http.idempotency.enabled=true` 且 `store=REDIS` 或未配置 store 时创建 `RedisIdempotencyStore`。
- `IdempotencyGuardAutoConfiguration`：当存在 `IdempotencyStore` 和 `ObjectMapper` 时创建 `IdempotencyGuard`。

## 11. DB 存储模型

DB 方案依赖 `community.http_idempotency` 表，初始化脚本位于：

```text
deploy/mysql/community/010_schema_shared.sql
```

核心字段：

| 字段 | 含义 |
| --- | --- |
| `operation` | 服务端内部操作名 |
| `user_id` | 当前用户 ID |
| `idem_key` | 客户端幂等 key |
| `request_hash` | 可选请求语义指纹 |
| `status` | `P` 或 `S` |
| `response_json` | 成功响应 JSON |
| `processing_expires_at` | `PROCESSING` 过期时间 |
| `success_expires_at` | `SUCCESS` 过期时间 |

唯一键：

```sql
unique key uk_http_idem (operation, user_id, idem_key)
```

DB 实现要点：
- 首次请求通过 insert 写入 `P` 状态。
- 遇到唯一键冲突时，只会在已有记录过期后尝试重新抢占。
- `saveSuccess(...)` 使用 upsert 写入 `S` 状态、响应 JSON 和成功过期时间。
- `get(...)` 读取到过期记录时会删除并返回空状态。

## 12. Redis 存储模型

Redis key 格式：

```text
idem:<operation>:<userId>:<Idempotency-Key>
```

值格式：
- 无指纹 processing：`P`
- 有指纹 processing：`P\n<requestHash>`
- 无指纹 success：`S\n<responseJson>`
- 有指纹 success：`S\n<requestHash>\n<responseJson>`

Redis 实现要点：
- `setIfAbsent(key, value, ttl)` 抢占 `PROCESSING`。
- 成功后使用普通 `set(key, successValue, ttl)` 覆盖为 `SUCCESS`。
- `extendProcessing(...)` 使用 Lua 脚本，只在当前值仍为 `P` 时延长 TTL。

当前仓库默认使用 DB。Redis 更轻，但 Redis 抖动会直接影响关键写链路的幂等判断。

## 13. 指标

Guard 会记录 Micrometer counter：

```text
http_idempotency_total{op="<operation>", outcome="<outcome>"}
```

常见 outcome：
- `first_time`
- `succeeded`
- `duplicate`
- `concurrent_conflict`
- `replay_conflict`
- `missing_key`
- `invalid_key`
- `failed`
- `store_error`
- `race_miss`
- `serialize_error`
- `unknown_state`

这些指标可用于观察重复请求比例、并发冲突、幂等存储故障和客户端缺少 key 的情况。

## 14. 接入新写接口

接入新接口时按以下步骤处理。

1. 判断是否需要幂等保护
- 会创建订单、资金变动、发帖、评论、通知触发等不可重复副作用的接口应优先考虑。
- 纯更新类接口如果天然可以覆盖写，未必需要使用该模块。

2. 在 controller 接收 header

```java
@RequestHeader(value = IdempotencyGuard.HEADER_IDEMPOTENCY_KEY, required = false)
String idempotencyKey
```

3. 在 owner `ApplicationService` 包裹真实写操作

```java
return idempotencyGuard.executeRequired(
        "domain:operation",
        userId,
        idempotencyKey,
        ResponseType.class,
        () -> realWriteService.create(...)
);
```

4. 选择稳定 operation 名
- 推荐格式：`domain:verb_object`。
- 一旦对外上线，应保持稳定。
- 客户端不传 operation。

5. 决定是否需要请求指纹
- 如果同 key 不同参数必须被拒绝，传入 `requestHash` 和业务 replay-conflict 错误码。
- 如果只需要“同 key 不重复执行”，可以使用无指纹 overload。

6. 确保返回值可序列化
- 重复请求会从 `response_json` 反序列化为指定 `Class<T>`。
- 对难以稳定序列化的返回值，应先设计明确 DTO。

7. 补测试
- 缺失 key 返回 `400`。
- 首次请求执行 supplier。
- 成功后同 key 重试返回缓存结果。
- 并发 processing 返回 `409`。
- 有指纹接口同 key 不同参数返回 replay-conflict。
- 存储异常时 required 入口 fail-closed 返回 `503`。

## 15. 测试位置

幂等模块自身测试：
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardFingerprintTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/IdempotencyGuardStoreFailureTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/JdbcIdempotencyStoreTest.java`
- `backend/community-common/common-idempotency/src/test/java/com/nowcoder/community/common/idempotency/RedisIdempotencyStoreTest.java`

应用侧相关测试：
- `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardSerializationFailureTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencyGuardTtlTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/infra/idempotency/IdempotencySchemaPersistenceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/controller/WalletControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/market/controller/MarketControllerTest.java`

## 16. 边界与风险

这套方案是实用型 HTTP 幂等，不是严格 exactly-once。

主要边界：
- 业务副作用成功和 `saveSuccess(...)` 不是同一个原子事务。极端故障下，业务可能已成功，但 `SUCCESS` 状态未保存。
- 如果 `processing-ttl` 短于真实业务执行时间，锁可能提前过期，后续重试存在再次执行的理论窗口。
- 如果客户端每次重试都换新 key，服务端无法把它们识别为同一次业务尝试。
- 成功响应复用依赖 JSON 序列化和反序列化。返回值应尽量使用稳定 DTO。
- DB 或 Redis 存储不可用时，required 入口会返回 `503`，牺牲可用性以降低重复副作用风险。

## 17. 相关文档

- `docs/handbook/SECURITY.md`：安全边界下的幂等摘要。
- `docs/handbook/SYSTEM_DESIGN.md`：系统设计层面的同步写路径与失败处理。
- `docs/handbook/DATA_MODEL.md`：`community.http_idempotency` 表在数据模型中的位置。
- `deploy/mysql/community/010_schema_shared.sql`：DB 表结构。
- `docs/superpowers/specs/2026-04-25-community-app-idempotency-unification-design.md`：钱包/市场幂等统一的历史设计记录。
