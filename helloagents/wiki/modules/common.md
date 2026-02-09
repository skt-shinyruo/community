# common 模块

## 1. 职责
- 提供目标态（微服务）统一的 **API 返回结构**、**错误码**、**异常收敛**、**traceId 约定**。

## 2. 核心实现
- 统一返回：`com.nowcoder.community.common.api.Result`
- 错误码：
  - `com.nowcoder.community.common.api.CommonErrorCode`
  - `com.nowcoder.community.common.api.AuthErrorCode`
  - `com.nowcoder.community.common.api.UserErrorCode`
  - `com.nowcoder.community.common.api.ContentErrorCode`
  - `com.nowcoder.community.common.api.SocialErrorCode`
  - `com.nowcoder.community.common.api.MessageErrorCode`
  - `com.nowcoder.community.common.api.SearchErrorCode`
  - `com.nowcoder.community.common.api.AnalyticsErrorCode`
  - `com.nowcoder.community.common.api.GatewayErrorCode`
  - `com.nowcoder.community.common.api.SimpleErrorCode`（运行期动态错误码：用于跨服务透传 code/message）
- 业务异常：`com.nowcoder.community.common.exception.BusinessException`
- traceId：
  - Header：`X-Trace-Id`（常量在 `com.nowcoder.community.common.web.TraceIdFilter` / `com.nowcoder.community.gateway.filter.TraceIdSupport`）
  - Servlet Filter：`com.nowcoder.community.common.web.TraceIdFilter`（仅 Servlet Web 环境生效）
  - 线程上下文：`com.nowcoder.community.common.trace.TraceContext`（统一 set/clear TraceId + MDC）
  - RestTemplate 透传（legacy）：`com.nowcoder.community.common.web.TraceIdClientHttpRequestInterceptor`（同步 HTTP 调用注入 `X-Trace-Id`）
  - Dubbo 透传：`com.nowcoder.community.common.dubbo.TraceContextDubboFilter`（consumer 写 attachment；provider 注入 `TraceContext/MDC` 并 finally 清理）
- 内部调用治理：
  - Dubbo（推荐）：`com.nowcoder.community.common.dubbo.TraceContextDubboFilter`、`com.nowcoder.community.common.dubbo.DubboMetricsFilter`（调用次数/时延统一埋点）
  - HTTP internal client（legacy/仅运维/兼容）：`com.nowcoder.community.common.web.internalclient.InternalClientSupport`
    - 指标统一：`internal_client_requests_total` / `internal_client_latency`（tags：client/api/outcome）
- 文本兼容工具：
  - `com.nowcoder.community.common.text.HtmlEntityCodec`：基础 HTML entity 白名单编解码（用于历史内容兼容，避免二次转义可见问题）
- 全局异常：
  - `com.nowcoder.community.common.web.GlobalExceptionHandler`
  - `com.nowcoder.community.common.web.SecurityExceptionHandler`（仅在存在 Security 类时启用）
- internal 接口说明（开发阶段）：
  - 当前版本不再通过 header token 对 `/internal/**` 做鉴权（避免配置/演进漂移带来的两极风险）
  - 生产建议通过网络隔离/网关拒绝策略收敛暴露面，并避免对外暴露 `/internal/**`
- 多实例定时任务 single-flight（可选）：
  - `com.nowcoder.community.common.scheduler.SingleFlightTaskGuard`：基于 Redis 的分布式单飞锁（`SET NX` + TTL 获取，compare-and-del 释放），用于 cleanup/reconcile 等可重试任务避免“集群内重复跑”
  - 任务侧通过各自配置开关控制（如 `*.idempotency.cleanup-single-flight`、`*.projection.reconcile.single-flight`），未启用或 Redis 不可用时应按任务风险选择 skip 或继续执行
- HTTP 写接口幂等保护：
  - `com.nowcoder.community.common.idempotency.IdempotencyGuard`：基于 Idempotency-Key 的写接口幂等（缺失 key 时 fail-closed 返回 400；存储不可用时对 required 入口返回 503）
  - 幂等开关与后端存储：
    - `http.idempotency.enabled`（默认 false，需在使用幂等的服务显式开启）
    - `http.idempotency.store=REDIS|DB`（默认 REDIS；DB 用于把 Redis 抖动从关键写链路中隔离出去）
  - TTL 配置（可按环境调整）：
    - `http.idempotency.processing-ttl`（默认 30s）
    - `http.idempotency.success-ttl`（默认 24h）
- 事务工具：
  - `com.nowcoder.community.common.tx.AfterCommitExecutor`：在事务提交后执行非 DB 副作用（Kafka 发送、缓存刷新等），用于 P0 消除“幽灵事件”。
- prod 启动期 fail-closed 校验：
  - `com.nowcoder.community.common.startup.StartupValidation` + `StartupValidationAutoConfig`：在 `prod` profile 下校验关键配置（JWT/trusted-proxy、auth dev-only 危险开关等），避免 silent fallback 与误配上线。
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
- `traceId` 由 gateway 注入并透传（WebFlux：`TraceIdWebFilter`；Servlet：`TraceIdFilter`）；下游服务将其写入 MDC 并在响应头回传。
- 统一错误协议：**HTTP status 表达“错误类别”**（4xx/5xx），**`Result.code` 表达“业务细分”**（领域错误码段）；可预期错误优先抛 `BusinessException(ErrorCode)`，由全局 handler 统一映射。
- 错误码段约定（领域拆分，便于检索/归因）：`10xxx auth` / `11xxx user` / `12xxx content` / `13xxx social` / `14xxx message` / `15xxx search` / `16xxx analytics` / `17xxx gateway`。
- 生产代码“清零门禁”：`common/src/test/java/com/nowcoder/community/common/quality/ExceptionUsageGateTest.java` 扫描各模块 `src/main/java`，禁止 `catch(Exception)` 与（除 `*SecurityConfig.java` 外的）`throws Exception` 回潮。

### 3.1 服务间同步调用（Dubbo RPC）
（已迁移）项目的“服务间同步调用”已收敛为 Dubbo RPC（契约在 `*-api` 模块）。

约定（Best Practices）：
- RPC method 统一返回 `Result<T>`（业务错误编码在 `Result.code/message`，避免跨服务异常序列化/语义漂移）。
- 默认 consumer `timeout` 偏短且可配置；默认 `retries=0`，仅对明确幂等读接口按需开启 1 次重试并控制总超时。
- Trace/metrics 通过 `common` 的 Dubbo Filter 统一透传与埋点（attachments：`X-Trace-Id/traceparent`；metrics outcome 口径一致）。

### 3.2 internal HTTP（legacy/运维/兼容）
- `/internal/**` 主要保留为运维入口/兼容调试；gateway 默认拒绝对外暴露。
- 如确需 HTTP internal 调用，建议统一使用 `InternalClientSupport.unwrap` 保留下游 `code/message/traceId`（通过 `SimpleErrorCode` + message 附带 traceId），便于排障与告警归因。
- outcome 口径建议使用：`success` / `error` / `timeout` / `degraded` / `forbidden`（保持跨服务一致）。

### 3.3 internal-token 轮转
- 说明：internal token 鉴权已在开发阶段移除，该 runbook 暂不适用。
