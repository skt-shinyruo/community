# Change Proposal: Redis 关键链路去硬依赖（幂等/会话/写入口可用性）

## Requirement Background
当前系统中，Redis 已成为多条关键链路的“硬依赖”，并且在多个位置采取 **fail-closed**（Redis 异常直接 503）策略，导致 Redis 抖动/不可用会被放大为：
- 用户侧强感知故障：不能发帖/不能评论/不能发私信，甚至登录/刷新链路异常；
- 故障半径过大：单点基础设施波动影响多个业务域与网关入口；
- SLO 难以稳定：在峰值/抖动场景下可用性与尾延迟劣化明显，且恢复时间不可控。

已定位的典型放大路径：
- **写接口幂等 required fail-closed：** `IdempotencyGuard.executeRequired(...)` 在 store 异常时返回 503，发帖/评论/私信均走 required 幂等。
- **登录链路默认 Redis 存储：** refresh/captcha/password-reset 等默认 Redis 实现，Redis 异常将上浮为 503。
- **网关限流依赖 Redis 且 fail-closed：** Redis 异常时网关直接返回 503，进一步前置并扩大影响面（包括写入口）。

## Change Content
1. **幂等（强一致 SSOT）：** 将 HTTP 写接口幂等从 Redis 迁移到 **MySQL（每服务独立 schema）**，通过唯一约束实现 insert-first 的幂等锁与状态机；Redis 仅保留为可选加速层（非必需）。
2. **会话/刷新（强一致 SSOT）：** 将 refresh token 存储从 Redis 迁移到 **MySQL（建议身份域/会话域 SSOT）**，并将 refresh token 以 hash 形式落库（降低凭据泄漏风险），支持旋转刷新与家族级撤销语义。
3. **登录安全（可降级）：** captcha、password reset、登录失败计数/限流在存储故障时提供明确降级策略（不再以 503 阻断登录主链路；降级必须可观测、可开关、可回滚）。
4. **网关隔离与降级：** 网关限流在 Redis 异常时进入“降级模式”（本地有界限流/或按规则 fail-open），确保核心写入口不再因 Redis 故障被统一 503；同时保留指标与审计以支撑风险控制。

## Impact Scope
- **Modules：**
  - `common`（幂等抽象与实现、错误语义与指标口径）
  - `content-service`（发帖/评论写入口幂等落库、schema 变更）
  - `message-service`（私信写入口幂等落库、schema 变更）
  - `auth-service`（refresh token 存储迁移、captcha/限流降级策略）
  - `gateway`（限流 Redis 异常降级策略）
  - `user-service`（若采用“身份域托管会话存储”的实现路径：新增内部会话存储接口与表）
  - `deploy`（MySQL 初始化脚本与环境配置说明）
- **Files（预计）：**
  - `common/src/main/java/com/nowcoder/community/common/idempotency/*`
  - `content-service/src/main/resources/application.yml`
  - `message-service/src/main/resources/application.yml`
  - `auth-service/src/main/resources/application.yml`
  - `gateway/src/main/resources/application.yml`
  - `deploy/mysql-init/*`
  - `*/src/test/resources/schema.sql`
- **APIs（可能新增/变更）：**
  - `user-service` 内部接口（用于 refresh token 的 store/find/revoke/revokeFamily，若采用内部托管）
  - `auth-service` 配置项：refresh store 新增 `db`/`dual` 等模式（用于平滑迁移）
- **Data（新增表/索引）：**
  - `community_content.http_idempotency`（或等价命名）
  - `community_message.http_idempotency`（或等价命名）
  - `community.auth_refresh_token`（或等价命名；若托管于 identity schema）

## Core Scenarios

<a id="req-idempotency-db"></a>
### Requirement: 写接口幂等（SSOT=MySQL，不因 Redis 故障 503）
**Module:** common + content-service + message-service
将 required 幂等的状态（PROCESSING/SUCCESS + TTL）落入 MySQL，并通过唯一键保证“同一 user + operation + key”只产生一次副作用。

#### Scenario: Redis 故障时仍可发帖/评论/私信（携带 Idempotency-Key）
条件：Redis 故障或抖动（超时/连接失败）。
- 发帖/评论/私信写入口不再因为幂等存储 503（幂等命中/并发冲突仍可返回 409/复用响应）。
- 幂等 store 异常仅与数据库可用性强绑定：DB 可用则链路可用。

<a id="req-refresh-db"></a>
### Requirement: 刷新会话（SSOT=MySQL，支持旋转刷新与家族级撤销）
**Module:** auth-service（+ user-service 若采用内部托管）
refresh token 从 Redis 迁移到 MySQL，支持 rotate/revoke/revokeFamily 语义；对外协议保持不变（refresh cookie + access token）。

#### Scenario: Redis 故障时仍可 refresh（不强依赖 Redis）
条件：Redis 不可用，但 MySQL（身份域/会话域）可用。
- `/api/auth/refresh` 仍可完成刷新（读写 MySQL session store）。
- 支持 logout 的 family revoke（落库更新/删除）。

<a id="req-auth-degrade"></a>
### Requirement: 登录安全能力在存储故障时可降级（不阻断登录主链路）
**Module:** auth-service
captcha/登录失败计数/限流在存储不可用时应提供明确策略（例如：降低/关闭部分安全能力并强告警），避免把 Redis 故障放大成“无法登录”。

#### Scenario: Redis 故障时 login 仍可用（安全能力降级可观测）
条件：Redis 不可用。
- 登录链路不因限流计数/验证码存储异常直接 503。
- 降级模式下应打点（metrics）并可被告警捕获。

<a id="req-gateway-ratelimit-degrade"></a>
### Requirement: 网关限流在 Redis 故障时进入降级模式（不再统一 503 阻断写入口）
**Module:** gateway
网关限流仍保持默认安全态，但当 Redis 不可用时可按规则降级，确保核心写入口优先可用，同时保留观测与审计。

#### Scenario: Redis 故障时核心写入口仍可达（降级策略可配置）
条件：Redis 不可用。
- 写入口请求不再被网关统一 503 阻断（按配置进入 fail-open 或本地限流 fallback）。
- 所有降级应可观测：指标 + 日志 + traceId。

## Risk Assessment
- **Risk：数据模型与迁移复杂度上升。**
  - **Mitigation：** refresh token 采用双读/双写或灰度切换策略；幂等无需迁移历史数据；提供快速回滚开关。
- **Risk：安全语义取舍（限流/captcha 降级）。**
  - **Mitigation：** 降级策略按接口分级；保留告警与审计；必要时在边界层（WAF/网关）启用更严格的静态阈值兜底。
- **Risk：DB 压力与写放大。**
  - **Mitigation：** 幂等表按 key 主键访问，索引可控；定期清理过期记录；必要时增加 Redis 只读缓存（非必需）。

