# Change Proposal: 异常处理语义收敛与分域错误码体系

## Requirement Background

当前仓库在异常处理上存在“粒度偏粗”的系统性问题：

- `catch (Exception ...)` 与 `throws Exception` 在生产代码中大量出现，容易把**可预期/可恢复**错误与**不可预期**错误混在一起处理，导致语义丢失。
- API 层（含对外 `/api/**` 与内部 `/internal/**`）无法稳定表达错误类别，调用方难以进行精准分支处理与降级策略选择。
- 尽管 `common` 已具备统一 `Result<T>`、`ErrorCode`/`BusinessException` 与 `GlobalExceptionHandler`，但仓库整体仍存在“在业务代码里兜底 Exception”的写法，导致：
  - 错误码/HTTP status 映射不一致；
  - 关键链路异常被吞掉（只打日志或忽略），排障成本高；
  - 业务语义与可观测性（traceId）在跨服务边界被削弱。

本变更目标是：以 `Result<T>` 为统一返回结构不变前提下，**允许 HTTP status 从“总是 200”调整为与错误类别匹配的 4xx/5xx**，并逐步把异常体系收敛到“可解释、可测试、可门禁”的目标态。

## Change Content

1. **分域错误码体系（按服务领域拆分）**
   - 各服务维护自己的错误码枚举（实现 `ErrorCode`），在 `Result.code` 中表达“业务细分”，在 `ErrorCode.httpStatus` 中表达“错误类别”。
   - `CommonErrorCode` 继续承载跨领域通用错误（参数错误/未认证/无权限/不存在/频控/服务不可用/服务端异常）。

2. **生产代码清零：消除 `catch (Exception ...)` 与 `throws Exception`**
   - 目标范围：所有模块 `src/main/**` 的业务代码（Controller/Service/Job/Kafka consumer/Client 等）。
   - 处理策略：
     - 把 `catch (Exception ...)` 替换为**更具体的异常**（例如 `NumberFormatException`、`IllegalArgumentException`、JSON/IO 相关异常等），或改为前置校验 + 抛出 `BusinessException`；
     - 把 `throws Exception` 替换为更具体的 checked exception，或在边界转换为 `BusinessException`/运行期异常，避免把“异常语义黑洞”传播到上层；
     - 框架配置类（例如 Spring Security `SecurityFilterChain` 相关配置）与测试代码允许保留少量更贴近框架最佳实践的 checked exception 声明（以避免为“清零”付出不必要的可读性/兼容性成本）。

3. **统一 HTTP/Result 映射与错误收敛**
   - Servlet（各业务服务）：在 `GlobalExceptionHandler` 基础上补齐常见 Spring MVC 运行期异常的映射（参数绑定、请求体解析等），确保 HTTP status 与 `Result.code` 语义一致。
   - WebFlux（gateway）：补齐除 401/403 之外的全局错误收敛（输入校验/路由/限流/上游错误透传等），确保对外响应 `Result` 结构一致且携带 traceId。

4. **关键异常路径集成/契约测试**
   - 增加“面向调用方”的契约测试：锁定 HTTP status + `Result.code/message/traceId` 行为，避免后续重构导致外部可观察行为漂移。
   - 覆盖关键路径：认证失败/权限不足/参数非法/资源不存在/限流/服务不可用/未知异常。

## Impact Scope

- **Modules：**
  - `common`（异常基座、错误码基座、统一映射）
  - `gateway`（WebFlux 错误收敛与对外协议一致性）
  - `auth-service`、`user-service`、`content-service`、`social-service`、`message-service`、`search-service`、`analytics-service`
- **Files：** 预计跨模块多文件改动（以“逐模块收敛 + 逐类场景回归测试”方式推进）
- **APIs：** `/api/**`、`/internal/**`
- **Data：** 不涉及数据结构变更（仅错误协议与异常语义收敛）

## Core Scenarios

### Requirement: 统一错误协议（HTTP status + Result）
**Module:** common / gateway / 各业务服务 API 层

#### Scenario: 调用方可基于 HTTP status + Result.code 做精确分支
- 4xx：参数/认证/权限/不存在/冲突/频控等客户端可预期错误
- 5xx：服务端异常/依赖不可用等服务端错误
- 响应体保持 `Result` 结构，并携带 `traceId` 与 `timestamp`

### Requirement: 生产代码清零（消除泛化 Exception）
**Module:** 全部生产模块

#### Scenario: 业务代码不再使用 `catch (Exception ...)` / `throws Exception`
- `src/main/**` 范围内对上述两类写法清零（框架配置类按约定豁免）
- 调用方不再面对“无法精确处理”的异常语义黑洞

### Requirement: 分域错误码体系
**Module:** auth/user/content/social/message/search/analytics/gateway

#### Scenario: 领域错误可被识别与定位
- 各领域错误码段清晰、可检索、可追踪
- `Result.code` 与日志/告警指标可做关联

### Requirement: 关键异常路径集成/契约测试
**Module:** gateway + 各业务服务

#### Scenario: 合同测试锁定对外错误行为
- 对关键接口：错误触发条件 -> HTTP status + `Result.code` + `Result.message` 的期望稳定
- 变更后 `mvn test` 能在 CI/本地及时发现协议回归

## Risk Assessment

- **风险：HTTP status 语义变化影响调用方**
  - 缓解：以契约测试锁定行为；前端已具备对 4xx/5xx 的通用 toast 逻辑，重点校验 refresh/登录等特殊逻辑不被破坏。
- **风险：大范围“清零”导致回归面大**
  - 缓解：按模块分批落地；每批次配套 `rg` 统计校验 + 关键场景测试；避免一次性大爆炸式替换。
- **风险：Kafka 消费异常语义变化影响重试/DLQ**
  - 缓解：明确“可重试/不可恢复/契约不兼容”的异常分流策略，确保仍能触发既有 error handler 机制（重试/DLQ/跳过）。
