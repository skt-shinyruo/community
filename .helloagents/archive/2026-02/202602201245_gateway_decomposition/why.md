# Change Proposal: Gateway 边界收敛与职责拆分（降爆炸半径）

## Requirement Background

当前 gateway 职责出现“边界层变胖”的趋势：除了路由与协议层能力，gateway 还承载了路径级权限矩阵与异常收敛、全局限流、审计、统计采集等多类能力。随着更多“业务相关能力”下沉到 gateway，gateway 正在演变为关键单体：一旦配置/实现变更出现问题，爆炸半径覆盖全站（所有 `/api/**` 流量都受影响）。

该问题的核心不是“gateway 功能多”，而是：
1. **业务策略（授权矩阵、业务限流口径、统计口径）与边界基础设施（路由、CORS、trace、统一错误协议）混在同一发布单元**；
2. **gateway 对业务路径的显式清单依赖** 会导致“新增/调整业务接口 → 必须改 gateway”，形成强耦合与高频变更；
3. 可丢弃链路（统计、审计）一旦实现/配置不当，可能反向拖垮主转发链路（线程/内存/队列/外部依赖）。

## Change Content

1. **定义并落地 “Gateway 边界契约”**：明确 gateway 的“允许职责清单/禁止清单”，并将其固化为可测试、可回滚的配置与门禁。
2. **授权矩阵责任下沉到服务侧（SSOT）**：gateway 不再维护业务路径级 permit/role 清单；业务授权由各服务的 `SecurityConfig` 负责，gateway 仅保留极小的“边界护栏”（例如显式拒绝 `/internal/**`、保护运维入口）。
3. **运维平面隔离（推荐）**：将高成本/高风险的运维能力从主 gateway 进程拆出（例如独立 `ops-service` 或 `ops-gateway`），避免运维代码/依赖变更影响主转发面。
4. **可丢弃链路旁路化（分阶段）**：审计/统计采集默认不进入主转发关键路径；优先采用结构化日志/事件驱动的方式解耦（analytics-service 不再成为 gateway 的同步/半同步依赖）。
5. **限流分层**：gateway 仅保留“边界型限流”（登录/注册/验证码等 anti-abuse）；业务敏感限流迁移到对应服务或默认关闭（通过配置中心灰度开启）。

## Impact Scope
- **Modules:**
  - gateway（安全策略、路由、全局 filter、配置与测试）
  - 各业务服务（auth/user/content/social/message/search/analytics）：确保服务侧授权为 SSOT，并补齐安全契约测试
  - （可选）新增 `ops-service` / `ops-gateway` 模块
- **Files (expected):**
  - `gateway/src/main/java/.../GatewaySecurityConfig.java` 及相关测试
  - `gateway/src/main/resources/application.yml`（路由与能力开关）
  - 各服务 `*SecurityConfig.java`（必要时仅做一致性补齐）
  - `deploy/docker-compose.yml` / `deploy/nacos-config/*`（如引入新 ops 服务）
  - knowledge base：`.helloagents/modules/gateway.md`、`.helloagents/arch.md`（边界与运行手册更新）
- **APIs:**
  - 对外 `/api/**` 行为保持兼容（公开接口仍可匿名访问；鉴权接口仍可匿名访问）
  - 运维接口 `/api/ops/**` 行为保持兼容（但实现/部署可能迁移到 ops 平面）
- **Data:**
  - 预期不引入强一致业务数据变更；若旁路采集改为事件驱动，可能新增 Kafka topic（不影响核心写路径）

## Core Scenarios

### Requirement: R1 Gateway Boundary Contract
**Module:** gateway

#### Scenario: R1S1 Public Traffic Does Not Depend On Gateway Allowlist
网关不再维护大量“公开 GET 白名单”，公开接口的可访问性由服务侧定义。
- 预期结果：新增公开读接口时，不需要改 gateway 即可对外可用（gateway 透明转发）。
- 预期结果：gateway 误配 matcher 不再导致公开接口全站 401/403。

#### Scenario: R1S2 Internal Paths Are Always Denied At Gateway
对 `/internal/**` 保持显式拒绝，避免误配路由导致 internal 能力对外暴露。
- 预期结果：任何 `/internal/**` 访问在 gateway 层直接拒绝。

### Requirement: R2 Authorization Responsibility Shift (Service As SSOT)
**Module:** gateway + *-service

#### Scenario: R2S1 Business Authorization Moves To Service Side
业务授权（如 moderation、管理员操作）在服务侧强制执行，gateway 不再承载业务矩阵。
- 预期结果：服务侧对敏感接口返回 401/403 的行为可通过单测/契约测试验证。
- 预期结果：gateway 仅对极少数边界入口（例如 `/api/ops/**`）做额外保护（可选）。

### Requirement: R3 Ops Plane Isolation
**Module:** gateway + (new) ops-service

#### Scenario: R3S1 Ops Changes Do Not Impact Main Gateway Forwarding
运维能力（reindex/replay/backfill 等）与主转发面分离部署。
- 预期结果：ops 平面发布失败/异常时，不影响 `/api/**` 主业务路由与转发。
- 预期结果：运维入口具备“一键关闭”能力（配置中心/blocked list/灰度）。

### Requirement: R4 Observability Offload (Audit/Analytics)
**Module:** gateway + analytics-service

#### Scenario: R4S1 Analytics Is Best-Effort And Non-Blocking
统计采集属于可丢弃链路：任何情况下都不影响主请求成功率与延迟。
- 预期结果：analytics-service 不可用时，gateway 仅丢弃采集并记录可观测指标。
- 预期结果：采集口径演进优先通过旁路（日志/事件）完成，减少 gateway 发布频率。

### Requirement: R5 Rate Limit Layering
**Module:** gateway + *-service

#### Scenario: R5S1 Edge Anti-Abuse Remains At Gateway
登录/注册/验证码等边界型 anti-abuse 仍由 gateway 承担（更靠近攻击面）。
- 预期结果：Redis 抖动时遵循既定降级策略（fail-open/fail-closed 可配置），并可观测。

#### Scenario: R5S2 Business Rate Limit Moves Closer To Domain
发帖/评论/点赞等业务敏感限流尽量迁移到对应服务侧（或默认关闭）。
- 预期结果：限流策略变更只影响单域服务，不放大全站爆炸半径。

## Risk Assessment
- **Risk:** gateway 透明化后，若某个服务误配为 `permitAll`，会扩大安全风险。  
  **Mitigation:** 建立“服务侧安全契约测试”与 smoke 门禁；关键接口在服务侧强制 role 校验，并在 CI 中验证。
- **Risk:** 透明化后，网关不再提前拒绝未登录请求，服务侧负载可能上升。  
  **Mitigation:** 通过网关/服务监控观测 401 占比与 QPS 变化；必要时仅对高频写接口做网关级快速拒绝（但不维护业务矩阵清单）。
- **Risk:** 运维能力拆分会增加部署复杂度（新服务、路由、配置）。  
  **Mitigation:** 分阶段迁移；保留旧入口一段时间并提供回滚路径；使用 blocked list 作为应急熔断。
