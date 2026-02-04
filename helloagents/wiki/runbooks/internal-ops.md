# internal OPS 运维入口 Runbook（break-glass + 双人确认）

## 0. 目标

对极高风险的 internal 运维入口（如 `search reindex`、`outbox replay`）提供**默认关闭**的安全态，并在需要时通过可回滚的流程临时开启执行：
- ✅ 默认 deny（break-glass 关闭时直接拒绝）
- ✅ 双重校验：`X-Internal-Token` + `X-Ops-Token`
- ✅ allowlist：仅允许指定来源 IP/CIDR 触发（避免 token 泄露扩大爆炸半径）
- ✅ single-flight + rate limit：防止重复触发或误触引发下游雪崩

## 1. 适用范围

- `POST /internal/search/reindex`（search-service：重建索引）
- `POST /internal/content/outbox/replay`（content-service：重放失败 outbox）
- `POST /internal/social/outbox/replay`（social-service：重放失败 outbox）
- `POST /internal/users/outbox/replay`（user-service：重放失败 outbox）
- `POST /internal/*/likes/backfill`（content-service：点赞投影冷启动/纠偏回填）

对外入口（经网关转发，仍受相同 break-glass 保护）：
- `POST /api/ops/search/reindex`（gateway -> search-service `/internal/search/reindex`，仅管理员）
  - legacy：`POST /api/search/internal/reindex` 默认禁用（gateway 返回 410 并提示迁移）

前端体验入口（仅管理员）：
- `/#/ops`（Ops Console：输入 `X-Ops-Token` 后可触发 reindex）
- `/#/search`（搜索页“重建索引”入口：同样会携带 `X-Ops-Token`）

## 2. 前置条件（必须满足）

1) **确定调用源 IP**
- 以服务侧 `ClientIpResolver` 的解析结果为准：
  - 默认使用 `remoteAddr`
  - 仅当 `remoteAddr ∈ gateway.trusted-proxy.cidrs` 才信任 `X-Forwarded-For`

2) **准备两类 token（建议两人分别保管）**
- `X-Internal-Token`：基础 internal 访问令牌（由 `InternalTokenFilter` 校验）
- `X-Ops-Token`：运维强保护令牌（由 `InternalOpsGuardFilter` 校验）

3) **确认 break-glass 默认关闭**
- 在 Nacos/配置中心中默认应为 `false`（未开启时直接拒绝）

## 3. 配置项（SSOT）

### 3.0 路径 → Header → 配置 key 映射（速查）

| 入口 | 实际 internal 路径 | 需要携带的 header | 关键配置（被调方） | 备注 |
|------|-------------------|------------------|-------------------|------|
| `POST /api/ops/search/reindex` | `POST /internal/search/reindex` | `X-Ops-Token`（调用方提供） | `ops.guard.search-reindex.*` + `ops.search.token*` + `search.internal-token*` | gateway 会注入 `X-Internal-Token=${SEARCH_INTERNAL_TOKEN}` |
| `POST /internal/<segment>/outbox/replay` | 同左 | `X-Internal-Token` + `X-Ops-Token` | `ops.guard.outbox-replay.*` + `ops.<segment>.token*` + `<segment>.internal-token*` | 建议仅在内网/堡垒机发起 |
| `POST /internal/<segment>/likes/backfill` | 同左 | `X-Internal-Token` + `X-Ops-Token` | `ops.guard.like-backfill.*` + `ops.<segment>.token*` + `<segment>.internal-token*` | 入口高成本，需严格限流 |

### 3.1 ops-token（按 internal segment 分域）

> segment 来自 `/internal/{segment}/...`，例如 `content` / `social` / `users` / `search`

- `ops.<segment>.token`
- `ops.<segment>.token-previous`（轮转窗口，可选）

### 3.2 break-glass（按运维动作分域）

- outbox replay：
  - `ops.guard.outbox-replay.enabled`（默认 `false`）
  - `ops.guard.outbox-replay.allowlist`（必须配置，否则 fail-closed 拒绝）
- search reindex：
  - `ops.guard.search-reindex.enabled`（默认 `false`）
  - `ops.guard.search-reindex.allowlist`（必须配置，否则 fail-closed 拒绝）
- like backfill：
  - `ops.guard.like-backfill.enabled`（默认 `false`）
  - `ops.guard.like-backfill.allowlist`（必须配置，否则 fail-closed 拒绝）

### 3.3 internal-token（最小权限，internal 兜底）
- `<segment>.internal-token` / `<segment>.internal-token-previous`
- users alias：`user.internal-token*`（仅当 segment 为 `users` 且非高权限写入口）
- users 高权限写入口（更小爆炸半径）：
  - `/internal/users/{id}/password`、`/internal/users/{id}/moderation`
  - 对应 key：`users.ops.internal-token*`（或 alias：`user.ops.internal-token*`）

### 3.4 频率/并发限制（全局）

- `ops.guard.rate.window-seconds`（默认 60）
- `ops.guard.rate.max`（默认 3）
- `ops.guard.lock.ttl-seconds`（默认 600）

## 4. 执行流程（默认关闭 → 临时开启 → 执行 → 验证 → 关闭）

### Step 1：临时开启 break-glass

以 outbox replay 为例（search reindex 同理）：
- 设置 `ops.guard.outbox-replay.enabled=true`
- 设置 `ops.guard.outbox-replay.allowlist=<你的IP或CIDR>`（例如 `10.0.0.0/8` 或 `127.0.0.1`）
- 设置 `ops.<segment>.token=<OPS_TOKEN>`（例如 `ops.content.token` / `ops.social.token` / `ops.users.token`）

### Step 2：执行请求（同时携带两类 token）

示例（content outbox replay）：
- method：`POST`
- path：`/internal/content/outbox/replay?limit=200`
- headers：
  - `X-Internal-Token: <CONTENT_INTERNAL_TOKEN>`
  - `X-Ops-Token: <OPS_CONTENT_TOKEN>`

### Step 3：验证结果

- 返回码：
  - `200`：执行成功（返回 replay 条数）
  - `403`：break-glass 未开 / allowlist 未命中 / token 不匹配
  - `409`：已有运维任务在执行（single-flight）
  - `429`：触发频率限制
  - `503`：Redis/保护器不可用（fail-closed）
- 日志：
  - 关键字：`[internal-ops]`（不应打印 token 明文）

### Step 4：立即关闭与回滚（强制）

执行完成后必须回滚到默认安全态：
- 设置 `ops.guard.<op>.enabled=false`
-（可选）收紧/清空 allowlist
-（可选）清空 `ops.<segment>.token-previous`

## 5. 可选：双人确认（Two-person rule）

建议的执行分工（降低单点泄露风险）：
- 人 A：持有并输入 `X-Internal-Token`
- 人 B：持有并输入 `X-Ops-Token` + 负责开启/关闭 break-glass

即使某一类 token 泄露，也无法在默认关闭与 allowlist 约束下直接触发高风险运维操作。

## 6. 403/失败排障 Checklist

> 建议先看返回体 `message`，不同过滤器的错误语义不同（便于快速定位到底是 internal-token 还是 ops-guard 阻断）。

1) **break-glass 未开启**
- 现象：`403` + “运维入口默认关闭（break-glass 未开启）”
- 排查：`ops.guard.<op>.enabled=true` 是否已生效（配置中心/实例环境）

2) **allowlist 未命中**
- 现象：`403` + “来源未在 allowlist 中”
- 排查：
  - `ops.guard.<op>.allowlist` 是否配置（逗号分隔 IP/CIDR）
  - 服务侧 `ClientIpResolver` 解析到的 `clientIp` 是什么（remote vs xff）
  - 若依赖 XFF：确认 `gateway.trusted-proxy.*` 已配置且 remoteAddr 命中 CIDR；并确保反向代理会剥离客户端自带 XFF

3) **ops-token 问题**
- 现象：`403` + “ops-token 未配置” / “ops-token 无效”
- 排查：`ops.<segment>.token*` 是否配置且与 `X-Ops-Token` 一致（注意 users alias：`ops.users.*` / `ops.user.*`）

4) **internal-token 问题**
- 现象：`403` + “internal-token 未配置” / “internal-token 无效”
- 排查：
  - 被调方 `<segment>.internal-token*` 是否配置
  - gateway 是否正确注入对应 internal token（例如 `/api/ops/search/reindex` 注入 `SEARCH_INTERNAL_TOKEN`）
  - users 高权限写入口是否误用了通用 token（应使用 `users.ops.internal-token*`）

5) **并发/频率/依赖不可用**
- `409`：single-flight 锁被占用（已有运维任务执行中）
- `429`：触发限频（`ops.guard.rate.*`）
- `503`：Redis 不可用或保护器异常（fail-closed）
