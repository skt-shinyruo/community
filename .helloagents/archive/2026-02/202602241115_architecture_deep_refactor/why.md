# 变更提案：架构深度重构（边界收敛 + 去漂移 + 去同步耦合）

## 需求背景

当前工程具备清晰的“模块化治理意图”（`contracts-core`/`*-api`/`infra-*`/`common`/`*-service`），并已有架构门禁（如 `contracts-core/src/test/java/com/nowcoder/community/common/arch/NoCrossServicePomDependencyTest.java`、`contracts-core/src/test/java/com/nowcoder/community/common/arch/NoCrossDomainContractImportTest.java`）。

但在实际代码演进中，仍出现了典型架构坏味道，导致可维护性/可测试性/可演进性成本持续上升：

1. **共享内核偏厚且职责混叠**：`contracts-core` 内出现运行期 ThreadLocal/MDC（`contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceId.java`、`contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceContext.java`），`Result` 静态工厂直接读取 ThreadLocal（`contracts-core/src/main/java/com/nowcoder/community/common/api/Result.java`），契约模块与运行时基础设施混在一起，升级联动面大。
2. **Reactive gateway 与 Servlet 微服务双栈**：trace、异常、鉴权等横切能力同时维护两套实现（例如 servlet `common/src/main/java/com/nowcoder/community/common/web/TraceIdFilter.java` 与 reactive `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdWebFilter.java`），语义一致性依赖“人为约定”，易漂移。
3. **安全配置在各服务重复且易漂移**：各服务 `*SecurityConfig` 普遍复制粘贴（例如 `auth-service/src/main/java/com/nowcoder/community/auth/config/AuthSecurityConfig.java`、`user-service/src/main/java/com/nowcoder/community/user/config/UserSecurityConfig.java`、`content-service/src/main/java/com/nowcoder/community/content/config/ContentSecurityConfig.java` 等），JWT authorities 解析逻辑多处重复，授权矩阵散落。
4. **事件发布存在双轨语义**：业务侧 publisher 同时支持 outbox 与 “after-commit 直发”两套语义（如 `content-service/src/main/java/com/nowcoder/community/content/event/KafkaContentEventPublisher.java`、`social-service/src/main/java/com/nowcoder/community/social/event/KafkaSocialEventPublisher.java`），可靠性能力可能被配置切换意外降级。
5. **Dubbo 同步调用在业务链路频繁出现**：跨域强耦合以“运行期依赖”形式存在（例如 `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`、`content-service/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`），引入超时瀑布、fail-open 争议与难测性。

本提案选择“深度重构”路径：以“可迁移、可回滚、可验证”为前提，完成架构边界的最终收敛，并显著降低横切能力漂移与跨服务同步耦合。

## 变更内容

1. **契约纯化（contracts-first）**：使 `contracts-core` 回归“稳定协议”，去除运行期 ThreadLocal/MDC 等实现细节；运行期能力移动到明确的 runtime/infra 模块，并通过 starter/Filter/Advice 自动装配。
2. **横切能力平台化（platformization）**：将 trace、security、错误收敛、metrics 等横切能力收敛到 `infra-*`/starter，服务侧保留“授权矩阵/领域逻辑”作为 SSOT。
3. **事件投递一致性收敛（outbox-only）**：默认安全态为 outbox reliable delivery，禁止在常态路径出现“事务后直发”语义分叉；保留应急开关时需显式标注与门禁。
4. **同步耦合治理（async-first + batch）**：把高频 Dubbo 同步依赖迁移为事件投影/本地缓存/批量 RPC；对仍保留的同步调用做“可观测 + 有界 + 明确失败语义”治理。
5. **架构护栏升级（guardrails）**：扩展现有门禁测试，覆盖“跨层/跨模块/双栈装配互斥/公共端点漂移”等关键风险点。

## 影响范围

- **模块：**
  - `contracts-core` / `contracts-event-core`
  - `common`
  - `infra-security-starter` / `infra-dubbo-starter` / `infra-outbox`
  - `gateway`
  - `auth-service` / `user-service` / `content-service` / `social-service` / `message-service` / `search-service` / `analytics-service` / `ops-service`
- **典型文件（非穷举）：**
  - `contracts-core/src/main/java/com/nowcoder/community/common/api/Result.java`
  - `contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceId.java`
  - `contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceContext.java`
  - `common/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`
  - `infra-security-starter/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`
  - `infra-security-starter/src/main/java/com/nowcoder/community/infra/security/autoconfig/ReactiveSecurityInfraAutoConfiguration.java`
  - `infra-outbox/src/main/java/com/nowcoder/community/infra/outbox/OutboxRelayJob.java`
  - `gateway/src/main/java/com/nowcoder/community/gateway/filter/TraceIdWebFilter.java`
  - `message-service/src/main/java/com/nowcoder/community/message/service/SocialServiceClient.java`
- **API：**
  - 目标是保持对外 HTTP API 兼容（`Result` 结构字段保持不变），但会新增/调整少量内部接口与事件投影能力（需按版本策略灰度）。
- **数据：**
  - outbox 表（若已存在则保持兼容；若存在多套实现则合并到统一 `infra-outbox`）。

## 核心场景

### Requirement: R1-contracts-purity
**Module:** contracts-core / common / infra-*
将 `contracts-core` 收敛为稳定契约，禁止运行期 ThreadLocal/MDC/Servlet/WebFlux 等基础设施实现进入契约模块。

#### Scenario: contracts-core-no-runtime-leak
条件：编译与测试阶段扫描 `contracts-core` 源码与依赖图。
- 期望：`contracts-core` 不包含 `ThreadLocal`/`org.slf4j.MDC`/Spring Web 相关实现代码。
- 期望：运行期 trace/security/outbox 能力由 `infra-*`/`common` 自动装配提供。

### Requirement: R2-trace-ssot-and-reactive-safe
**Module:** common / gateway / infra-dubbo-starter / infra-outbox
统一 traceId 的生成、透传与注入点，保证 reactive 与 servlet 语义一致且不会因线程复用串线。

#### Scenario: traceid-propagation-e2e
条件：HTTP → gateway → service → Dubbo → Kafka（含 consumer）链路。
- 期望：响应 header 始终包含 `X-Trace-Id`（`contracts-core/src/main/java/com/nowcoder/community/common/trace/TraceHeaders.java`）。
- 期望：`Result.traceId` 在 HTTP 与 Dubbo 返回中一致可追踪。
- 期望：reactive 侧不依赖 ThreadLocal 作为主传递机制（必要时仅在受控边界桥接）。

### Requirement: R3-security-ssot-and-drift-governance
**Module:** infra-security-starter / common / gateway / all *-service
将 JWT 解码/authorities 解析/actuator 安全链统一收敛到 starter，服务侧仅保留授权矩阵 SSOT，并增加漂移门禁。

#### Scenario: service-security-matrix-ssot
条件：修改任意服务公开/受限 API。
- 期望：服务侧 `*SecurityConfig` 仅表达授权矩阵，不重复实现 converter/decoder。
- 期望：新增公开端点必须显式声明，否则默认认证；新增敏感端点必须显式限制角色，否则门禁失败。

### Requirement: R4-outbox-only-default-safe
**Module:** infra-outbox / content-service / social-service / user-service / message-service
事件投递默认采用 outbox 可靠投递，禁止常态路径回退到直发导致一致性降级。

#### Scenario: outbox-reliable-delivery
条件：DB commit 成功但 Kafka 投递失败/超时。
- 期望：事件仍留在 outbox，可重试投递并具备可观测指标。
- 期望：业务请求返回不被 outbox relay 阻塞（主链路优先）。

### Requirement: R5-reduce-sync-coupling
**Module:** message-service / content-service / gateway / social-service / user-service
将高频 Dubbo 同步依赖迁移为事件投影/批量接口/缓存，消除超时瀑布与 fail-open 争议。

#### Scenario: message-send-no-per-request-social-rpc
条件：用户发送私信（需校验拉黑关系）。
- 期望：message-service 不在请求路径对 social-service 发起同步 RPC；使用本地投影或可控降级策略。
- 期望：失败模式可观测（指标 + 日志 + traceId），并具备清晰的安全取舍（默认 fail-closed）。

## 风险评估

- **风险：兼容性破坏（接口/事件/行为）**
  - 缓解：分阶段迁移；对外 `Result` 字段保持兼容；事件协议保持 versioned envelope；灰度发布与回滚开关（仅应急）。
- **风险：跨服务联动回归成本高**
  - 缓解：先补齐门禁与契约测试，再做大规模迁移；每阶段只推进一类横切能力或一类同步依赖治理。
- **风险：双栈上下文丢失**
  - 缓解：明确“注入点 SSOT”；对 reactive 侧禁止 ThreadLocal 作为主传递；补齐 e2e trace 断言用例。

