# Change Proposal: Gateway 安全策略 SSOT 收敛（删除 legacy-matrix + CI 安全契约对齐）

## Requirement Background

当前实现中，网关侧同时存在两套安全策略：
- `gateway.security.mode=legacy-matrix`：在 gateway 侧维护业务路径级授权矩阵（`permitAll/hasRole/hasAnyRole` 等）。
- `transparent`：网关不再维护业务授权矩阵，仅保留最小边界护栏，将“业务授权 SSOT”下沉到各服务自身 `*SecurityConfig`。

同时，网关还承载多类横切能力（例如审计、限流、OriginGuard、统计采集等）。当“网关路径权限矩阵”和“各服务自身 SecurityConfig”长期并存时：
1. 新增/改动接口非常容易出现规则漂移（误放行/误拦截）。
2. 网关横切能力越多，发布频率与故障爆炸半径越集中（一个点影响全链路）。

因此需要明确安全策略 SSOT，并通过自动化对齐检查阻断漂移，让网关回归“薄网关 / 降爆炸半径”的定位。

## Change Content
1. **删除 `legacy-matrix`**：移除网关侧业务路径级授权矩阵实现与配置口径，避免“双模式”成为永久治理债务。
2. **网关收敛为透明模式**：网关仅保留最小边界护栏：
   - `/internal/**` 显式拒绝（denyAll）
   - `/api/ops/**` 仅允许 `ADMIN`（双保险，防误触/防误暴露）
   - 其余 `/api/**` 与 `/files/**` 等业务请求不在网关做授权矩阵（permitAll 透明转发）
3. **迁移期增加 CI 安全契约对齐检查（阻断型）**：在 `mvn test` 中补齐/新增契约测试，至少覆盖：
   - 公开 GET 白名单：`/api/posts**`、`/api/search/posts`、`/api/users/*`、`/api/categories**`、`/api/tags**`、`/files/**`、`/api/likes/count*`、`/api/follows/*` 等
   - 管理/治理接口：`/api/ops/**`、`/api/users/admin/**`、`/api/moderation/**`、`/api/analytics/**`、`/api/posts/*/(top|wonderful|delete)` 等
4. **同步清理文档与配置模板**：移除 `gateway.security.mode` 相关说明与示例，避免使用方误配。

## Impact Scope
- **Modules:** gateway、user-service、content-service、social-service、analytics-service、ops-service、search-service、deploy/docs、helloagents 知识库
- **Files (high-level):**
  - `gateway/src/main/java/.../GatewaySecurityConfig.java`
  - `gateway/src/main/resources/application.yml`
  - `deploy/nacos-config/gateway.yaml`
  - `docs/ARCHITECTURE.md`
  - `helloagents/wiki/modules/gateway.md`
  - 各服务 `src/test/java/.../*Security*Contract*Test.java`（对齐检查）
- **APIs:** 无新增/变更（仅调整安全策略与验证）
- **Data:** 无变更

## Core Scenarios

### Requirement: gateway-security-ssot
**Module:** gateway
网关不再承载业务授权矩阵，安全边界收敛为最小护栏，降低误配爆炸半径。

#### Scenario: internal-deny
任意请求命中 `/internal/**`：
- 期望：网关侧拒绝（不允许对外暴露 internal 入口）

#### Scenario: ops-admin-gate
任意请求命中 `/api/ops/**`：
- 期望：未登录返回 401；非 ADMIN 返回 403；ADMIN 通过安全链路（后续返回由路由/下游决定）

#### Scenario: transparent-forward
其余业务请求（例如公开 GET 白名单）：
- 期望：网关不在此处做 401/403 拦截（透明转发；具体授权由下游各服务 SSOT 决定）

### Requirement: service-security-ssot
**Module:** user-service/content-service/social-service/search-service/analytics-service/ops-service
各服务 `*SecurityConfig` 成为业务授权 SSOT，确保“公开入口保持公开、管理入口必须收敛”。

#### Scenario: public-get-whitelist
公开 GET 白名单（按服务拆分）：
- 期望：匿名可访问（不应返回 401/403；成功语义由业务决定）

#### Scenario: privileged-apis
管理/治理接口：
- 期望：必须鉴权并校验角色（未登录 401；角色不匹配 403）

### Requirement: ci-security-contract
**Module:** multi-module tests
通过 CI 契约测试阻断“规则漂移”：
- 期望：当任一公开 GET 被误拦截、或任一管理接口被误放行时，`mvn test` 失败并阻断合入/发布。

## Risk Assessment
- **Risk:** 删除 `legacy-matrix` 后，网关侧不再兜底拦截业务接口；若某服务 `*SecurityConfig` 漏配，可能造成误放行。
- **Mitigation:**
  - 用“服务侧安全契约测试”覆盖关键公开/管理入口，作为发布门禁（CI 阻断）。
  - 网关保留旁路防护能力（限流/OriginGuard/审计）与“运维入口收口”（`/api/ops/**`）。
  - 应急策略优先使用网关 `blocked-path-patterns` 进行临时 404 隐藏高风险入口，并通过回滚代码变更恢复（不再依赖 legacy-matrix 双模式）。

