# Technical Design: 异常处理语义收敛与分域错误码体系

## Technical Solution

### Core Technologies
- Spring Boot 3.2.x（Java 17）
- Spring MVC（各业务服务）+ `@RestControllerAdvice`（统一异常收敛）
- Spring Cloud Gateway（WebFlux，统一对外入口）
- 统一返回结构：`com.nowcoder.community.common.api.Result`
- 统一错误码接口：`com.nowcoder.community.common.api.ErrorCode`（含 `httpStatus` 映射）
- 统一业务异常：`com.nowcoder.community.common.exception.BusinessException`

### Implementation Key Points

1. **错误码体系分层**
   - 通用错误：继续使用 `CommonErrorCode`（语义稳定、跨服务通用）。
   - 领域错误：每个服务新增一份 `{Domain}ErrorCode`（实现 `ErrorCode`），并在枚举中显式配置：
     - `code`：领域内细分错误码（建议按服务段分配，避免冲突）
     - `message`：默认可读提示（避免泄露内部细节）
     - `httpStatus`：映射到 HTTP status（4xx/5xx）

2. **异常语义收敛策略（面向调用方）**
   - **可预期错误**：统一抛出 `BusinessException`（携带 `ErrorCode`），由全局异常处理器映射为：
     - HTTP status = `ErrorCode.httpStatus`
     - body = `Result.error(code, message)`（保留 `traceId/timestamp`）
   - **不可预期错误**：由兜底 handler 统一映射到：
     - HTTP 500 + `CommonErrorCode.INTERNAL_ERROR`
     - 仅在日志记录堆栈；响应 message 不暴露内部实现细节（fail-closed）

3. **“清零”落地方法论（批处理但可控）**
   - `catch (Exception ...)` 常见替换路径：
     - 解析类：改为捕获 `NumberFormatException` / `IllegalArgumentException` / JSON 解析异常；
     - IO/外部依赖类：捕获 `IOException` / 客户端异常并转为 `BusinessException(SERVICE_UNAVAILABLE/INTERNAL_ERROR)`；
     - “只想忽略”的场景：明确忽略的异常类型（例如只忽略 parse 失败），避免误吞业务异常。
   - `throws Exception` 常见替换路径：
     - 能精确定位 checked exception 的：改为更具体的 `throws IOException/JsonProcessingException/...`
     - API/Kafka 边界：不对外暴露 checked exception，统一捕获并转换为 `BusinessException` 或运行期异常（让容器错误处理可控）。
   - 豁免策略（方案 1 的边界）：Spring Security `SecurityFilterChain` 等框架配置类与测试代码允许保留少量 checked exception 声明，避免“为清零而清零”导致可读性下降与框架对齐困难。

4. **Servlet 侧统一异常映射增强**
   - 在 `GlobalExceptionHandler` 中补齐常见 Spring MVC 异常映射（参数绑定、消息解析、类型不匹配等），统一落到 `CommonErrorCode.INVALID_ARGUMENT` 或更合适的 4xx。
   - 保持 `BusinessException` 的 HTTP status 与 `Result.code` 一致性：HTTP 用于“类别”，`Result.code` 用于“细分”。

5. **Gateway（WebFlux）全局错误收敛补齐**
   - 现状 gateway 已对 401/403 做了 JSON 响应与 traceId 回填。
   - 需要新增 WebFlux 的全局错误处理器（`ErrorWebExceptionHandler`）：
     - 统一把非 2xx 的异常映射为 `Result`；
     - 统一回填 `X-Trace-Id/traceparent` 与 `Result.traceId`；
     - 对输入校验/请求体解析等映射为 400；对未知异常映射为 500。

## Architecture Design

```mermaid
flowchart TD
    A[Client/Frontend] -->|HTTP| B[Gateway (WebFlux)]
    B -->|HTTP| C[Service API (Spring MVC)]
    C -->|throws BusinessException| D[GlobalExceptionHandler]
    D -->|4xx/5xx + Result| B
    B -->|4xx/5xx + Result + traceId| A
```

## Architecture Decision ADR

### ADR-018: 错误协议采用“HTTP status 表达类别 + Result.code 表达细分”，并以分域错误码体系收敛异常语义
**Context:** 当前仓库存在大量 `catch(Exception)`/`throws Exception`，导致可预期错误语义被吞没；同时对外 API 多以 200 返回错误体，调用方难以基于 HTTP 语义做统一处理与降级。

**Decision:**
1. 保持统一返回体 `Result<T>` 不变；
2. 错误时允许使用 4xx/5xx，HTTP status 表达“错误类别”；
3. `Result.code` 表达“业务细分错误码”，并按服务领域拆分错误码段；
4. 业务可预期错误统一抛出 `BusinessException(ErrorCode, message)`，由全局异常处理器统一映射。

**Rationale:**
- HTTP status 可让调用方（前端/网关/内部 client）快速识别类别并实施通用策略（重试/提示/跳转登录/降级）。
- `Result.code` 提供更细粒度的业务语义，支持精确分支与告警归因。
- 统一异常收敛可减少“Controller 层拼 JSON/吞异常/随意降级”的不一致实现。

**Alternatives:**
- 方案 A：始终返回 200，仅靠 `Result.code` 表达错误 → 拒绝原因：调用方难以复用 HTTP 生态能力（缓存/重试策略/网关策略/浏览器语义）。
- 方案 B：完全依赖 HTTP status，不返回统一结构 → 拒绝原因：现有前后端与内部调用已基于 `Result`，迁移成本大且不利于跨服务一致性。

**Impact:**
- 大范围生产代码重构（清零 `catch(Exception)`/`throws Exception`）；
- 需要用契约测试锁定行为，避免回归；
- 对 Kafka/任务等非 HTTP 场景需明确异常分流与可观测性（traceId）策略。

## API Design

### Error Response（统一约定）
- **HTTP status：** 4xx/5xx（与错误类别一致）
- **Body：** `Result<Void>`（或 `Result<T>`，data 通常为空）
  - `code`：业务细分错误码（可为 4xx/5xx，也可为领域段错误码）
  - `message`：可读提示（避免泄露敏感信息）
  - `traceId`：用于全链路排障
  - `timestamp`：便于排查与对齐日志

## Data Model

不涉及数据结构变更。

## Security and Performance

- **Security**
  - 对外响应不返回堆栈与内部细节；堆栈仅记录在服务端日志。
  - 保持现有 internal-token/ops-token 保护策略不被绕过（异常收敛不改变鉴权语义）。
- **Performance**
  - 统一异常映射为轻量逻辑，避免在热路径做复杂 string 拼接或反射。
  - Gateway 的错误收敛采用最小化序列化与 header 回填，避免二次编码与重复 traceId 计算。

## Testing and Deployment

- **Testing**
  - 为关键异常路径增加集成测试/契约测试（验证 HTTP status + `Result.code/message/traceId`）。
  - 分批改动后执行 `mvn test`，并用 `rg` 统计确保 `src/main` 范围内清零目标达成。
- **Deployment**
  - 先在测试环境/灰度环境观察 4xx/5xx 分布与错误码分布是否符合预期；
  - 监控前端错误率与 refresh/login 流程是否有异常回归；
  - 若出现协议回退需求，可临时通过网关层做兼容（但以契约测试为准，避免长期分叉）。
