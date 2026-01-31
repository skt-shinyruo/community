# Change Proposal: 微服务边界与韧性治理（Docker Compose 部署）

## Requirement Background

本仓库为 Spring Boot 3 + Java 17 的多微服务工程（`gateway` 统一对外入口 + `*-service` 业务域服务 + `common` 基线库 + `frontend` SPA），本地与目标部署形态以 **Docker Compose** 为主（明确不使用 Kubernetes / service mesh）。

代码与配置现状已具备部分工程化基线（例如 `X-Trace-Id`、统一 `Result<T>`、`/internal/**` + `X-Internal-Token`、Kafka DLQ/幂等/Outbox 等），但仍需要把“靠约定/自觉”的部分升级为“靠默认策略/机制/测试”的体系化治理，重点落地以下 5 点：

1. **边界硬化（External / Internal / Ops 三分）**：避免 internal 命名被误认为“只在内网”，降低误暴露与授权复杂度。
2. **数据所有权与最小权限**：用 DB 权限硬约束阻断跨域直读直写，保持微服务自治。
3. **同步调用韧性基线**：跨服务 HTTP 调用统一具备超时、错误语义保真、降级口径与可观测，降低级联故障风险。
4. **异步一致性闭环**：Outbox/幂等/DLQ 形成“可运营”的闭环（可告警、可回放、可审计）。
5. **契约与可观测性体系化**：DTO/事件契约可回归；可观测性从 traceId 日志相关性升级为可选的 tracing span（仍以 Compose 部署）。

## Change Content
1. **对外/对内/运维入口三分统一**
   - External：`/api/**`（用户接口）
   - Ops：`/api/ops/**`（对外运维入口，强保护）
   - Internal：`/internal/**`（仅服务间调用，默认不经公网暴露）
   - 对历史路径（如 `/api/*/internal/**`）提供短期兼容与明确弃用窗口。
2. **Compose 层网络隔离 + 默认拒绝**
   - 仅对外暴露 `frontend` 与 `gateway`（以及必要的观测端口）。
   - 其他服务不映射 `ports` 到宿主机，仅在 compose 私有网络互通；内部接口仍由 `X-Internal-Token` fail-closed 兜底。
3. **DB 最小权限落地（同实例，多账号，多 schema）**
   - 同一 MySQL 实例下按服务拆 schema（已有），并进一步按服务拆 DB 用户并授予最小权限（落实到初始化脚本与配置）。
4. **跨服务同步调用“统一三件套”**
   - 超时：connect/read 必须确定性（在线链路更严格，后台任务可更宽松）。
   - 语义：错误映射与降级口径统一（success/error/timeout/degraded/forbidden），关键 internal API 不允许降级（fail-closed）。
   - 可观测：统一 client 指标与 traceId 透传，便于定位级联故障。
5. **异步链路默认可靠 + 可运营**
   - 生产侧默认启用 outbox relay（可配置回滚到 after-commit 直发但不推荐）。
   - 消费侧统一：有限重试 → DLQ，且 DLQ 有回放脚本与 Runbook（含风控与审计）。
6. **契约与可观测性升级（Compose 友好）**
   - 契约测试：DTO 字段白名单 + 事件 envelope unknown/version handling。
   - Tracing：可选引入 Micrometer Tracing + OTLP（docker compose 加一个轻量 tracing backend，如 Tempo/Jaeger/Zipkin 其一），与现有 Prometheus/Loki/Grafana 并存。

## Impact Scope
- **Modules:** `deploy/`、`gateway/`、`common/`、各 `*-service/`、`frontend/`、`docs/`、`helloagents/wiki/`
- **Files:** 主要为配置与少量入口路由/命名变更；新增/更新运维与回放文档；补齐回归测试
- **APIs:** 新增或收敛 `/api/ops/**`；收敛对外 internal 命名入口；`/internal/**` 保持为服务间调用专用
- **Data:** MySQL 最小权限账号；Kafka topic/DLQ 策略保持但强化默认运营闭环；可选新增 tracing 数据流

## Core Scenarios

### Requirement: R1-边界三分（External/Internal/Ops）
<a id="requirement-r1-边界三分externalinternalops"></a>
**Module:** gateway / deploy / docs
用命名 + 默认策略强约束边界：internal 不对外；ops 有更强保护；external 对用户友好。

#### Scenario: S1-internal-默认不可对外暴露
<a id="scenario-s1-internal-默认不可对外暴露"></a>
当外部用户尝试访问任何 `/internal/**`：
- 必须被拒绝（网络层不暴露 + gateway 明确 deny），且有审计/traceId 便于排障。

#### Scenario: S2-ops-入口强保护（双钥 + 可审计）
<a id="scenario-s2-ops-入口强保护双钥--可审计"></a>
当管理员触发高风险运维操作（如 reindex、outbox replay）：
- 必须同时满足：ADMIN（或接口要求角色）+ `X-Ops-Token` + allowlist/频率限制（默认拒绝）。

### Requirement: R2-数据所有权与最小权限（DB Access Policy）
<a id="requirement-r2-数据所有权与最小权限db-access-policy"></a>
**Module:** deploy / 各 service / docs
每个服务只拥有本域 schema 的 DB 权限，跨域读取走 internal API。

#### Scenario: S1-跨域直读被 DB 权限阻断
<a id="scenario-s1-跨域直读被-db-权限阻断"></a>
当某服务尝试直接访问非本域 schema：
- SQL 必须失败（权限不足），避免“分布式单体”悄然形成。

#### Scenario: S2-跨域读取走 internal API（契约可演进）
<a id="scenario-s2-跨域读取走-internal-api契约可演进"></a>
当 search-service 需要读取帖子用于 reindex：
- 只能调用 content-service 的 `/internal/content/posts`，字段白名单可演进且有回归测试兜底。

### Requirement: R3-同步调用韧性基线（无 mesh）
<a id="requirement-r3-同步调用韧性基线无-mesh"></a>
**Module:** common / 各 service
跨服务 HTTP 调用统一做到：确定性超时 + 语义化错误 + 可观测指标；并明确哪些接口可降级、哪些必须 fail-closed。

#### Scenario: S1-下游不可用时确定性 fast-fail
<a id="scenario-s1-下游不可用时确定性-fast-fail"></a>
当下游服务不可用/网络抖动：
- 调用方在超时阈值内返回，并按统一指标口径记录 outcome。

#### Scenario: S2-可降级接口返回默认值且可观测
<a id="scenario-s2-可降级接口返回默认值且可观测"></a>
当聚合读接口依赖的“非关键”下游失败：
- 允许降级返回默认值，并记录 degraded 指标；关键鉴权类 internal API 不允许降级（fail-closed）。

### Requirement: R4-异步一致性闭环（Outbox + 幂等 + DLQ 可运营）
<a id="requirement-r4-异步一致性闭环outbox--幂等--dlq"></a>
**Module:** common / content-service / social-service / user-service / message-service / search-service / scripts / docs
生产默认可靠投递；消费端幂等与 DLQ 策略统一；并形成回放与告警闭环。

#### Scenario: S1-提交成功但发送失败不丢事件
<a id="scenario-s1-提交成功但发送失败不丢事件"></a>
当 DB 已提交但 Kafka 发送失败：
- 事件必须进入 outbox 并可重试投递；积压可观测并可告警。

#### Scenario: S2-消费失败进入 DLQ 且可回放
<a id="scenario-s2-消费失败进入-dlq-且可回放"></a>
当消费端反序列化或业务处理失败：
- 有界重试后进入 `<topic>.dlq`，并可按 runbook 安全回放到目标 topic。

### Requirement: R5-契约与可观测性体系化（Compose 友好）
<a id="requirement-r5-契约与可观测性体系化compose-友好"></a>
**Module:** common / gateway / 各 service / docs / deploy
以契约测试固化 DTO/事件契约；可选引入 tracing span（OTLP）并以 docker compose 方式部署追踪后端。

#### Scenario: S1-HTTP + Kafka 端到端可追踪（可选 tracing）
<a id="scenario-s1-http--kafka-端到端可追踪可选-tracing"></a>
当一次用户请求触发 HTTP 写入与 Kafka 消费：
- 能通过 traceId 串联日志；若启用 tracing，则可定位到具体 span 的耗时与错误。

#### Scenario: S2-DTO 字段白名单回归
<a id="scenario-s2-dto-字段白名单回归"></a>
当后续重构 entity/联表扩展：
- 公共读接口不得泄露治理字段，契约测试失败即阻断回归。

## Risk Assessment
- **风险：路径与权限调整可能影响前端/联调。**
  - **缓解：** 保留短期兼容路径 + 明确弃用窗口；用回归测试锁定权限矩阵与关键入口。
- **风险：DB 最小权限启用后暴露历史越界访问。**
  - **缓缓：** 先在 dev/staging 打开并修复越界；确保跨域读取改走 internal API。
- **风险：可观测性新增 tracing 组件带来部署复杂度。**
  - **缓解：** 作为可选项分阶段引入；默认仍以现有 Prometheus/Loki/Grafana + traceId 日志相关性为基线。

