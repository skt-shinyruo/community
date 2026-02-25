# 任务清单：架构深度重构 Phase 2（Reactive 去 ThreadLocal + Dubbo trace 对齐）

Directory: `.helloagents/archive/2026-02/202602241220_architecture_deep_refactor_phase2/`

---

## 1. gateway：Reactive 链路去 ThreadLocal（analytics collect）
- [√] 1.1 更新 `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`：移除 `TraceContext/TraceId` 依赖，改为 Dubbo attachments 显式透传 traceId，并在 finally 恢复/清理（避免线程复用串线）。

## 2. infra-dubbo-starter：trace SSOT 对齐
- [√] 2.1 更新 `infra-dubbo-starter/src/main/java/com/nowcoder/community/common/dubbo/TraceContextDubboFilter.java`：consumer 侧优先使用 invocation 已携带的 `X-Trace-Id` attachment 作为 traceId（并在调用期间注入 TraceContext/MDC），避免生成无关 traceId。

## 3. Guardrails：门禁防回潮
- [√] 3.1 新增 gateway 门禁测试：禁止 gateway 生产代码 import `com.nowcoder.community.common.trace.TraceContext`（reactive 链路不得引入 ThreadLocal trace 上下文）。

## 4. 文档与变更记录
- [√] 4.1 同步更新知识库（gateway/infra/dubbo trace 说明）与 `.helloagents/CHANGELOG.md`（记录 Phase 2 变更点）。

## 5. 测试与迁移
- [√] 5.1 运行 `mvn test`（全仓或至少 `-pl gateway,infra-dubbo-starter -am`），确保门禁与单测通过。
  > Note: 已执行 `mvn -pl gateway,infra-dubbo-starter -am test` 与全仓 `mvn test`，BUILD SUCCESS。
- [√] 5.2 迁移方案包到 `.helloagents/archive/2026-02/` 并更新 `.helloagents/archive/_index.md`。
