# Change Proposal: 运维与一致性加固（Outbox 并发 + Reindex 单飞 + internal/ops 护栏）

## Requirement Background
当前工程已具备“社区”核心链路（登录/发帖/评论/点赞关注/私信通知/搜索/统计）与较完善的安全默认态（internal-token、OriginGuard、限流、审计、outbox、消费幂等等）。

但在“多实例并发 + 运维操作 + 配置漂移 + 兼容窗口”这些更贴近生产的场景中，仍存在一组可预期会放大的问题：

1. **Outbox 认领并发性能隐患**：认领使用 `SELECT ... FOR UPDATE` 且未启用 `SKIP LOCKED`，多实例 relay 并发时可能出现阻塞/头阻塞，造成吞吐下降与延迟抖动。
2. **Outbox 运维能力不一致**：content/social 已提供 `/internal/{segment}/outbox/health|replay` 运维入口（并受 internal-token/ops-guard 保护），但 user-service outbox 缺少对应 controller，导致排障与自助恢复链路不对齐。
3. **Reindex single-flight 锁 TTL 固定**：search-service reindex 的 Redis 锁 TTL 固定为 30 分钟，若 reindex 超时运行，可能出现“锁过期→并发重建”，放大 ES/下游压力。
4. **internal/ops 安全依赖“多层拼装”**：internal-token 解析包含别名/分域写入口；ops guard 又叠加 break-glass、allowlist、token 与 Redis 限流/锁。若配置 key 命名或 segment 发生漂移，容易表现为大量 403（功能不可用）或运维入口无法开启（可运维性差）。
5. **遗留兼容入口仍对外暴露**：网关仍保留 `/api/search/internal/reindex`（对外但命名 internal），即便已标记弃用，也会持续增加维护成本与误用风险。
6. **dev 便捷能力的误用风险**：dev profile 固定验证码、回传 activation/reset link、默认演示账号口令等，如果环境隔离不严，会直接变成真实安全事故源。

本变更目标是：在不推翻现有架构的前提下，补齐“并发吞吐 + 运维一致性 + 配置护栏 + 兼容入口收敛”的工程化细节，使系统在多实例与生产运维场景下更稳定、更可控、更可排障。

## Change Content
1. Outbox：在 MySQL 8 场景启用 `SKIP LOCKED` 的非阻塞认领策略，降低多实例并发头阻塞。
2. Outbox：补齐 user-service 的 outbox 运维入口（health/replay），并与 InternalOpsGuardFilter 的 break-glass 机制对齐。
3. Reindex：为 reindex single-flight 锁增加“续租/心跳”能力，避免长任务导致锁过期引发并发重建。
4. internal/ops：收敛配置命名与校验策略（启动期 fail-fast + doctor 自检），降低配置漂移导致的 403 与不可运维风险。
5. Legacy：对 `/api/search/internal/reindex` 进行默认禁用/显式引导迁移（410/文案/脚本），并给出最终移除窗口。
6. dev/prod：补齐“危险 dev 配置”的 prod 护栏（prod 下 fail-closed），并收敛文档，避免误用扩散。

## Impact Scope
- **Modules:** `content-service/`, `social-service/`, `user-service/`, `search-service/`, `gateway/`, `common/`, `deploy/`, `scripts/`, `docs/`, `helloagents/wiki/`
- **Files:** 预计涉及：
  - `*/src/main/resources/mapper/outbox-event-mapper.xml`
  - `search-service/src/main/java/.../ReindexJobService.java`
  - `gateway/src/main/java/...`（legacy path 拦截/开关）
  - `deploy/nacos-config/*.yaml`, `deploy/.env.example`, `deploy/docker-compose.yml`
  - `scripts/doctor.sh`, `scripts/search-reindex.sh`
  - `auth-service/src/main/resources/application-dev.yml` 与 prod 校验逻辑
- **APIs:**
  - 新增：`user-service` outbox 运维入口（internal）
  - 收敛：`/api/search/internal/reindex`（legacy，计划默认禁用并逐步移除）
- **Data:** 不改核心业务表结构；涉及 outbox / 幂等表的并发行为与运维操作；reindex 过程可能长时间运行（需更强保护）。

## Core Scenarios

### Requirement: Outbox 认领并发与吞吐（outbox-claim-concurrency）
**Module:** `content-service` / `social-service` / `user-service`

#### Scenario: 多实例 relay 并发时不头阻塞（skip-locked）
当同一服务部署为多实例（或同机多进程）时：
- outbox relay 认领批次应尽量避免互相阻塞；
- 并发实例应倾向于“跳过已锁行”各自拉取不同批次；
- 即便 Kafka 抖动导致积压，也应保持可预测的吞吐与延迟，不出现长期锁等待堆积。

### Requirement: Outbox 运维能力一致性（outbox-ops-parity）
**Module:** `content-service` / `social-service` / `user-service`

#### Scenario: 三个服务均支持 outbox health/replay 且受 break-glass 保护（ops-endpoints-parity）
- `GET /internal/{segment}/outbox/health` 返回 NEW/RETRY/SENDING/FAILED 计数；
- `POST /internal/{segment}/outbox/replay` 仅在 break-glass 开启（ops.guard + allowlist + ops token）时允许；
- 运维入口在默认配置下为 deny（不会被误触）。

### Requirement: Reindex single-flight 锁续租（reindex-lock-renewal）
**Module:** `search-service`

#### Scenario: reindex 运行超过 30 分钟也不会并发重建（lock-renewal）
当 reindex 因数据量/ES/下游抖动导致运行超过锁 TTL：
- 只要任务仍在执行且持有锁，应持续续租（避免 TTL 过期）；
- 并发请求必须稳定返回 409（含 jobId，便于排障）；
- 若执行实例崩溃，锁应在可控时间后自然过期，允许重新触发（避免永久卡死）。

### Requirement: internal/ops 配置一致性与启动校验（internal-ops-config-drift）
**Module:** `common` / `deploy` / `scripts`

#### Scenario: break-glass 开关开启但关键配置缺失时 fail-fast（fail-fast-when-enabled）
当启用以下任一高风险开关：
- `OPS_OUTBOX_REPLAY_ENABLED=true`
- `OPS_SEARCH_REINDEX_ENABLED=true`

系统应在启动期或 doctor 自检期明确报错，指出缺失项（token/allowlist/Redis 等），避免“上线后才 403 / 无法运维”的被动排障。

### Requirement: legacy 对外入口收敛（legacy-search-internal-reindex）
**Module:** `gateway` / `search-service` / `scripts` / `docs`

#### Scenario: legacy 路径默认不可用且给出迁移引导（disable-and-guide）
- 默认访问 `POST /api/search/internal/reindex` 返回明确的“已弃用/请使用 /api/ops/search/reindex”提示（建议 410/403，按策略确定）；
- 保留可控的临时兼容开关，便于短期回滚与灰度；
- `scripts/search-reindex.sh` 等工具统一使用 `/api/ops/search/reindex`。

### Requirement: dev/prod 护栏（dev-prod-guardrails）
**Module:** `auth-service` / `docs` / `deploy`

#### Scenario: prod 下禁止固定验证码与敏感链接回传（prod-guardrails）
当 `SPRING_PROFILES_ACTIVE` 包含 `prod` 时：
- 若配置了固定验证码（如 `auth.captcha.fixed-code`）必须阻断启动；
- 若开启了 activation/reset link 回传也必须阻断启动；
- 文档明确区分 dev 与 prod 的行为与风险。

## Risk Assessment
- **Risk: `SKIP LOCKED` 兼容性/行为差异**
  - **Mitigation:** 仅在 MySQL 8 场景启用；提供开关可回退到旧策略；加入并发冒烟验证。
- **Risk: reindex 续租实现不当导致“锁被错误续租”或“锁无法释放”**
  - **Mitigation:** 使用 jobId 作为 owner，续租必须校验 owner；释放必须校验 owner；异常情况下依赖 TTL 自然过期兜底。
- **Risk: 配置项增多导致运维复杂度上升**
  - **Mitigation:** 统一命名约定（segment 对齐）；doctor.sh 与启动校验提供明确错误；`deploy/.env.example` 给出默认安全示例。
