# 技术设计：架构深度重构（Phase 2：Reactive 去 ThreadLocal + Dubbo trace 对齐）

## 方案概述

### 1) gateway analytics：用 Dubbo attachments 替代 TraceContext

- 在 `gateway` 的 analytics worker 调用前：
  - 通过 `RpcContext.getClientAttachment().setAttachment(...)` 显式注入 `X-Trace-Id`（必要时补齐/规范化）。
  - 调用结束后恢复/清理对应 attachment，避免线程复用导致串线。
- 移除 `TraceContext` / `TraceId` 的直接调用与依赖。

### 2) infra-dubbo-starter：consumer 侧以“既有 attachment”作为 traceId SSOT

- 在 Dubbo consumer filter 中优先读取 invocation 的 `X-Trace-Id` attachment：
  - 若存在则将其作为 traceId，并在调用期间注入 `TraceContext/MDC`（用于 consumer 侧日志一致性）。
  - 若不存在则回退到 ThreadLocal traceId（若有）或生成新的 traceId。
- provider 侧仍以 attachment 为准注入 `TraceContext/MDC`。

### 3) 门禁：禁止 gateway 引入 TraceContext

- 新增 gateway 侧源码扫描门禁测试，禁止在 `gateway/src/main/java` 引入 `com.nowcoder.community.common.trace.TraceContext`（或直接使用 `TraceId/TraceContext`），防止 reactive 侧 ThreadLocal 回潮。

## 风险与回滚

- 风险：attachments 注入/清理逻辑若遗漏，可能造成“同一线程复用下的 attachment 串线”。
  - 缓解：对单个 key 保存 before 值并 finally 恢复；并用门禁测试限制网关侧再引入 TraceContext 作为旁路实现。
- 回滚：仅涉及 gateway analytics 链路与 Dubbo filter 的兼容增强；可通过回滚本次变更恢复原行为（且 analytics 采集本身为 best-effort）。

