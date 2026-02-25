# 技术方案：common 模块化拆分与复用漂移治理

## Technical Solution

### Core Technologies

- Java 17 / Spring Boot 3（Servlet + WebFlux 共存）
- Maven 多模块（monorepo）
- Spring Security（Resource Server JWT + Actuator basic-auth）
- Apache Dubbo（服务间同步调用、Filter 透传/埋点）
- MyBatis（outbox mapper + XML）
- Micrometer + Prometheus（指标）

### Implementation Key Points

本方案围绕两个目标展开：

1. **把“共享内核”拆成“按职责显式依赖”的小模块**，降低升级耦合面；
2. **把“重复横切能力”收敛成 starter/infra**，降低漂移与运维成本。

为避免一次性“大爆炸”改动，本方案采用可迭代落地的结构：

#### 1) contract vs infra/starter 的边界

- **contract（跨域稳定协议）：** `Result/ErrorCode`、事件 envelope/parser 等。特点：演进慢、版本治理严格、跨服务/跨模块共享。
- **infra/starter（横切基础设施）：** JWT decoder、actuator/prometheus basic-auth、安全链模板、Dubbo filters、outbox relay 等。特点：以 Spring Boot auto-config 交付，使用方“零重复代码”。
- **domain（业务域语义）：** 域错误码、域事件 payload/type/topic、域内业务逻辑。特点：只在域内拥有与演进，其他服务不直接依赖枚举语义。

#### 2) 模块拆分建议（Maven modules）

在现有模块体系上新增/拆分以下模块（命名可在实施阶段按仓库约定微调）：

1. **`contracts-core`（新增）**
   - 内容：`Result<T>`、`ErrorCode`、`CommonErrorCode`、`SimpleErrorCode`、`BusinessException` 等跨域稳定协议。
   - 目标：让 `*-api` 与服务模块依赖一个“更小、更稳定”的核心 contract，而不是整个 common。

2. **`contracts-event-core`（新增）**
   - 内容：`EventEnvelope`、`EventEnvelopeParser`、`UnknownEventAction` 等事件通用协议与解析治理。
   - 目标：将事件“框架协议”从 domain payload 中剥离出来，降低耦合。

3. **`infra-security-starter`（新增）**
   - 内容：
     - `JwtProperties`（统一 `security.jwt.hmac-secret`）
     - Servlet：`JwtDecoder` bean + `actuatorSecurityFilterChain`（prometheus basic-auth）
     - Reactive：`ReactiveJwtDecoder` bean + `actuatorSecurityWebFilterChain`
     - Prometheus basic-auth user details（Servlet/Reactive）
   - 目标：删除各服务重复实现与漂移风险点；服务侧保留自身 API 授权矩阵即可。

4. **`infra-outbox`（新增）**
   - 内容：`OutboxEvent/Mapper/Service/RelayJob/Properties` + MyBatis mapper XML + 统一 metrics 命名与 tags。
   - 目标：消除 `user/content/social` 多套 outbox 复制粘贴；策略通过配置治理而不是代码分叉。

5. **`infra-dubbo-starter`（新增，可选但推荐）**
   - 内容：`TraceContextDubboFilter`、`DubboMetricsFilter` 等 Dubbo 横切能力。
   - 目标：Dubbo 横切能力不与 web/事件/错误码混放在 common 中，按需依赖。

> 说明：现有 `common` 将被“瘦身”为仅保留少量真正通用、且尚未拆出的能力，并在迁移完成后评估是否继续保留或进一步拆分。

#### 3) 域错误码的归属策略

- 各域 `*ErrorCode` 移出 common，放到各自域模块（优先 `*-service`，如需对外作为契约则放到 `*-api`）。
- 禁止其他模块 import 域错误码枚举：
  - 跨服务错误处理统一通过 `Result.code/message` 透传与 code 段约定归因；
  - gateway/聚合服务不再依赖下游域枚举语义（避免“共享内核式耦合”）。

#### 4) 事件契约（payload/type/topic）按域拆分

- `EventEnvelope`/parser 属于“通用事件协议”，放入 `contracts-event-core`。
- `payload/type/topic` 由生产方域的 contract 提供（建议落在 `*-api`）：
  - 例如 social 产生的 `LikePayload/FollowPayload/BlockPayload` 归属 `social-api`；
  - content 产生的 `PostPayload/CommentPayload/...` 归属 `content-api`；
  - 消费方按需引入相应域 contract，避免“所有事件都依赖一个巨大 common”。

#### 5) 统一 actuator/prometheus basic-auth 与 JWT decoder

目标是**把重复代码变成统一 auto-config**：

- actuator 访问策略一致：
  - `/actuator/health`、`/actuator/info` permitAll
  - `/actuator/prometheus` 需要 `PROMETHEUS` 角色
  - 其他 actuator 端点 denyAll
- basic-auth 用户来源一致：`community.metrics.basic-auth.username/password`
- JWT HMAC secret 校验一致：
  - fail-closed（缺失或长度不足直接启动失败或报错）
  - Servlet/Reactive 两套 decoder 均覆盖

#### 6) outbox 统一与可配置化

- 统一核心状态机、claim 策略（含 `SKIP LOCKED` 可选）、退避重试、发送超时、指标口径；
- 通过 properties 提供可调参数，而不是让每个服务复制一份实现后各自改动；
- 统一指标命名：建议采用“同名指标 + tags(service/status/topic/outcome)”策略，避免指标名爆炸与漂移。

## Architecture Decision ADR

### ADR-1：从“mega common”转向“职责化模块 + starter”

**Context：** common 同时包含跨域契约与大量基础设施实现，且横切能力在多服务重复实现，导致耦合与漂移风险。

**Decision：** 拆分出 contracts/infra/starter 模块；域语义（错误码、事件 payload/type/topic）归属域模块；横切能力通过 starter 交付并删除重复实现。

**Rationale：**
- 依赖显式化：服务只依赖“需要的能力”，减少无关变更导致的全仓升级。
- 漂移收敛：重复实现集中到 starter，由配置治理差异。
- 架构护栏：模块边界天然限制“什么都往 common 放”。

**Alternatives：**
- 方案 A：保留 common 单模块但强分层 + 门禁（拒绝原因：common 仍是共享内核耦合点，且难以阻止继续膨胀）。
- 方案 B：引入外部 schema registry/多仓（拒绝原因：当前工程成本过高，超出本次治理目标）。

**Impact：**
- 会产生一段迁移期（依赖调整 + import 批量替换 + 删除重复代码）。
- 需要补齐最小契约测试与架构门禁，确保迁移不改变运行期语义。

## API Design

- 不新增对外业务 API。
- 可能新增/调整内部配置项（以保持统一为目标），并同步更新 `docs/` 与 `.helloagents/`。

## Data Model

- outbox 表结构需在 `user/content/social` 等 schema 中核对一致性。
- 若存在差异，迁移期通过补齐字段/索引或在 mapper 层做兼容处理（本方案优先选择“统一表结构”，避免长期兼容负担）。

## Security and Performance

- **Security：**
  - JWT secret 校验 fail-closed，避免 silent fallback；
  - actuator/prometheus 端点必须统一最小暴露面；
  - 迁移中严禁引入明文 token/key 的持久化或日志输出。
- **Performance：**
  - outbox relay 批量 claim + bounded 重试，避免 DB 头阻塞；
  - 指标采集不得影响主链路（失败时降级为 best-effort）。

## Testing and Deployment

- Testing：
  - 单测：starter/outbox/event contract 的最小覆盖；
  - 门禁：`mvn test` 全仓通过；
  - 关键行为：actuator 安全链、JWT decoder、outbox 状态机/重试策略的行为测试。
- Deployment：
  - 若调整配置键或指标名，同步更新 `deploy/` 与相关脚本/文档；
  - 迁移过程中保持服务启动与核心接口可用。

