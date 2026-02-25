# common（运行期工具）+ contracts（跨服务稳定协议）

## 1. 职责边界（目标态）

本仓库已从“mega common”演进为 **contracts + infra/starter + common-runtime** 的组合：

- **contracts（跨服务稳定协议）**：演进慢、变更需谨慎、跨服务共享。
- **infra/starter（横切基础设施交付）**：以 auto-config/starter 形式交付，消除重复实现与漂移。
- **common（运行期共享工具）**：仅保留少量运行期通用能力与工具，避免继续承载“域语义”。

## 2. 核心实现（代码位置 = SSOT）

### 2.1 contracts-core（跨服务稳定协议）
- 统一返回：`contracts-core/src/main/java/com/nowcoder/community/contracts/api/Result.java`
  - 字段：`code/message/data/traceId/timestamp/httpStatus`
  - 约定：跨服务透传时，**消费者不需要 import 生产方域错误码枚举**；如需还原 HTTP 语义，可使用 `httpStatus`。
- 错误码接口：`contracts-core/src/main/java/com/nowcoder/community/contracts/api/ErrorCode.java`
- 通用错误码：`contracts-core/src/main/java/com/nowcoder/community/contracts/api/CommonErrorCode.java`
- 运行期动态错误码（透传）：`contracts-core/src/main/java/com/nowcoder/community/contracts/api/SimpleErrorCode.java`
- 业务异常：`contracts-core/src/main/java/com/nowcoder/community/contracts/exception/BusinessException.java`
- trace header SSOT：`contracts-core/src/main/java/com/nowcoder/community/contracts/trace/TraceHeaders.java`

> 域错误码不再集中在 contracts-core/common：见各域模块（2.4）。

### 2.2 contracts-event-core（通用事件协议）
- Event envelope：`contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventEnvelope.java`
- 解析器：`contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventEnvelopeParser.java`
- unknown handling：`contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/UnknownEventAction.java`
- topic SSOT：`contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventTopics.java`
- topic 约定：`contracts-event-core/src/main/java/com/nowcoder/community/contracts/event/EventTopicConventions.java`

域事件契约（type/payload）归属生产方域的 `*-api`（例如 `content-api`、`social-api`、`user-api`），避免 common 成为事件 SSOT 的单点耦合源；topic 则由中立 contracts 统一定义，降低跨域耦合与漂移风险。

### 2.3 common（运行期共享工具）
- 全局异常收敛：`common/src/main/java/com/nowcoder/community/platform/web/GlobalExceptionHandler.java`
- 安全异常收敛：`common/src/main/java/com/nowcoder/community/platform/web/SecurityExceptionHandler.java`（按条件启用）
- traceId：
  - Header：`X-Trace-Id`
  - SSOT（纯工具）：`contracts-core/src/main/java/com/nowcoder/community/contracts/trace/TraceIdCodec.java`
  - Servlet Filter：`common/src/main/java/com/nowcoder/community/platform/web/TraceIdFilter.java`
  - WebFlux WebFilter：`common/src/main/java/com/nowcoder/community/platform/web/reactive/TraceIdWebFilter.java`
  - Result trace 回填（Servlet 出口）：`common/src/main/java/com/nowcoder/community/platform/web/ResultTraceIdAdvice.java`
  - 线程上下文（ThreadLocal + MDC）：`common/src/main/java/com/nowcoder/community/platform/trace/TraceContext.java`
  - internal HTTP client（legacy/过渡）：`common/src/main/java/com/nowcoder/community/platform/web/internalclient/InternalClientSupport.java`
- 事务提交后副作用：`common/src/main/java/com/nowcoder/community/platform/tx/AfterCommitExecutor.java`
- Kafka 消费 trace 辅助：`common/src/main/java/com/nowcoder/community/platform/kafka/KafkaTraceSupport.java`
- 审计过滤器（服务侧）：`common/src/main/java/com/nowcoder/community/platform/web/AuditLogFilter.java`
- 幂等保护：`common/src/main/java/com/nowcoder/community/platform/idempotency/IdempotencyGuard.java`
- single-flight（可选）：`common/src/main/java/com/nowcoder/community/platform/scheduler/SingleFlightTaskGuard.java`
- prod 启动期 fail-closed 校验：`common/src/main/java/com/nowcoder/community/platform/startup/StartupValidationAutoConfig.java`

### 2.4 域错误码（domain owns semantics）
各域 `*ErrorCode` 归属各自域模块（域内自洽演进）：

- auth：`auth-service/src/main/java/com/nowcoder/community/auth/api/AuthErrorCode.java`
- user：`user-api/src/main/java/com/nowcoder/community/user/api/UserErrorCode.java`
- content：`content-api/src/main/java/com/nowcoder/community/content/api/ContentErrorCode.java`
- social：`social-api/src/main/java/com/nowcoder/community/social/api/SocialErrorCode.java`
- message：`message-service/src/main/java/com/nowcoder/community/message/api/MessageErrorCode.java`
- search：`search-api/src/main/java/com/nowcoder/community/search/api/SearchErrorCode.java`
- analytics：`analytics-api/src/main/java/com/nowcoder/community/analytics/api/AnalyticsErrorCode.java`
- gateway：`gateway/src/main/java/com/nowcoder/community/gateway/api/GatewayErrorCode.java`

跨服务错误语义：统一通过 `Result.code/message/httpStatus` 透传与 code 段约定归因，不要求消费者 import 生产方枚举。

### 2.5 infra/starter（横切能力交付）
- Dubbo 横切（trace/metrics）：`infra-dubbo-starter/`
- 安全与 actuator 一致性（Servlet/Reactive）：`infra-security-starter/`
- Outbox 可靠投递（实体/mapper/service/relay/job/metrics/properties）：`infra-outbox/`

## 3. 约定（重要）
- **HTTP status 表达“错误类别”，Result.code 表达“业务细分”。**
- 错误码段约定：`10xxx auth` / `11xxx user` / `12xxx content` / `13xxx social` / `14xxx message` / `15xxx search` / `16xxx analytics` / `17xxx gateway`。
- 跨域依赖门禁：
  - `contracts-core/src/test/java/com/nowcoder/community/contracts/arch/NoCrossDomainContractImportTest.java`：禁止跨域 import 域错误码；并禁止 infra/contracts/common/gateway/ops 等模块依赖 domain payload。
