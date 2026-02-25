# Change Proposal: 后端架构治理（5项问题：社交持久化 / 事件可靠性 / 内部调用韧性 / 网关统计链路 / internal-token 安全）

## Requirement Background
当前后端为 Spring Boot 3 微服务架构（gateway + 多业务服务），已具备统一 Result、错误码、Kafka 基线与部分内部调用治理能力，但在“数据可靠性、跨服务一致性、安全边界与韧性”方面仍存在系统性缺口，主要集中在以下 5 类问题：

1. **社交域默认值与职责边界仍存在误配风险（social-service）**：代码层面同时提供 DB/Redis/Memory 三套存储实现，其中 Redis 实现使用 `matchIfMissing=true`（缺省兜底）。虽然当前配置已显式 `social.storage=db`，但一旦配置缺失/被覆盖（Nacos 漂移、profile 误配），可能无意启用 Redis-only 模式；同时缺少“DB 为 SSOT、Redis 仅做可选缓存”的明确约束与运维说明，导致可靠性与性能优化难以按统一标准治理。
2. **事件生产端可靠性未形成“默认安全态”（content/social 事件生产）**：content-service / social-service 已具备 Outbox 能力，但部署侧默认 `outbox.enabled=false`，关闭时仍依赖 after-commit best-effort 直发 Kafka，发送失败存在丢消息窗口；可靠投递未在默认链路上统一，导致最终一致性链路治理难以落地。
3. **跨服务同步调用仍存在“契约不一致 + 走公共 API”现象（internal clients）**：部分内部 client 已统一了 timeout/traceId/指标，但错误码透传、降级语义与配置命名仍存在差异；尤其在 user-service 中仍存在以 Authorization 调用 `/api/**` 的跨服务聚合（而非 internal-token + internal API），容易造成权限耦合、调用链不清晰与排障成本上升。
4. **网关统计采集链路仍有资源与一致性隐患（gateway → analytics-service）**：当前 gateway 侧 UV/DAU 采集已引入 timeout/并发上限与指标，但仍使用进程内静态 Set 做“当天去重”（多实例不共享、内存高基数风险、阈值清空导致短时间重复采集）；并且对下游调用的 traceId 透传不明确，影响链路可观测。
5. **internal-token 的“最小爆炸半径”仍未闭环（shared secret / 配置治理）**：`InternalTokenFilter` 已支持按服务 segment 的 token 与 previous 轮转窗口，但多处配置仍允许 fallback 到全局 `INTERNAL_TOKEN`（或 client 侧默认读取 `INTERNAL_TOKEN`），一旦泄露会扩大影响范围；同时缺少轮转/回滚 runbook 与启动期校验策略，存在误配与运维风险。

## Change Content
1. **社交域 SSOT 治理：固化 DB 默认值 + 明确 Redis 职责**
   - 固化 MySQL 为默认 SSOT：避免配置缺失时误落到 Redis-only。
   - Redis 作为可选缓存/加速层（明确开关与语义），不再作为“缺省存储兜底”。
2. **事件可靠性统一：默认开启 Outbox（保留灰度/回滚开关）**
   - 将 Outbox 从“可选能力”提升为“默认安全态”（部署侧默认开启，保留回滚到 after-commit 的开关）。
   - 提供可观测指标与失败可视化（失败数量、重试次数、投递延迟），并补齐运维查询/重放能力。
3. **内部调用治理升级：统一 client 契约与降级语义**
   - 统一配置结构（base-url / timeout / internal-token / fail-open 策略），减少硬编码与不一致。
   - 统一错误映射与指标：成功/失败/超时/降级次数与耗时，避免“静默失败”与错误码丢失。
   - 逐步收敛：优先治理 user-service 的跨服务聚合路径（从 /api+Authorization 收敛到 internal API）。
4. **网关统计链路治理：有界化 + 明确超时/并发限制 + traceId 透传**
   - 移除或替换进程内静态集合为“有界 TTL 缓存/采样策略”（多实例可控、内存可控）。
   - 明确下游调用的 timeout/并发限制与指标语义，并补齐 traceId 透传。
   - 预留演进路径：gateway 发送访问事件到 Kafka，由 analytics-service 消费（彻底从在线链路剥离）。
5. **internal-token 安全加固：分级、轮转与最小爆炸半径**
   - 逐步收敛为“按服务 token”（必要时按环境/按调用方进一步细分）。
   - 支持 token 轮转（current + previous）与灰度切换（不中断调用）。
   - internal 保护统一下沉到 `InternalTokenFilter`，并通过配置治理/校验减少全局 token 兜底带来的爆炸半径。

## Impact Scope
- **Modules:**
  - social-service（存储模式默认值治理、Outbox 开关与 internal API）
  - common（internal-token/内部调用基础能力增强）
  - user-service（跨服务聚合调用收敛）
  - gateway（analytics 采集链路治理）
  - analytics-service（可选：事件化采集与批处理能力）
  - deploy（Nacos 配置与开关、运行手册）
  - .helloagents/wiki（架构/运维 Runbook 更新）
- **APIs:**
  - 可能新增/补齐 internal 运维 API（例如 outbox 观测/重放），需严格 internal-token 保护。
  - 对外 API（/api/**）尽量保持兼容，避免破坏前端调用。
- **Data:**
  - social-service 的关系表与 outbox 表已存在于部署脚本中，本次以“默认开关/行为语义/可运维性”为主，按需补充索引与参数。

## Core Scenarios

### Requirement: R1-social-persistence-ssot
<a id="r1-social-persistence-ssot"></a>
**Module:** social-service / deploy

社交关系数据（点赞/关注/拉黑）具备可恢复性与确定性默认值：MySQL 为 SSOT，Redis 不作为缺省存储兜底；若启用 Redis 缓存，则 Redis 故障或数据丢失时可从 MySQL 重建。

#### Scenario: R1S1-redis-loss-can-rebuild
<a id="r1s1-redis-loss-can-rebuild"></a>
当启用 Redis 缓存且 Redis 故障/误删导致缓存数据丢失：
- 可通过脚本/内部运维接口从 MySQL 重建 Redis 缓存与必要的计数。
- 重建过程可观测（耗时/数量/失败）且可中断与重试。

#### Scenario: R1S2-read-write-consistency
<a id="r1s2-read-write-consistency"></a>
在高并发点赞/关注写入下：
- DB 写入为准（SSOT），Redis 缓存允许短暂滞后但可最终一致。
- 不出现长期“关系丢失/重复”的业务错误（幂等/唯一约束兜底）。

---

### Requirement: R2-social-outbox-reliable-produce
<a id="r2-social-outbox-reliable-produce"></a>
**Module:** social-service / content-service / deploy

事件生产可靠投递：DB 事务提交后，Kafka 投递失败不丢事件，具备重试与失败归档策略；Outbox 默认开启（保留灰度/回滚开关）。

#### Scenario: R2S1-kafka-down-not-lost
<a id="r2s1-kafka-down-not-lost"></a>
当 Kafka 短暂不可用/抖动：
- 写请求仍可成功提交 DB（在允许异步的场景），事件进入 outbox 等待重试。
- Kafka 恢复后可自动补发，不产生“幽灵事件”（未提交却投递）或“永久丢失”（已提交但未投递）。

#### Scenario: R2S2-retry-and-failure-visible
<a id="r2s2-retry-and-failure-visible"></a>
当持续投递失败（超过最大重试次数）：
- outbox 事件进入 failed 状态并可被观测（指标/日志/查询接口）。
- 支持人工重放（受 internal-token 保护）。

---

### Requirement: R3-internal-client-governance
<a id="r3-internal-client-governance"></a>
**Module:** common / user-service / auth-service / content-service / message-service / search-service / gateway

跨服务同步调用具备统一治理：确定性超时、可观测指标、统一错误映射与明确降级语义，避免级联慢请求与排障困难。

#### Scenario: R3S1-timeout-fast-fail
<a id="r3s1-timeout-fast-fail"></a>
当下游抖动/不可用：
- 调用方在阈值内返回（不被挂死）。
- 在线链路与后台任务采用不同超时默认值（在线更严格）。

#### Scenario: R3S2-explicit-fallback-policy
<a id="r3s2-explicit-fallback-policy"></a>
对每个跨服务调用明确策略：
- 哪些可降级（例如读路径计数类）与降级默认值。
- 哪些必须 fail-closed（例如写路径权限/风控校验）。

#### Scenario: R3S3-observability
<a id="r3s3-observability"></a>
出现失败时：
- 指标可区分 success/error/timeout/degraded。
- 日志带 traceId 与关键 tags（client/api/outcome），便于定位。

---

### Requirement: R4-gateway-analytics-pipeline
<a id="r4-gateway-analytics-pipeline"></a>
**Module:** gateway / analytics-service

网关统计采集链路有界、可控、可演进：不因异常流量导致内存膨胀或无界下游调用；失败可观测；并预留异步事件化演进路径。

#### Scenario: R4S1-bounded-memory
<a id="r4s1-bounded-memory"></a>
当 UV/DAU 基数异常升高：
- 网关侧不出现无界内存膨胀（有界缓存/采样/限流）。
- analytics-service 仍能正确去重与聚合（Redis/HLL/bitset）。

#### Scenario: R4S2-timeout-and-limit
<a id="r4s2-timeout-and-limit"></a>
当 analytics-service 抖动：
- 网关调用具备明确超时与并发限制，不影响主业务请求链路。
- 失败不完全“吞掉”，至少记录指标用于告警与排障。

#### Scenario: R4S3-userid-parse-safety
<a id="r4s3-userid-parse-safety"></a>
当 JWT subject 非数字或缺失：
- 网关不触发 DAU 记录或进行安全的转换处理，避免大量 400 静默失败。

---

### Requirement: R5-internal-token-security
<a id="r5-internal-token-security"></a>
**Module:** common / all services / deploy

internal-token 具备更强安全边界：最小爆炸半径、可轮转、配置不易漂移，并统一由 Filter 层强制校验。

#### Scenario: R5S1-service-scoped-token
<a id="r5s1-service-scoped-token"></a>
当某个服务 token 泄露：
- 影响范围仅限该服务的 internal API（而非所有 internal API）。
- 可快速更换该服务 token，不影响其他服务。

#### Scenario: R5S2-rotation-without-downtime
<a id="r5s2-rotation-without-downtime"></a>
当需要轮转 token：
- 支持 current + previous 同时生效的灰度窗口，避免调用中断。
- 轮转有明确操作手册与回滚路径。

#### Scenario: R5S3-no-manual-drift
<a id="r5s3-no-manual-drift"></a>
当新增 internal controller：
- 无需手写 token 校验逻辑，Filter 自动兜底，避免遗漏与漂移。

## Risk Assessment
- **Risk：配置漂移导致“模式误启用/能力未开启”** → Mitigation：默认值固化（DB/outbox）、启动期校验、Nacos 配置基线与 runbook。
- **Risk：Outbox 事件重复投递** → Mitigation：消费者幂等（insert-first + 唯一约束）+ 业务幂等 key 设计 + 指标告警。
- **Risk：引入/开启 Outbox 后吞吐下降** → Mitigation：批量 claim、合理 batch-size、并行度与指标驱动调参。
- **Risk：internal-token 误配置导致 internal API 不可用** → Mitigation：轮转窗口（previous token）、启动期校验与 runbook；灰度环境先演练。
- **Risk：网关采集链路放大下游压力** → Mitigation：采样/限流/超时/并发限制；必要时演进为 Kafka 事件化采集。
