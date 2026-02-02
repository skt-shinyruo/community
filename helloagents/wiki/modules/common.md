# common 模块

## 1. 职责
- 提供目标态（微服务）统一的 **API 返回结构**、**错误码**、**异常收敛**、**traceId 约定**。

## 2. 核心实现
- 统一返回：`com.nowcoder.community.common.api.Result`
- 错误码：
  - `com.nowcoder.community.common.api.CommonErrorCode`
  - `com.nowcoder.community.common.api.AuthErrorCode`
  - `com.nowcoder.community.common.api.SimpleErrorCode`（运行期动态错误码：用于跨服务透传 code/message）
- 业务异常：`com.nowcoder.community.common.exception.BusinessException`
- traceId：
  - Header：`X-Trace-Id`（常量在 `com.nowcoder.community.common.web.TraceIdFilter` / `com.nowcoder.community.gateway.filter.TraceIdGlobalFilter`）
  - Servlet Filter：`com.nowcoder.community.common.web.TraceIdFilter`（仅 Servlet Web 环境生效）
  - 线程上下文：`com.nowcoder.community.common.trace.TraceContext`（统一 set/clear TraceId + MDC）
  - RestTemplate 透传：`com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor`（同步调用注入 `X-Trace-Id`）
- 内部调用治理：
  - headers/错误映射/指标：`com.nowcoder.community.common.web.internalclient.InternalClientSupport`
  - 指标统一：`internal_client_requests_total` / `internal_client_latency`（tags：client/api/outcome）
- 文本兼容工具：
  - `com.nowcoder.community.common.text.HtmlEntityCodec`：基础 HTML entity 白名单编解码（用于历史内容兼容，避免二次转义可见问题）
- 全局异常：
  - `com.nowcoder.community.common.web.GlobalExceptionHandler`
  - `com.nowcoder.community.common.web.SecurityExceptionHandler`（仅在存在 Security 类时启用）
- internal API 最小权限：
  - `com.nowcoder.community.common.internal.InternalTokenFilter`：对 `/internal/**` 强制校验 `X-Internal-Token`（按 `/internal/{segment}` 映射到 `{segment}.internal-token`）
  - 轮转窗口：支持 `{segment}.internal-token-previous`（current + previous 并存）
- internal 运维入口强保护（break-glass）：
  - `com.nowcoder.community.common.internal.InternalOpsGuardFilter`：对 `/internal/**` 中的高风险运维动作进行二次校验（`X-Ops-Token` + allowlist + 限流），默认关闭
  - 当前覆盖：`/internal/*/outbox/replay`、`/internal/search/reindex`、`/internal/*/likes/backfill`
- HTTP 写接口幂等保护：
  - `com.nowcoder.community.common.idempotency.IdempotencyGuard`：基于 Redis 的 Idempotency-Key 幂等（缺失 key 时 fail-closed 返回 400；存储不可用时对 required 入口返回 503）
  - TTL 配置（可按环境调整）：
    - `http.idempotency.processing-ttl`（默认 30s）
    - `http.idempotency.success-ttl`（默认 24h）
- 事务工具：
  - `com.nowcoder.community.common.tx.AfterCommitExecutor`：在事务提交后执行非 DB 副作用（Kafka 发送、缓存刷新等），用于 P0 消除“幽灵事件”。
- prod 启动期 fail-closed 校验：
  - `com.nowcoder.community.common.startup.StartupValidation` + `StartupValidationAutoConfig`：在 `prod` profile 下校验关键配置（JWT/internal-token/trusted-proxy 等），避免 silent fallback。
- Kafka 消费辅助：
  - `com.nowcoder.community.common.kafka.KafkaTraceSupport`：消费端从 envelope 读取 `traceId` 注入 MDC，并在 finally 清理。
- 事件 envelope 解析：
  - `com.nowcoder.community.common.event.EventEnvelopeParser`：统一校验 required fields（eventId/type/version/payload），用于消费端版本治理与 unknown handling。
- unknown handling 策略：
  - `com.nowcoder.community.common.event.UnknownEventAction`：`SKIP` / `DLQ`
  - 默认策略（建议）：`unsupported envelope version -> DLQ`；`unknown event type -> SKIP（按 type 去重告警，避免 DLQ 噪音）`
  - 配置项：
    - `community.kafka.consumer.unsupported-version-action`（默认 `DLQ`）
    - `community.kafka.consumer.unknown-type-action`（默认 `SKIP`）
- 事件契约常量（跨服务边界）：
  - `com.nowcoder.community.common.event.EventTopics`：topic 统一定义（post/comment/social/moderation v1 + `.dlq`）
  - `com.nowcoder.community.common.event.EventTypes`：type 统一定义（PostPublished/CommentCreated/LikeCreated/ModerationActionApplied 等）

## 3. 约定
- 服务端统一输出 `Result<T>`，避免 Controller 拼接字符串 JSON。
- `traceId` 由 gateway 生成并透传；下游服务将其写入 MDC 并在响应头回传。

### 3.1 internal client 约定（跨服务同步调用）
- 建议优先调用 `/internal/**`（使用 `X-Internal-Token`），避免跨服务透传 Authorization 造成鉴权耦合。
- `InternalClientSupport.unwrap` 会保留下游 `code/message/traceId`（通过 `SimpleErrorCode` + message 附带 traceId），便于排障与告警归因。
- outcome 口径建议使用：`success` / `error` / `timeout` / `degraded` / `forbidden`（保持跨服务一致）。

### 3.2 internal-token 轮转
- Runbook：`helloagents/wiki/runbooks/internal-token-rotation.md`
