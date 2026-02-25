# Technical Design: 一致性缺口与依赖耦合治理（Perceived Consistency + RPC 收敛 + 幂等/配置护栏）

## Technical Solution

### Core Technologies
- Frontend: Vue 3 + Vite + Vue Router + Pinia + Axios
- Gateway: Spring Cloud Gateway + Spring Security（JWT）
- Services: Spring Boot 3（Servlet stack）+ MyBatis/JdbcTemplate
- Infra: MySQL + Redis + Kafka + Elasticsearch
- Observability: Prometheus / Grafana / Loki（traceId 贯穿）
- Internal Security:
  - `/internal/**`：`X-Internal-Token`（`InternalTokenFilter` 兜底）
  - 高风险 internal 运维入口：`X-Ops-Token` + allowlist + single-flight（`InternalOpsGuardFilter`）

### Implementation Key Points

#### 1) Perceived Consistency（Read-your-writes）
目标不是把全链路改成强一致，而是“把最终一致的真实成本显式化并最小化用户可感知不一致”。

建议落地策略（P0→P1）：
- **P0（推荐）：前端覆盖（Overlay）优先**
  - 写接口（点赞/取消、发帖、评论）在响应中已经携带最新状态时，前端用该响应刷新 UI；
  - 对“随后短时间内的读请求”引入短 TTL 的覆盖层（例如按 `entityType:entityId` 缓存 `liked/likeCount`），在页面 reload/刷新后仍能尽量保持 read-your-writes；
  - 覆盖 TTL 到期后自然回归后端投影结果（不引入永久分叉）。
- **P1（可选）：读路径“缺失可判定”与可控回源**
  - 仅在“投影缺失可判定（UNKNOWN）”时回源 SSOT，并回填投影（避免把所有请求都变成同步 RPC）。
  - 注意：回源必须有超时/熔断与可观测，且只用于“缺口修复”，不做常态化强一致读取。

#### 2) RPC Aggregation Hardening（降低 fan-out）
针对 user-service 用户主页聚合：
- 在 social-service 提供**单次 internal read 聚合接口**返回用户主页所需统计（likeCount/followeeCount/followerCount + viewer 的 hasFollowed）。
- user-service 侧将原本多次调用收敛为一次调用，减少延迟放大与失败点。
- 降级语义建议：避免把“依赖不可用”伪装成 0；可通过新增字段 `degraded=true/false` 或 `source=ssot/degraded` 让前端可区分展示。

针对 message-service 用户名解析：
- 增加 username→userId 的短 TTL 缓存（内存或 Redis，可按环境选择）。
- 前端也可尽量发送 `toId`（一次 resolve 后复用），降低写路径对 user-service 的依赖放大。

#### 3) Idempotency UX & Safety（幂等体验与安全）
现状：部分写接口强制 `Idempotency-Key`，缺失直接 400，脚本/第三方容易踩坑。

建议改进（保持安全默认态）：
- `IdempotencyGuard` 支持 TTL 配置化（processing/success），并可按 operation 细化默认值；
- 缺失 key 的错误提示更明确（包含 header 名、用途、示例/文档指引），并配套提供脚本模板；
- P1 可选：对关键写接口引入 DB-level idempotency（以 DB 约束/字段作为最终幂等锚点），降低“幂等存储不可用但副作用已发生”的灰区。

#### 4) Config Guardrails（配置护栏）
目标是把“容易误配且影响巨大”的配置项固化为**可执行校验**，并在 CI/部署前可自动阻断。

建议：
- 新增 `scripts/doctor.sh`（或同等脚本）：
  - 校验 `JWT_HMAC_SECRET`/`*_JWT_HMAC_SECRET` 长度与一致性；
  - 校验各服务 `X-Internal-Token`/`X-Ops-Token` 必要项是否配置（按环境区分）；
  - 校验生产部署必须显式启用 `prod` profile（与 `application-prod.yml` 的 fail-fast 配合）；
  - 输出明确建议（不输出敏感值）。
- 文档补齐“配置矩阵”：哪些必配、哪些可选、哪些必须分环境（dev/prod）。

## Architecture Decision ADR

### ADR-0XX: 优先采用“前端 Overlay”解决感知一致性
**Context:** 读侧依赖 Kafka 投影存在短暂滞后，用户在写后立即刷新可能看到旧值；强一致化会引入同步依赖与级联风险。  
**Decision:** 对交互敏感场景采用前端短 TTL 覆盖层实现 read-your-writes；后端保持最终一致为主。  
**Rationale:** 以最小侵入换取最大体验收益；不把“投影滞后”升级为“全链路同步耦合”。  
**Alternatives:**
- 读路径强制回源 SSOT（拒绝原因：会把多数读请求变成同步 RPC，风险与成本高）
- 全面事件化投影到所有服务（拒绝原因：改动面大，演进周期长，需补齐更多事件契约）
**Impact:** 需要前端在渲染层引入覆盖合并与 TTL 管理；对多端一致性只提供“用户感知优先”的近似保证。

### ADR-0XY: 用户主页 social 统计采用聚合 internal endpoint
**Context:** user-service 用户主页对 social-service 存在多次同步调用，延迟放大且降级语义不友好。  
**Decision:** 在 social-service 提供聚合 internal read API，一次返回所需统计与状态；user-service 只调用一次。  
**Rationale:** 在不引入新事件契约的前提下，显著降低 fan-out 与失败点，演进成本较低。  
**Alternatives:**
- user-service 维护本地投影（拒绝原因：需要 FollowRemoved 等事件契约补齐与更多存储/回填机制）
- 网关聚合（拒绝原因：gateway 不应承担业务聚合与跨域数据拼装）
**Impact:** internal API 需要纳入 internal-token 保护与测试；前端可根据降级标记优化展示。

### ADR-0XZ: IdempotencyGuard 以“可配置 + 工具化”优先，DB-level 作为 P1
**Context:** 强制 header 能保证写路径安全，但对非浏览器客户端不友好；同时 processing TTL 固定存在慢链路重复执行风险。  
**Decision:** P0 先实现 TTL 配置化 + 更友好错误提示 + 示例脚本；P1 再评估 DB-level 幂等锚点。  
**Rationale:** 先用低风险改动解决主要易用性问题，再逐步增强一次性语义。  
**Impact:** 需要更新 common 配置项与文档；DB-level 幂等涉及 schema 变更，需单独评审。

## API Design

### social-service（internal read 聚合）
**[GET] /internal/social/read/users/{userId}/profile-stats**
- Query:
  - `viewerId`（可选）：查看者 userId（用于计算 hasFollowed）
- Response（示例字段，保持可扩展）：
  - `likeCount`
  - `followeeCount`
  - `followerCount`
  - `hasFollowed`（viewerId 缺失或无效时返回 null/false，按契约约定）
  - `degraded`（可选）：表示该结果是否发生降级（便于前端区分展示）

### （可选）对外 API：staleness/降级标记
为保持向后兼容，建议优先新增可选字段而非改变现有字段类型：
- `degraded` / `dataFresh` / `projectionHint`（按模块选择最小集合）

## Data Model

- P0：不强制变更数据模型。
- P1（可选）：对关键写操作引入 DB-level idempotency（例如以 `(user_id, idempotency_key, operation)` 唯一约束作为最终幂等锚点）。

## Security and Performance

- **Security**
  - 新增 internal endpoint 必须走 `X-Internal-Token` 保护（遵循 internal 最小权限原则）。
  - ops 入口保持 break-glass 默认关闭，必要时纳入 `InternalOpsGuardFilter` 的 allowlist/频控。
- **Performance**
  - RPC fan-out 收敛可显著降低尾延迟；
  - 前端覆盖层应短 TTL、容量受控，避免无界增长；
  - username→userId 缓存 TTL 受控，避免长期陈旧。

## Testing and Deployment

- **Testing**
  - social-service：聚合 internal API 单测/集成测试（含 viewerId/降级语义）
  - user-service：UserController 聚合数据回归（不再多次调用）
  - frontend：PostDetailView/PostsView 的 like 覆盖与刷新回归（含 refresh 场景）
  - common：IdempotencyGuard TTL 配置与错误语义测试
- **Deployment**
  - 新脚本 `scripts/doctor.sh` 纳入 CI（或部署前检查），作为“上线前自检”步骤
  - 按环境补齐配置矩阵并验证（dev/prod）
