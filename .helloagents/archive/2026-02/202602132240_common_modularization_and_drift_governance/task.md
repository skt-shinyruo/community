# Task List：common 模块化拆分与复用漂移治理

Directory: `.helloagents/archive/2026-02/202602132240_common_modularization_and_drift_governance/`

---

## 1. contracts/infra 模块创建与依赖收敛
- [√] 1.1 新增 `contracts-core` 模块（Result/ErrorCode/CommonErrorCode/BusinessException 等），并让 `*-api` 依赖它而不是直接依赖“全量 common”，verify why.md#scenario-s1-common-slim
- [√] 1.2 新增 `contracts-event-core` 模块（EventEnvelope/parser/UnknownEventAction），并迁移通用事件协议实现，verify why.md#scenario-s4-domain-event-contract
- [√] 1.3 新增 `infra-dubbo-starter` 模块（TraceContextDubboFilter/DubboMetricsFilter 等），并迁移 Dubbo 横切能力，verify why.md#scenario-s1-common-slim

## 2. Security Starter：JWT + actuator/prometheus basic-auth 统一
- [√] 2.1 新增 `infra-security-starter`（Servlet/Reactive 双栈）并提供统一 JwtProperties + decoder + actuator 安全链，verify why.md#scenario-s2-actuator-consistency
- [√] 2.2 迁移 `gateway`：删除重复 `JwtProperties` 与 prometheus basic-auth 实现，改为依赖 starter（保留网关 API 授权矩阵），verify why.md#scenario-s2-actuator-consistency
- [√] 2.3 迁移各 Servlet 服务（auth/user/content/social/message/search/analytics）：删除重复 `JwtProperties`、`prometheusUserDetailsService` 与 actuator filter chain，改为依赖 starter，verify why.md#scenario-s2-actuator-consistency
- [√] 2.4 增加最小门禁测试：确保 `/actuator/prometheus` 需要 PROMETHEUS 角色且其他 actuator 端点默认 denyAll，verify why.md#scenario-s2-actuator-consistency

## 3. Outbox：统一实现并删除重复
- [√] 3.1 新增 `infra-outbox` 模块：抽出 `OutboxEvent/Mapper(xml)/Service/RelayJob/Properties/metrics`，verify why.md#scenario-s3-outbox-unified
- [√] 3.2 迁移 `user-service` outbox：替换为 `infra-outbox` 并删除 `user-service/.../outbox/*` 重复实现，verify why.md#scenario-s3-outbox-unified
- [√] 3.3 迁移 `content-service` outbox：替换为 `infra-outbox` 并删除重复实现，verify why.md#scenario-s3-outbox-unified
- [√] 3.4 迁移 `social-service` outbox：替换为 `infra-outbox` 并删除重复实现，verify why.md#scenario-s3-outbox-unified

## 4. 事件契约按域归属（payload/type/topic）
- [√] 4.1 将各域事件 payload/type/topic 从 common 迁移到对应 `*-api`（producer domain owns contract），并更新 producer/consumer 引用，verify why.md#scenario-s4-domain-event-contract
- [√] 4.2 在 consumer 侧补齐解析与 unknown handling 的契约测试（不携带敏感字段、可序列化、unknown 策略可配置），verify why.md#scenario-s4-domain-event-contract

## 5. 错误码归属治理（禁止跨域枚举依赖）
- [√] 5.1 将 `*ErrorCode` 从 common 迁移到各自域模块（必要时放到 `*-api`），并修复跨域 import（例如 gateway import SearchErrorCode），verify why.md#scenario-s5-no-cross-domain-errorcode-import
- [√] 5.2 增加结构性门禁（测试/架构规则）：禁止跨域 import 域错误码与域事件 payload，verify why.md#scenario-s5-no-cross-domain-errorcode-import

## 6. Security Check
- [√] 6.1 执行安全检查（鉴权边界、敏感信息、配置 fail-closed、EHRB 风险规避）

## 7. Documentation Update
- [√] 7.1 同步更新知识库：`.helloagents/modules/common.md`（common 边界）、`.helloagents/modules/infra.md`（starter/outbox）、相关服务模块文档，补齐迁移指引
- [√] 7.2 更新 `.helloagents/CHANGELOG.md` 与 `docs/ARCHITECTURE.md`（模块拆分与依赖关系）

## 8. Testing
- [√] 8.1 执行 `mvn test`（全仓）并修复因迁移导致的编译/测试失败
- [√] 8.2 执行最小冒烟：启动关键服务并验证 `/actuator/health` 与 `/actuator/prometheus` 访问策略一致
