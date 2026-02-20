# Runbook：gateway analytics 采集（隔离/背压/排障）

## 目标
- analytics 采集属于“可丢弃链路”：任何情况下都不应影响主请求转发成功率与延迟。
- 采集失败要可观测（metrics/log），便于快速定位“analytics-service 不可用/鉴权错误/超时/队列积压”。

## 关键设计（现状）
- gateway filter 仅做字段收集与去重（TTL 缓存）：
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/AnalyticsCollectGlobalFilter.java`
  - DAU 采集不再手动 `.subscribe()`：将 principal 解析挂载到返回的 reactive 链路中并行执行；principal 获取设置短超时（50ms），超时/异常直接跳过（采集可丢弃，不影响主链路）
- filter 不直接远程调用；统一投递到有界队列：
  - `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`
- 异步 worker 消费队列并通过 Dubbo RPC 调用 analytics-service（带 timeout/并发上限；best-effort）。

## 配置项（SSOT）
- `analytics.collect.enabled`（默认 false）
- `analytics.collect.timeout-ms`（默认 300）：单次采集请求超时
- `analytics.collect.max-concurrency`（默认 50）：worker in-flight 上限
- `analytics.collect.queue-capacity`（默认 10000）：网关侧有界队列容量（满则丢弃）
- `analytics.collect.dedup-enabled`（默认 true）：网关单实例内去重开关（降噪）
- `analytics.collect.dedup-ttl-seconds`（默认 86400）：去重 TTL（秒）
- `analytics.collect.uv-cache-max-size` / `analytics.collect.dau-cache-max-size`：去重缓存容量（单实例）
- `gateway.trusted-proxy.enabled` / `gateway.trusted-proxy.cidrs`：决定是否信任 `X-Forwarded-For`（影响 UV 的 IP 维度；仅当 remoteAddr ∈ CIDR 才解析 XFF，避免伪造）

> 安全提示：启用可信代理后，应确保反向代理/Ingress 会剥离客户端自带的 `X-Forwarded-*` / `Forwarded` 并由代理统一回填，否则可能放大伪造风险。

## 观测指标（Metrics）
### 1) 计数：`gateway_analytics_collect_total`
tags：
- `metric`：`uv` / `dau`
- `outcome`：
  - `queued`：成功入队
  - `dropped`：队列满或投递失败（可丢弃链路）
  - `attempt`：worker 开始尝试调用
  - `ok`：调用成功
  - `timeout`：调用超时（见 `analytics.collect.timeout-ms`）
  - `error` / `worker_error`：调用失败或 worker 异常
  - `skipped_bad_subject`：JWT subject 非数字，跳过 DAU
  - `skipped_principal_error`：获取 principal 失败，跳过 DAU

### 2) 延迟：`gateway_analytics_collect_latency`
tags：
- `metric`：`uv` / `dau`

## 常见问题与处理
### 1) `dropped` 持续增长
含义：网关侧队列满/投递失败（采集链路被背压保护）。

排查与处理：
1) 先确认业务链路是否正常（采集不应影响主链路）
2) 查看 `ok/timeout/error` 比例：
   - timeout 高：下游慢或不可用 → 调小 `timeout-ms` 或先修复 analytics-service
   - error 高：检查 discovery / 路由 / 下游可用性
3) 调整参数（建议按优先级）：
   - 降低 `max-concurrency`（减少对下游冲击）
   - 增大 `queue-capacity`（仅在确认内存余量足够且确有必要时）
   - 直接 `analytics.collect.enabled=false`（高压期临时关闭采集）

### 2) `error` 增长但 `timeout` 不高
常见原因：
- Dubbo registry（Nacos）不可用或不可达
- analytics-service 未启动/未注册 Dubbo 服务
- 接口契约不匹配（consumer/provider 版本不一致）

建议：
- 查看 gateway 日志中 Dubbo 调用异常（registry/serialization/no provider 等关键字）
- 确认 Nacos 可达且 analytics-service 已注册对应 Dubbo service（必要时对照 namespace/group 是否一致）

### 3) DAU 一直为 0
常见原因：
- 用户未登录（无 JWT principal）
- JWT subject 不是数字（subject 解析失败会计入 `skipped_bad_subject`）
- 被浏览器预检 OPTIONS 放大（已在 filter 中跳过 OPTIONS）
- principal 获取超时/异常（会计入 `skipped_principal_error`；当前 hard-code 50ms，必要时可提高以提升 DAU 采集率）

## 建议的验收口径
- 主链路 P95/P99 不应因采集启用显著波动
- `dropped` 在可控范围内（可接受，采集链路可丢弃）
- `timeout/error` 可快速定位并具备清晰修复路径
