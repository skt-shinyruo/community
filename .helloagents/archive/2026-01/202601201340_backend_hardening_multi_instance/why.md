# Change Proposal: 后端多实例上线加固（同站不同源 / HTTP / Docker Compose）

## Requirement Background

当前后端已具备 Spring Boot 3 + Spring Cloud Gateway + 多微服务的基本骨架，并且沉淀了统一返回结构 `Result<T>`、统一错误码、traceId、以及 Kafka+DLQ（部分服务）等基础能力。

但在“学习并且要上线 + 多实例”的目标下，现状仍存在若干会在上线后被放大成事故的风险：

1. **登录/刷新跨源的易错点较多**：前端与网关同站不同源（例如 `http://localhost:5173` → `http://localhost:12882`），refresh 走 cookie 的方案可行，但必须同时满足 CORS（允许 credentials + 精确 origin）、前端 `withCredentials`、以及网关 OriginGuard allowlist 等多处配置一致。
2. **auth-service 仍与 user 表强耦合**：`auth-service` 直接依赖 MySQL user 表，无法做到真正的无状态扩容与最小权限；同时“身份域数据所有权”不清晰。
3. **跨服务同步调用标准不统一**：`user-service` 已对下游调用做了超时/降级/可观测，但 `message-service -> user-service` 等链路尚未对齐，存在级联雪崩风险。
4. **Kafka 消费幂等存在进程内去重**：`content-service` 的 Kafka 消费逻辑使用 JVM 内存 `seenEventIds` 去重，多实例/重启/再均衡下不可靠。
5. **Kafka 失败处理不一致**：`message-service/search-service` 已具备统一 `DefaultErrorHandler + DLQ`，但 `content-service` 侧尚未对齐；失败可能表现为无限重试/阻塞或不可运营。
6. **事件链路 traceId 丢失**：HTTP 链路已有 `X-Trace-Id`，但 Kafka 消费端未把 envelope 的 `traceId` 注入 MDC/TraceId，线上排障成本高。
7. **密码哈希仍是 legacy（MD5+salt）**：符合学习/演示，但不符合“可上线”的最低安全基线；需要渐进升级到 BCrypt/Argon2。
8. **JWT 密钥治理存在漂移风险**：多服务分别读取不同 env key（AUTH/GATEWAY/JWT_HMAC_SECRET），环境配置稍有偏差会导致验签不一致或权限事故。
9. **事件投递仅解决“幽灵事件”**：`AfterCommit` 能避免“回滚仍发事件”，但无法保证“commit 后必达”，仍可能丢事件；需要规划 Outbox。

## Change Content

1. 统一“同站不同源 + cookie refresh”的 CORS/OriginGuard/前端 credentials 策略，保证多实例下登录/刷新/登出稳定可用。
2. 将身份域数据所有权收敛到 `user-service`：新增内部鉴权接口供 `auth-service` 调用，逐步让 `auth-service` 完全不连 MySQL。
3. 跨服务同步调用统一“确定性超时 + 指标 + 降级语义 + traceId 透传”基线。
4. Kafka 消费统一“幂等（持久化或天然幂等）+ 失败重试 + DLQ + 回放”闭环，并补齐消费端 traceId 注入。
5. 密码哈希升级为可上线基线（渐进迁移，避免一次性重置用户）。
6. JWT 密钥治理：先统一配置来源与命名，规划中期 RS256（私钥仅在 auth，其他服务公钥验签）。
7. 规划 Outbox（P1）以实现“commit 后必达”，提升最终一致可靠性。

## Impact Scope
- **Modules:** `gateway` / `auth-service` / `user-service` / `message-service` / `content-service` / `search-service` / `common` / `deploy` / `frontend`
- **APIs:**
  - 新增 `user-service` 内部接口：`/internal/users/**`（供 auth-service 使用）
  - 补齐 internal-token 统一校验（对 `/internal/**`）
- **Data:**
  -（可选）新增 content-service 消费幂等表（或 Redis 幂等键）
  -（P1）新增 Outbox 表与 relay 机制（content/social 等生产者）

## Core Scenarios

### Requirement: R1-cors-cookie-refresh
<a id="r1-cors-cookie-refresh"></a>
**Module:** gateway / auth-service / frontend

在“同站不同源 + HTTP”的约束下，保证 refresh cookie 方案可用且不引入明显 CSRF 回归。

#### Scenario: R1S1-fe-gateway-cookie-login-refresh
<a id="r1s1-fe-gateway-cookie-login-refresh"></a>
前端 `Origin=http://localhost:5173` 调用网关 `http://localhost:12882`：
- 登录成功后浏览器接收并保存 refresh cookie
- refresh 请求自动携带 refresh cookie，返回新的 access token
- logout 清理 refresh cookie
- CORS/OriginGuard 配置不互相冲突

#### Scenario: R1S2-origin-guard-allowlist
<a id="r1s2-origin-guard-allowlist"></a>
OriginGuard 仅对敏感接口生效（login/refresh/logout），且 allowlist 必须与前端 origin 一致：
- allowlist 正确时：放行
- allowlist 缺失/误配时：明确拒绝并可观测（避免 silent fail-open）

---

### Requirement: R2-auth-service-stateless-no-mysql
<a id="r2-auth-service-stateless-no-mysql"></a>
**Module:** auth-service / user-service / deploy

auth-service 逐步变为“无库编排服务”，所有身份数据（用户状态/密码校验/角色）由 user-service 作为 SSOT。

#### Scenario: R2S1-login-via-user-service-internal-auth
<a id="r2s1-login-via-user-service-internal-auth"></a>
auth-service 登录流程不再直连 MySQL：
- auth-service 调用 user-service internal API 完成用户名密码校验与状态校验
- auth-service 仅负责签发 access token、签发/旋转 refresh token（Redis 存储）

#### Scenario: R2S2-refresh-check-user-status
<a id="r2s2-refresh-check-user-status"></a>
refresh 过程中可校验用户状态（禁用/未激活）：
- user 被禁用后 refresh 立即失效并可被 family revoke
- 多实例下请求命中任一 auth 实例表现一致

---

### Requirement: R3-internal-api-hardening
<a id="r3-internal-api-hardening"></a>
**Module:** gateway / 各业务服务 / deploy

`/internal/**` 接口做到“默认安全”：即使未来新增 internal API，也不依赖开发者“记得在 controller 手工校验 token”。

#### Scenario: R3S1-internal-token-enforced
<a id="r3s1-internal-token-enforced"></a>
对所有 `/internal/**`：
- 必须校验 `X-Internal-Token`
- token 未配置/不匹配 → 明确返回 403
- 校验逻辑在 Filter/Interceptor 层统一实现，避免遗漏

#### Scenario: R3S2-compose-network-isolation
<a id="r3s2-compose-network-isolation"></a>
docker compose 仅暴露 gateway：
- 外部无法直接访问各服务的 `808x` 端口
- internal API 只能在 compose 内网访问

---

### Requirement: R4-sync-call-resilience-standardize
<a id="r4-sync-call-resilience-standardize"></a>
**Module:** user-service / message-service / auth-service / search-service（内部调用）

跨服务 HTTP 调用统一具备：
- 确定性超时（connect/read）
- 清晰降级语义（哪些接口允许降级、默认值是什么）
- 可观测（成功/失败/降级次数与耗时）

#### Scenario: R4S1-downstream-unavailable-fast-fail
<a id="r4s1-downstream-unavailable-fast-fail"></a>
当下游服务不可用或网络异常：
- 调用方在超时阈值内返回（不被挂死）
- 有明确的日志/指标可定位到调用链路

---

### Requirement: R5-kafka-consume-idempotency-dlq-trace
<a id="r5-kafka-consume-idempotency-dlq-trace"></a>
**Module:** content-service / message-service / search-service / common

Kafka 消费统一做到：
- 幂等（进程重启/多实例/重试均不重复产生不可接受副作用）
- 失败有边界（重试次数 + DLQ）
- 可运营（DLQ 指标 + 回放脚本 + 运行手册）
- traceId 可贯穿（从 envelope 注入 MDC/TraceId）

#### Scenario: R5S1-duplicate-delivery-idempotent
<a id="r5s1-duplicate-delivery-idempotent"></a>
同一 `eventId` 重复投递/重试：
- message/search 不产生重复副作用（已具备 DB 幂等基线）
- content 不依赖 JVM 内存去重（改为持久化幂等或天然幂等）

#### Scenario: R5S2-consume-failure-to-dlq
<a id="r5s2-consume-failure-to-dlq"></a>
消费发生不可恢复异常：
- 按统一策略重试
- 超过阈值写入 `<topic>.dlq`
- 指标可观测，支持回放

#### Scenario: R5S3-traceid-in-kafka-log
<a id="r5s3-traceid-in-kafka-log"></a>
Kafka 消费日志能打印出 `traceId`，并与 HTTP 链路一致，便于串联排障。

---

### Requirement: R6-password-hash-upgrade
<a id="r6-password-hash-upgrade"></a>
**Module:** user-service / auth-service

密码存储与校验升级到 BCrypt/Argon2，同时保证历史用户平滑迁移。

#### Scenario: R6S1-gradual-rehash-on-login
<a id="r6s1-gradual-rehash-on-login"></a>
历史用户仍可登录：
- 校验 legacy 密码成功后，自动升级为新哈希并写回
- 新注册用户默认使用新哈希

---

### Requirement: R7-jwt-key-governance
<a id="r7-jwt-key-governance"></a>
**Module:** auth-service / gateway / 各业务服务 / deploy

避免“多服务多 secret”导致的配置漂移：
- 短期：统一 secret 配置来源与命名
- 中期：RS256（auth 私钥签发，其他服务仅持公钥验签）

#### Scenario: R7S1-env-drift-protection
<a id="r7s1-env-drift-protection"></a>
配置不一致时应能在启动期或健康检查阶段被发现，避免运行期随机 401/403。

---

### Requirement: R8-outbox-reliable-delivery
<a id="r8-outbox-reliable-delivery"></a>
**Module:** content-service / social-service / message-service / search-service

将“最终一致”升级到“可靠最终一致”：
- 事件不因 Kafka 短暂不可用而丢失
- 可重试、可回溯、可观测

#### Scenario: R8S1-commit-then-kafka-down-no-loss
<a id="r8s1-commit-then-kafka-down-no-loss"></a>
DB commit 后 Kafka 不可用：
- 事件进入 Outbox，后续恢复后自动补发
- 下游通过幂等保证不重复副作用

## Risk Assessment
- **风险：HTTP 环境下 cookie 安全性弱**  
  - Mitigation：明确该环境仅用于学习/内网演示；生产尽快切 HTTPS；网关启用 OriginGuard 与限流；refresh cookie 仅 `HttpOnly` 且限定 path。
- **风险：auth-service 断库迁移影响登录链路**  
  - Mitigation：分阶段迁移（先加 internal API + 双路径开关，再移除 Mapper/数据源）；加回归用例与灰度切换。
- **风险：internal-token 泄露**  
  - Mitigation：compose 网络隔离（仅暴露 gateway）；token 定期轮换；internal 请求仅允许服务间调用；必要时引入 mTLS/mesh（后续）。
- **风险：密码哈希升级回滚困难**  
  - Mitigation：渐进升级（登录时 rehash）；保留兼容校验一段时间；写入操作要可观测。
- **风险：Outbox 增加系统复杂度**  
  - Mitigation：作为 P1 迭代，先补齐 P0 的 DLQ/幂等/可观测；Outbox 以最小子集落地并通过压测与演练验证。
