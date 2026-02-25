# Technical Design: Redis 关键状态迁移到 MySQL（幂等/refresh）+ 故障降级隔离

## Technical Solution
### Core Technologies
- Java 17 / Spring Boot 3.x（现有技术栈）
- MySQL（各服务独立 schema：`community_content`、`community_message`、`community` 等）
- MyBatis / JdbcTemplate（用于幂等与会话状态的强一致落库）
- SHA-256（refresh token 持久化仅存 hash，避免明文凭据落库）
- Micrometer Metrics（降级/错误/冲突的可观测性统一口径）

### Implementation Key Points
1. **幂等存储抽象与可插拔实现**
   - `common` 保留 `IdempotencyStore` 抽象，新增 `JdbcIdempotencyStore` 实现（SSOT=MySQL）。
   - `IdempotencyGuard` 不再在构造函数内硬编码 Redis 实现，改为依赖注入 `IdempotencyStore`（按配置选择）。
   - 幂等状态机：`PROCESSING`（并发互斥/处理中提示）→ `SUCCESS`（缓存响应复用）；TTL 由 `processing_ttl/success_ttl` 控制。
2. **幂等表模型（按服务落库）**
   - 表建议命名统一：`http_idempotency`（位于各自 schema）。
   - 唯一键：`(operation, user_id, idem_key)`；并发通过 insert-first 争抢。
   - 值：`status/response_json/expires_at` 等字段；提供清理任务（按 expires_at 分批删除）。
3. **refresh token SSOT 迁移到 MySQL**
   - 增加 refresh token DB 存储模式 `db`（以及迁移期 `dual`）：对外 cookie 协议不变。
   - refresh token 落库只存 `token_hash`（hex sha256），查询时由 auth-service 对 cookie 值计算 hash 再查。
   - `family_id` 作为“会话族”维度，实现 logout 的 family revoke。
4. **登录安全能力降级（不阻断主链路）**
   - captcha 与登录失败计数/限流在存储异常时不再直接 503 阻断登录。
   - 推荐降级策略：进入“风险提示 + 最小安全兜底”模式（记录指标/日志、必要时提高边界层静态限流）。
5. **网关限流降级隔离**
   - Redis 不可用时，网关限流进入降级模式：可选 fail-open（保证可用性）或 per-rule 本地有界限流（保留一定风控能力）。
   - 降级必须可观测：统一 metrics outcome（`degraded/error`）+ traceId。

## Architecture Decision ADR

### ADR-001: 幂等与 refresh token 从 Redis 迁移到 MySQL（SSOT）
**Context:** 当前 required 幂等、refresh/captcha 等在 Redis 异常时 fail-closed，放大为核心写功能与登录链路不可用。  
**Decision:** 对“业务正确性强相关”的关键状态（幂等、refresh token）迁移到 MySQL 作为 SSOT；Redis 仅作为可选加速层，不再作为必需依赖。  
**Rationale:** MySQL 具备更成熟的持久化与一致性语义，且各服务已有独立 schema；通过唯一约束与索引可实现可控的幂等与会话存储。  
**Alternatives:**  
- 继续依赖 Redis + 提升 Redis HA：仍无法彻底避免抖动放大与 fail-closed 503，且关键状态仍是易失/外部依赖。  
- 引入专用一致性 KV（etcd/Consul/ZK）：运维与复杂度显著上升，不符合迭代 0 的投入产出。  
**Impact:**  
- 新增/调整 DB 表与清理任务；实现复杂度上升但故障半径显著降低；需要灰度与迁移策略（尤其 refresh token）。

### ADR-002: 网关限流 Redis 故障的降级策略
**Context:** 网关限流依赖 Redis 且默认 fail-closed，会把 Redis 故障前置为对外 503。  
**Decision:** 在 Redis 异常时进入降级模式：按规则选择 fail-open 或本地有界限流 fallback，并强制可观测。  
**Rationale:** 限流属于安全增强能力，不应在基础设施抖动时把业务写入口统一打挂；降级可显著降低故障半径。  
**Alternatives:** 始终 fail-closed（安全优先但可用性差、故障半径大）。  
**Impact:** 需要明确接口分级与风险接受边界，并配套告警与审计。

## API Design
（若采用 refresh token DB 托管于 user-service 的实现路径）
- **[POST] `/internal/users/sessions/refresh/store`**：存储 refresh token（hash + userId + familyId + expiresAt）
- **[GET] `/internal/users/sessions/refresh/{tokenHash}`**：查询 refresh token record
- **[POST] `/internal/users/sessions/refresh/revoke`**：撤销单个 token（按 hash）
- **[POST] `/internal/users/sessions/refresh/revoke-family`**：撤销 family（按 familyId）

## Data Model
```sql
-- content/message schema: http_idempotency（示意）
create table http_idempotency (
  id bigint auto_increment primary key,
  operation varchar(64) not null,
  user_id int not null,
  idem_key varchar(128) not null,
  status varchar(16) not null,
  response_json mediumtext null,
  processing_expires_at timestamp null,
  success_expires_at timestamp null,
  created_at timestamp null default current_timestamp,
  updated_at timestamp null default current_timestamp on update current_timestamp,
  unique key uk_http_idem (operation, user_id, idem_key),
  key idx_http_idem_success_expires (success_expires_at, id),
  key idx_http_idem_processing_expires (processing_expires_at, id)
);

-- identity schema: auth_refresh_token（示意，存 hash）
create table auth_refresh_token (
  token_hash char(64) primary key,
  user_id int not null,
  family_id varchar(64) not null,
  expires_at timestamp not null,
  revoked_at timestamp null,
  created_at timestamp null default current_timestamp,
  key idx_refresh_family (family_id, expires_at),
  key idx_refresh_user (user_id, expires_at)
);
```

## Security and Performance
- **Security：**
  - refresh token 仅存 hash（建议 sha256(token) 的 hex），避免明文落库；
  - 降级策略必须可观测并可开关；避免 silent fail-open；
  - internal API 仅限内网调用（部署层隔离），并保留审计字段（traceId/userId/outcome）。
- **Performance：**
  - 幂等查询以唯一键命中，写放大可控；通过过期清理避免表膨胀；
  - refresh token 按主键 hash 查询，O(1)；family revoke 走索引批量更新/删除。

## Testing and Deployment
- **Testing：**
  - 幂等：并发同 key 返回 409；重复请求复用响应；store 异常下仍符合预期语义；
  - refresh：rotate/revoke/revokeFamily 的正确性；双读/双写迁移模式下兼容旧 token；
  - 网关：Redis 故障时按配置进入 degraded outcome（不再 503 阻断写入口）。
- **Deployment：**
  - 先上线“兼容模式”（refresh token dual）与幂等 DB store 开关；
  - 观察指标与日志无异常后，切换到 DB-only；
  - 保留快速回滚：切回 Redis store 与旧策略。

