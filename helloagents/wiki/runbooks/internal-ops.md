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

### 3.1 ops-token（按 internal segment 分域）

> segment 来自 `/internal/{segment}/...`，例如 `content` / `social` / `search`

- `ops.<segment>.token`
- `ops.<segment>.token-previous`（轮转窗口，可选）

### 3.2 break-glass（按运维动作分域）

- outbox replay：
  - `ops.guard.outbox-replay.enabled`（默认 `false`）
  - `ops.guard.outbox-replay.allowlist`（必须配置，否则 fail-closed 拒绝）
- search reindex：
  - `ops.guard.search-reindex.enabled`（默认 `false`）
  - `ops.guard.search-reindex.allowlist`（必须配置，否则 fail-closed 拒绝）

### 3.3 频率/并发限制（全局）

- `ops.guard.rate.window-seconds`（默认 60）
- `ops.guard.rate.max`（默认 3）
- `ops.guard.lock.ttl-seconds`（默认 600）

## 4. 执行流程（默认关闭 → 临时开启 → 执行 → 验证 → 关闭）

### Step 1：临时开启 break-glass

以 outbox replay 为例（search reindex 同理）：
- 设置 `ops.guard.outbox-replay.enabled=true`
- 设置 `ops.guard.outbox-replay.allowlist=<你的IP或CIDR>`（例如 `10.0.0.0/8` 或 `127.0.0.1`）
- 设置 `ops.content.token=<OPS_TOKEN>`（或 `ops.social.token`）

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

