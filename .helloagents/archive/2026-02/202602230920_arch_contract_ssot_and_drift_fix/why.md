# 变更提案：架构治理（契约 SSOT + 漂移门禁 + 可观测性一致）

## 需求背景

本项目是多模块微服务工程，包含 `contracts-*`、`common`、`infra-*`、`*-api`、`*-service`、`gateway` 等模块。随着迭代推进，出现了典型的“契约漂移 + 边界回潮”风险：

1. **Kafka topic 常量分散在多个业务域**：消费者为了拿到 topic 常量被迫依赖生产方域模块，或出现重复定义，导致“反向依赖/漂移”。
2. **trace header 常量重复定义**：`X-Trace-Id/traceparent` 在 gateway、Servlet、Dubbo 等链路里若各自 hardcode，容易 drift，且排障口径不一致。
3. **事件链路 traceId 缺失**：非 HTTP 场景（定时/消息）若无法生成/透传 traceId，会导致日志无法串联。
4. **错误语义脆弱**：通过异常 message 文案分支判断（例如包含某中文短语）会在重构/国际化/日志文案变更时失效。
5. **缺少结构性门禁**：即便约定“服务间只依赖 `*-api`/contracts”，也可能在 Maven 依赖或 import 层面回潮为 service->service、跨域错误码 import、payload 泄漏到 infra/common/gateway。
6. **运维脚本/Runbook 与代码 SSOT 不一致**：topic 清单若只在 compose 中更新而脚本未同步，会导致本地演练与排障流程出错。

## 变更内容

1. **契约中立化（SSOT）**：将 Kafka topic 常量收敛到 `contracts-event-core` 的单点定义，并在全仓统一引用。
2. **trace 统一（SSOT）**：将 trace header 常量收敛到 `contracts-core`，gateway/Servlet/Dubbo 等链路统一引用，避免重复定义。
3. **事件 traceId 强制可用**：事件 envelope 构造时若缺失 traceId，强制生成，保证事件链路可观测性。
4. **投影缺失语义结构化**：为“读模型投影缺失”提供明确 errorCode，移除基于 message 文案的脆弱判断。
5. **架构门禁**：增加 Maven 与源码 import 级别的门禁测试，防止跨服务/跨域依赖回潮。
6. **运维与文档对齐**：脚本与 Runbook 的 topic 清单与 SSOT 同步，知识库引用路径与实际代码对齐。

## 影响范围

- **模块：**
  - `contracts-core/`、`contracts-event-core/`、`common/`、`infra-dubbo-starter/`
  - 各业务服务（`*-service/`）Kafka 生产/消费与守卫逻辑
  - `scripts/`、`deploy/`、`docs/`、`.helloagents/`
- **API：** 无对外 API 形态变更（主要为内部契约与错误语义治理）
- **数据：** 无 schema 变更（投影缺失语义仅影响错误码与运行期行为）

## 核心场景

### Requirement: R1-event-topic-ssot
**Module:** contracts-event-core + all services
Kafka topic 常量必须在中立 contracts 层定义，避免由某业务域“代持”导致反向依赖。

#### Scenario: S1-producer-consumer-use-ssot-topic
当任意服务生产/消费事件时：
- 生产端与消费端均引用 `EventTopics.*`，不再定义/引用域内 topic 常量类。
- 删除旧常量类，避免并存造成 drift。

### Requirement: R2-trace-header-ssot
**Module:** contracts-core + gateway + common + infra-dubbo-starter
`X-Trace-Id/traceparent` 常量必须有唯一 SSOT，所有链路一致引用。

#### Scenario: S2-gateway-and-servlet-trace-consistent
当请求经过 gateway 与下游服务时：
- gateway 注入并回写 `X-Trace-Id/traceparent`
- Servlet 侧从 `X-Trace-Id/traceparent` 解析/生成 traceId，并写入 MDC + 响应头

#### Scenario: S3-dubbo-attachment-uses-same-keys
当通过 Dubbo 进行 RPC 调用时：
- attachment key 与 HTTP header key 口径一致，且由同一 SSOT 常量提供，避免 drift。

### Requirement: R3-traceid-always-present-in-event
**Module:** contracts-event-core + common
事件消息必须包含可用 traceId（缺失时生成），保证异步链路可观测性。

#### Scenario: S4-event-envelope-must-have-traceid
当事件在非 HTTP 场景触发（ThreadLocal 为空）时：
- `EventEnvelope.of(...)` 自动生成 traceId，日志可按 traceId 串联。

### Requirement: R4-projection-missing-structured-error
**Module:** content-service + message-service
“投影缺失”必须通过结构化 errorCode 表达，禁止通过异常 message 文案判断分支。

#### Scenario: S5-guard-branches-by-errorcode-only
当本地投影缺失时：
- repository 抛出携带 `PROJECTION_MISSING` 的 `BusinessException`
- guard 仅按 errorCode 决策（bootstrap 回填 / fail-closed）

### Requirement: R5-arch-guardrails
**Module:** contracts-core test
必须通过门禁测试约束跨模块依赖方向，防止回潮。

#### Scenario: S6-no-service-to-service-pom-deps
当任意 service 的 pom 声明依赖其他 service 时：
- 测试失败并给出明确违规清单

#### Scenario: S7-no-cross-domain-errorcode-import-or-payload-leak
当发生跨域 import 域错误码，或 infra/common/gateway/ops 依赖 domain event payload 时：
- 测试失败并给出违规清单

### Requirement: R6-ops-scripts-and-docs-aligned-with-ssot
**Module:** scripts + docs + .helloagents/wiki
运维脚本与 Runbook 必须与代码 SSOT 对齐，避免排障/演练误导。

#### Scenario: S8-kafka-topic-reset-covers-all-topics
当执行 topic 重置脚本时：
- topic 清单覆盖所有 `EventTopics` 与对应 DLQ

## 风险评估

- **风险：契约收敛变更面广**（生产/消费/脚本/文档）。
  - **缓解：** 全仓一次性替换并删除旧实现（无过渡期），通过 `mvn test` 做回归验证。
- **风险：门禁测试过严导致后续开发受阻**。
  - **缓解：** 门禁只约束“架构底线”（service->service 依赖、跨域错误码 import、payload 泄漏），不限制合法的 `*-api`/contracts 依赖。

