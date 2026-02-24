# 任务清单：架构深度重构（边界收敛 + 去漂移 + 去同步耦合）

Directory: `helloagents/history/2026-02/202602241115_architecture_deep_refactor/`

---

## 1. 架构护栏（Guardrails First）
- [√] 1.1 新增 contracts 纯化门禁测试：禁止 `contracts-core` 出现 ThreadLocal/MDC/Spring Web 等运行期实现（新增 `contracts-core/src/test/java/com/nowcoder/community/common/arch/NoContractsRuntimeLeakTest.java`），验证 why.md#requirement-r1-contracts-purity-contracts-core-no-runtime-leak
- [√] 1.2 强化跨模块依赖门禁（在 `contracts-core/src/test/java/com/nowcoder/community/common/arch/NoCrossDomainContractImportTest.java` 增补规则）：禁止 gateway/infra/common 直接 import 域 payload/内部实现，验证 why.md#requirement-r1-contracts-purity
- [√] 1.3 新增 outbox-only 门禁测试：当 `events.outbox.enabled=true` 时禁止业务代码走 after-commit 直发路径（新增 `common/src/test/java/com/nowcoder/community/common/arch/OutboxOnlyGateTest.java`），验证 why.md#requirement-r4-outbox-only-default-safe-outbox-reliable-delivery

## 2. contracts 纯化与 trace 注入点收敛
- [√] 2.1 重构 `contracts-core/src/main/java/com/nowcoder/community/common/api/Result.java`：移除对 ThreadLocal trace 的隐式依赖（不再直接读取 `TraceId.get()`），验证 why.md#requirement-r1-contracts-purity
- [√] 2.2 新增 Servlet `Result` 自动补全 traceId 的 Advice（新增 `common/src/main/java/com/nowcoder/community/common/web/ResultTraceIdAdvice.java`），验证 why.md#requirement-r2-trace-ssot-and-reactive-safe-traceid-propagation-e2e
- [√] 2.3 调整全局异常收敛只负责错误语义，traceId 统一由 Advice 注入（`GlobalExceptionHandler` 不再承担 traceId 回填），验证 why.md#requirement-r2-trace-ssot-and-reactive-safe

## 3. trace 双栈一致性与 reactive 安全
- [√] 3.1 抽取 traceId 解析/规范化 SSOT（新增 `contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceIdCodec.java`），并在 servlet/reactive 两端复用，验证 why.md#requirement-r2-trace-ssot-and-reactive-safe
- [√] 3.2 让 gateway trace 注入复用 SSOT：更新 `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdSupport.java` 使用 `TraceIdCodec`，验证 why.md#requirement-r2-trace-ssot-and-reactive-safe
- [-] 3.3 移除 gateway 中对 ThreadLocal 的 reactive 侧依赖点（优先治理 analytics collect）：更新 `gateway/src/main/java/com/nowcoder/community/gateway/analytics/AnalyticsCollectDispatcher.java`，验证 why.md#requirement-r2-trace-ssot-and-reactive-safe
  > Note: 当前 dispatcher 仍会在 worker 线程内使用 `TraceContext` 维持日志串联；后续可考虑改为“显式参数透传 + 不落 ThreadLocal”，并与 Dubbo Filter 的附件透传策略统一。

## 4. Security 平台化（减少复制粘贴与漂移）
- [√] 4.1 在 starter 输出统一 authorities converter（新增 `infra-security-starter/src/main/java/com/nowcoder/community/infra/security/jwt/AuthoritiesConverterFactory.java`），验证 why.md#requirement-r3-security-ssot-and-drift-governance
- [√] 4.2 迁移一个服务作为样板（auth-service）：更新 `auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java` 使用 starter 提供的 converter，验证 why.md#requirement-r3-security-ssot-and-drift-governance-service-security-matrix-ssot
- [√] 4.3 迁移另一个服务（content-service）：更新 `content-service/src/main/java/com/nowcoder/community/content/config/ContentSecurityConfig.java` 使用 starter 提供的 converter，验证 why.md#requirement-r3-security-ssot-and-drift-governance-service-security-matrix-ssot
- [√] 4.4 建立“公开端点漂移”门禁（新增 `common/src/test/java/com/nowcoder/community/common/arch/PublicEndpointDriftGateTest.java`），覆盖 `user-service/src/main/java/com/nowcoder/community/user/config/UserSecurityConfig.java`、`search-service/src/main/java/com/nowcoder/community/search/config/SearchSecurityConfig.java` 等服务的公开 GET 白名单一致性，验证 why.md#requirement-r3-security-ssot-and-drift-governance

## 5. outbox-only 语义收敛（可靠投递默认安全态）
- [√] 5.1 将业务 publisher 的“直发路径”降级为显式应急开关（先治理 content-service）：更新 `content-service/src/main/java/com/nowcoder/community/content/event/KafkaContentEventPublisher.java`，验证 why.md#requirement-r4-outbox-only-default-safe-outbox-reliable-delivery
- [√] 5.2 将业务 publisher 的“直发路径”降级为显式应急开关（治理 social-service）：更新 `social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`，验证 why.md#requirement-r4-outbox-only-default-safe-outbox-reliable-delivery

## 6. Dubbo 同步耦合治理（投影/批量化优先）
- [-] 6.1 选择一个安全敏感高频点做投影化试点（message-service 拉黑校验）：更新 `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java` 为“本地投影优先 + 可观测失败语义”，验证 why.md#requirement-r5-reduce-sync-coupling-message-send-no-per-request-social-rpc
  > Note: 本次先完成 contracts/trace/security/outbox 的边界收敛；同步耦合治理需要结合投影数据模型与事件回放策略，建议单独开包推进。
- [-] 6.2 为试点补齐事件消费投影（新增 `message-service/src/main/java/com/nowcoder/community/message/event/SocialBlockProjectionConsumer.java`），验证 why.md#requirement-r5-reduce-sync-coupling-message-send-no-per-request-social-rpc

## 7. 安全检查
- [-] 7.1 执行安全检查（G9）：输入校验、敏感信息、权限控制、fail-open 风险梳理与消除（覆盖 `message-service`/`content-service`/`gateway`）

## 8. 文档与知识库同步
- [√] 8.1 更新架构文档与模块边界说明：`docs/ARCHITECTURE.md`（补齐 contracts 纯化/trace 注入点/outbox-only/sync-cou合治理原则）
- [√] 8.2 更新知识库：`helloagents/wiki/arch.md`（新增 ADR 索引与迁移阶段说明）

## 9. 测试与验证
- [√] 9.1 运行单元测试门禁：`mvn test`（重点关注 arch gate 与 starter 兼容）
  > Note: 2026-02-24 已执行全仓 `mvn test`，BUILD SUCCESS。
- [-] 9.2 关键链路 smoke：登录/发帖/点赞/拉黑/私信（验证 traceId 全链路与错误语义一致）
  > Note: 建议在 docker compose 环境按 `docs/ARCHITECTURE.md` 的关键链路做最小人工验证（尤其是 trace headers 与 outbox 语义）。
