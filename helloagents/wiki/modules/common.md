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
- 全局异常：
  - `com.nowcoder.community.common.web.GlobalExceptionHandler`
  - `com.nowcoder.community.common.web.SecurityExceptionHandler`（仅在存在 Security 类时启用）
- internal API 最小权限：
  - `com.nowcoder.community.common.internal.InternalTokenFilter`：对 `/internal/**` 强制校验 `X-Internal-Token`（按 `/internal/{segment}` 映射到 `{segment}.internal-token`）
- 事务工具：
  - `com.nowcoder.community.common.tx.AfterCommitExecutor`：在事务提交后执行非 DB 副作用（Kafka 发送、缓存刷新等），用于 P0 消除“幽灵事件”。
- Kafka 消费辅助：
  - `com.nowcoder.community.common.kafka.KafkaTraceSupport`：消费端从 envelope 读取 `traceId` 注入 MDC，并在 finally 清理。
- 事件契约常量（跨服务边界）：
  - `com.nowcoder.community.common.event.EventTopics`：topic 统一定义（post/comment/social/moderation v1 + `.dlq`）
  - `com.nowcoder.community.common.event.EventTypes`：type 统一定义（PostPublished/CommentCreated/LikeCreated/ModerationActionApplied 等）

## 3. 约定
- 服务端统一输出 `Result<T>`，避免 Controller 拼接字符串 JSON。
- `traceId` 由 gateway 生成并透传；下游服务将其写入 MDC 并在响应头回传。
