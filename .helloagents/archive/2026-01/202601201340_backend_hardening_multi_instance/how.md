# Technical Design: 后端多实例上线加固（同站不同源 / HTTP / Docker Compose）

## Technical Solution

### Core Technologies
- Java 17 / Spring Boot 3.2.x
- Spring Cloud Gateway（统一入口：路由/CORS/鉴权/限流/审计/trace）
- Spring Security 6（JWT 资源服务器）
- Nacos（注册发现/配置中心）
- MyBatis + MySQL（按服务 schema 隔离，逐步推进最小权限）
- Redis（refresh token、业务缓存、幂等/队列等）
- Kafka（最终一致事件）+ DLQ（失败闭环）
- Micrometer + Prometheus（指标）

### Implementation Key Points

#### 1) 同站不同源下的 cookie refresh 稳定性
- 网关 CORS：允许 credentials，且允许 origin 必须精确匹配前端（不可用 `*`）。
- 前端 HTTP 客户端：对登录/刷新/登出等需要 cookie 的请求启用 `withCredentials/credentials: include`。
- auth refresh cookie：保持 `SameSite=Lax`、`secure=false`（HTTP 环境必需），`HttpOnly=true`、`path=/api/auth` 以缩小作用域（实现点：`auth-service` 的 `RefreshTokenService`）。
- 网关 OriginGuard：对 login/refresh/logout 校验 Origin allowlist，与 CORS allowlist 使用同一份配置源，避免双点漂移。

#### 2) auth-service 去 MySQL：身份域 SSOT 收敛到 user-service
- user-service 提供内部鉴权 API：负责用户名密码校验、用户状态/角色查询、密码升级写入等。
- auth-service 仅保留“安全编排 + token 签发 + refresh 会话”：
  - 登录：验证码/节流在 auth，用户名密码校验委托给 user-service internal API
  - refresh：根据 refresh token 得到 userId，再向 user-service internal API 拉取状态/roles
  - logout：family revoke（Redis）不依赖 DB
- 迁移策略：先引入双路径开关（数据库直连 vs internal API），灰度后彻底移除 MyBatis/datasource。

#### 3) internal API 统一强制校验
- 统一的 `/internal/**` 校验在 Filter/Interceptor 层实现，而不是由每个 Controller “手写 assertInternalToken”。
- Header 固定：`X-Internal-Token`；当服务端 token 未配置时直接拒绝（fail-closed），避免 silent fail-open。
- compose 网络隔离：仅对外暴露 gateway，其余服务仅内网可达。

#### 4) 跨服务同步调用标准化
统一到“超时 + 指标 + 降级语义”三件套：
- RestTemplate/WebClient：强制 connect/read timeout（在线链路更严格，后台任务可更宽松）。
- 失败策略：明确哪些接口可以降级（返回默认值），哪些必须失败（例如鉴权类 internal API）。
- 指标：按 `client_requests_total{api,outcome}` 与 `client_latency{api,outcome}` 统一打点。
- traceId：同步调用透传 `X-Trace-Id`（至少保证日志链路可串联）。

#### 5) Kafka 消费统一：幂等 + DLQ + traceId
- 消费端 ack：继续使用 manual ack（当前 content/search 已是 manual）。
- 幂等：
  - message-service/search-service：保持 DB 幂等（eventId 唯一约束/记录）为基线
  - content-service：移除 JVM `seenEventIds`；使用持久化幂等（Redis/DB）或确保副作用天然幂等
- 失败处理：统一 DefaultErrorHandler（重试次数 + backoff）与 DLQ 投递 `<topic>.dlq`，并输出指标。
- traceId：从 event envelope 取 `traceId` 写入 MDC/TraceId，保证消费日志可与 HTTP 串联。

#### 6) 密码哈希升级（渐进）
- 新注册/重置密码：直接使用 BCrypt/Argon2 写入 `user.password`
- 历史用户：登录校验 legacy（MD5+salt）成功后，立即 rehash 并写回为 BCrypt
- 回滚策略：保留 legacy 校验逻辑一段时间；升级写回做成幂等与可观测

#### 7) JWT 密钥治理
- 短期：统一 HMAC secret 的读取键，减少 “AUTH/GATEWAY/JWT_HMAC_SECRET” 漂移；在启动期强校验长度与非空。
- 中期：切 RS256（auth 私钥签发；gateway 与各服务使用公钥验签），把“密钥扩散面”收敛到 auth-service。

#### 8) Outbox（P1 可靠投递）
- 生产者（content/social 等）在业务事务内写 outbox 表（包含 eventId/topic/key/payload/status/attempts/next_retry_at）
- relay worker 周期扫描并投递 Kafka，成功后标记已发送；失败可重试并可观测
- 下游继续以 eventId 幂等保证“至少一次投递”下无重复副作用

## Architecture Decision ADR

### ADR-001: 同站不同源 + HTTP 环境下采用 refresh cookie（SameSite=Lax）
**Context:** 需要多实例稳定登录态，但暂时无 HTTPS。  
**Decision:** refresh token 走 HttpOnly cookie，SameSite=Lax，secure=false；前端开启 credentials；网关 CORS 允许 credentials。  
**Rationale:** 同站不同源下 Lax 可用且比 localStorage 更安全；HTTP 环境无法使用 SameSite=None+Secure。  
**Alternatives:** refresh token 放 localStorage → 拒绝原因：XSS 风险更高、上线安全基线不满足。  
**Impact:** 必须严格配置 CORS 与 OriginGuard；后续切 HTTPS 时可升级 Secure 策略。

### ADR-002: 身份域 SSOT 收敛到 user-service，auth-service 仅做编排
**Context:** auth-service 直连 user 表导致数据所有权不清、最小权限难落地、多实例弹性受限。  
**Decision:** user-service 负责密码校验与用户状态；auth-service 调用 internal API，不再连 MySQL。  
**Rationale:** 清晰边界、减少跨库耦合、便于后续 schema/账号隔离与演进。  
**Alternatives:** 保持共享 user 表 → 拒绝原因：上线后协作/权限/迁移成本持续升高。  
**Impact:** 需要 internal API、安全策略与同步调用韧性标准化。

### ADR-003: internal API 统一 Filter 强制校验 `X-Internal-Token`
**Context:** 目前 internal token 多由 controller 手工校验，易遗漏。  
**Decision:** 在服务层统一 Filter/Interceptor 对 `/internal/**` 校验并 fail-closed。  
**Rationale:** 默认安全、减少人为失误。  
**Alternatives:** 继续 controller 手写校验 → 拒绝原因：长期漂移不可控。  
**Impact:** 需要各服务统一接入与配置来源治理。

### ADR-004: Kafka 消费统一 DefaultErrorHandler + DLQ
**Context:** 失败处理不一致会导致无限重试或不可运营。  
**Decision:** 所有 Kafka consumer 统一 DefaultErrorHandler，重试后写入 `<topic>.dlq`，并输出指标。  
**Impact:** 运维侧需要 DLQ 告警与回放流程。

### ADR-005: Kafka 消费从 envelope 注入 traceId
**Context:** 事件链路缺少 traceId 会显著增加排障成本。  
**Decision:** 在 consumer 入口解析 envelope `traceId` 并写入 MDC/TraceId。  
**Impact:** 统一日志格式可串联 HTTP 与 Kafka。

## API Design

### [Internal] POST /internal/users/authenticate
- **Purpose:** 校验用户名密码与用户状态/角色，供 auth-service 登录使用
- **Header:** `X-Internal-Token: <token>`
- **Request:** `{ "username": "...", "password": "..." }`
- **Response:** `{ "userId": 1, "username": "...", "status": 1, "authorities": ["ROLE_USER"] }`

### [Internal] GET /internal/users/{userId}/session-profile
- **Purpose:** refresh 时查询用户状态与角色（禁用/角色变化立即生效）
- **Header:** `X-Internal-Token: <token>`
- **Response:** `{ "userId": 1, "status": 1, "authorities": ["ROLE_USER"] }`

## Data Model

### (Optional) content_consume_dedup / content_consumed_event
用途：content-service 对某些事件消费做持久化幂等（替代 JVM `seenEventIds`）。  
实现可选：MySQL 表（强一致）或 Redis key（轻量）。

### (P1) outbox_event
用途：生产者可靠投递 Kafka（commit 后必达）。  
字段建议：`id`、`event_id`（唯一）、`topic`、`key`、`payload_json`、`status`、`attempts`、`next_retry_at`、`created_at`、`sent_at`。

## Security and Performance
- **Security:**
  - refresh cookie `HttpOnly` + `path=/api/auth`，同站不同源配合 OriginGuard 降低 CSRF 风险
  - `/internal/**` 强制 `X-Internal-Token` + compose 网络隔离（仅暴露 gateway）
  - 密码升级为 BCrypt/Argon2，逐步淘汰 MD5
  - JWT secret 治理，避免配置漂移；中期切 RS256 降低密钥扩散面
- **Performance:**
  - 跨服务调用强制超时，避免线程长时间阻塞与级联雪崩
  - Kafka 失败快速进入 DLQ，避免卡死消费能力
  - 幂等去 JVM 化，减少重启导致的重复副作用

## Testing and Deployment
- **Testing:**
  - CORS + cookie refresh 的集成测试/手工回归用例（同站不同源）
  - auth→user internal 鉴权链路测试（成功/失败/禁用/角色变化）
  - Kafka consumer 幂等与 DLQ 流程测试（重复投递/异常投递）
  - 密码渐进升级测试（legacy 用户首次登录后升级）
- **Deployment:**
  - compose 只暴露 gateway；各服务端口不对外映射
  - 统一配置项在 Nacos/环境变量落地（JWT secret、internal-token、CORS allowlist）
  - 分阶段灰度：先引入 internal API 与双路径开关，再断库并清理遗留
