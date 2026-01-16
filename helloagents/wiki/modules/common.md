# common 模块

## 1. 职责
- 提供目标态（微服务）统一的 **API 返回结构**、**错误码**、**异常收敛**、**traceId 约定**。

## 2. 核心实现
- 统一返回：`com.nowcoder.community.common.api.Result`
- 错误码：
  - `com.nowcoder.community.common.api.CommonErrorCode`
  - `com.nowcoder.community.common.api.AuthErrorCode`
- 业务异常：`com.nowcoder.community.common.exception.BusinessException`
- traceId：
  - Header：`X-Request-Id`（常量在 `com.nowcoder.community.common.trace.TraceId`）
  - Servlet Filter：`com.nowcoder.community.common.web.TraceIdFilter`（仅 Servlet Web 环境生效）
- 全局异常：
  - `com.nowcoder.community.common.web.GlobalExceptionHandler`
  - `com.nowcoder.community.common.web.SecurityExceptionHandler`（仅在存在 Security 类时启用）

## 3. 约定
- 服务端统一输出 `Result<T>`，避免 Controller 拼接字符串 JSON。
- `traceId` 由 gateway 生成并透传；下游服务将其写入 MDC 并在响应头回传。

