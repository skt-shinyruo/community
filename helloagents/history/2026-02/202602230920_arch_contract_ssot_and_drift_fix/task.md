# 任务清单：架构治理（契约 SSOT + 漂移门禁 + 可观测性一致）

Directory: `helloagents/plan/202602230920_arch_contract_ssot_and_drift_fix/`

---

## 1. 契约与常量 SSOT（Kafka / Trace）
- [√] 1.1 在 `contracts-event-core` 新增 topic SSOT（`EventTopics`），并全仓 Kafka producer/consumer 统一引用，verify why.md#requirement-r1-event-topic-ssot-s1-producer-consumer-use-ssot-topic
- [√] 1.2 在 `contracts-core` 新增 trace header SSOT（`TraceHeaders`），并让 gateway/Servlet 侧统一引用，verify why.md#requirement-r2-trace-header-ssot-s2-gateway-and-servlet-trace-consistent
- [√] 1.3 修复 Dubbo attachment key 的重复定义（统一引用 trace header SSOT），verify why.md#requirement-r2-trace-header-ssot-s3-dubbo-attachment-uses-same-keys
- [√] 1.4 事件 envelope 在 traceId 缺失时强制生成（`EventEnvelope.of`），verify why.md#requirement-r3-traceid-always-present-in-event-s4-event-envelope-must-have-traceid

## 2. 错误语义治理（投影缺失）
- [√] 2.1 为 content/message 增加 `PROJECTION_MISSING` errorCode，并移除基于异常 message 文案的判断分支，verify why.md#requirement-r4-projection-missing-structured-error-s5-guard-branches-by-errorcode-only

## 3. 架构门禁（防回潮）
- [√] 3.1 增加 Maven 依赖门禁：禁止 service->service 依赖，verify why.md#requirement-r5-arch-guardrails-s6-no-service-to-service-pom-deps
- [√] 3.2 校验并保持 import 门禁：禁止跨域错误码 import 与 payload 泄漏到 infra/common/gateway/ops，verify why.md#requirement-r5-arch-guardrails-s7-no-cross-domain-errorcode-import-or-payload-leak

## 4. 运维脚本与文档对齐
- [√] 4.1 同步更新 topic 运维脚本清单（reset/replay/runbook），确保覆盖 moderation topic 与 DLQ，verify why.md#requirement-r6-ops-scripts-and-docs-aligned-with-ssot-s8-kafka-topic-reset-covers-all-topics
- [√] 4.2 知识库引用路径与实际代码对齐（避免 SSOT 文档漂移），verify why.md#requirement-r6-ops-scripts-and-docs-aligned-with-ssot-s8-kafka-topic-reset-covers-all-topics

## 5. 安全检查
- [√] 5.1 执行安全检查（G9）：常量漂移风险、敏感信息、权限边界、不可逆操作风险

## 6. 测试
- [√] 6.1 执行全仓测试：`mvn test`，确认门禁与回归用例通过
