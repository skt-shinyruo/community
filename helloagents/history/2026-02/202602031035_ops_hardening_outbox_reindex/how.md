# Technical Design: 运维与一致性加固（Outbox 并发 + Reindex 单飞 + internal/ops 护栏）

## Technical Solution

### Core Technologies
- Backend: Java 17 / Spring Boot 3 / Spring Security / Spring Cloud Gateway
- Data/Infra: MySQL 8 / Redis / Kafka / Elasticsearch
- Config: Nacos（`deploy/nacos-config/*.yaml`）+ `.env`（本地）
- Observability: Micrometer / Prometheus / Grafana / Loki

### Implementation Key Points
1. **Outbox 并发认领：启用非阻塞策略**
   - 将 outbox 认领 SQL 从 `FOR UPDATE` 升级为 `FOR UPDATE SKIP LOCKED`（MySQL 8 支持）。
   - 目标：多实例 relay 并发时“跳过已锁行”，避免阻塞与头阻塞。
   - 保留回退开关：在不支持或出现异常时可切回旧行为（兼容性兜底）。
2. **Outbox 运维能力一致性：补齐 user-service**
   - 为 user-service 增加 `/internal/users/outbox/health` 与 `/internal/users/outbox/replay`。
   - 运维入口保护：
     - internal-token：由 `common/InternalTokenFilter` 兜底；
     - break-glass：由 `common/InternalOpsGuardFilter` 兜底（enabled + allowlist + X-Ops-Token + Redis single-flight/限流）。
   - 补齐 user-service 的 `deploy/nacos-config/user-service.yaml` 与 `deploy/.env.example` 的 ops token 配置（避免“功能有但不可用”）。
3. **Reindex single-flight：锁续租/心跳**
   - 在 `ReindexJobService` 中引入“可续租锁”：
     - lock key value = jobId；
     - acquire 时设置 TTL；
     - 运行中定期续租（renew），续租必须校验当前 value 仍等于 jobId（避免误续租）。
   - 建议用 Lua 脚本实现 check-and-expire 的原子性（避免 get+expire 的竞态）。
   - 续租频率建议：TTL 的 1/3 或固定 60s（可配置），任务结束立即释放锁（同样校验 owner）。
4. **internal/ops 配置漂移治理：启动校验 + doctor 自检**
   - 扩展 `common/StartupValidation`（或服务侧补充校验）：
     - 当 `OPS_OUTBOX_REPLAY_ENABLED=true` 时，要求：
       - `ops.guard.outbox-replay.allowlist` 非空；
       - 当前服务对应的 `ops.<segment>.token` 非空（以及可选 previous）；
       - Redis 可用（否则运维保护器会返回 503，属于“配置/依赖缺失”应尽早暴露）。
     - 当 `OPS_SEARCH_REINDEX_ENABLED=true` 时，要求：
       - `ops.guard.search-reindex.allowlist` 非空；
       - `ops.search.token` 非空；
       - Redis 可用。
   - 同步更新 `scripts/doctor.sh` 与 `deploy/.env.example`，让“缺什么”在启动前即可看到。
5. **Legacy 入口收敛：默认禁用 + 明确迁移**
   - gateway 增加对 `/api/search/internal/reindex` 的显式拦截：
     - 默认返回 410/403（按策略确定）+ 提示迁移到 `/api/ops/search/reindex`；
     - 可用配置临时开启兼容（仅用于短期回滚/灰度）。
   - 脚本与文档统一迁移到 `/api/ops/search/reindex`，减少误用面。
6. **dev/prod 护栏：危险配置 prod 下 fail-closed**
   - 在 auth-service 增加启动期校验：
     - `prod` profile 下禁止 `auth.captcha.fixed-code`；
     - `prod` profile 下禁止 activation/reset link 回传开关；
   - 将默认账号口令/固定验证码等说明收敛为“仅 dev/演示”，避免被当成生产配置。

## Architecture Decision ADR

### ADR-001: Outbox 认领策略使用 `FOR UPDATE SKIP LOCKED`
**Context:** 多实例 relay 并发时，`FOR UPDATE` 容易造成锁等待与头阻塞，吞吐与延迟不可预测。  
**Decision:** 在 MySQL 8 场景升级为 `FOR UPDATE SKIP LOCKED`，以非阻塞方式并发认领；保留开关回退到旧策略。  
**Rationale:** 改动小、收益明确，且与现有“认领→markSending→发送→状态更新”的实现兼容。  
**Alternatives:**  
- 方案 A：维持 `FOR UPDATE` → 风险：多实例并发吞吐下降，积压时更明显。  
- 方案 B：改为 Redis 队列/外部调度器 → 工程量大，引入新依赖与一致性问题。  
**Impact:** mapper SQL 变更 + 并发冒烟用例补齐；需明确 MySQL 版本要求与回退开关。  

### ADR-002: Reindex 锁采用“owner=jobId + 续租（Lua check-and-expire）”
**Context:** reindex 可能超过 30 分钟，固定 TTL 会导致锁过期，引发并发重建与资源被压垮。  
**Decision:** 增加锁续租，续租必须校验当前 owner；释放锁同样校验 owner。  
**Rationale:** 在不引入 Redisson 的前提下，把“锁过期并发重建”风险降到最低，并保留 TTL 作为崩溃兜底。  
**Alternatives:**  
- 方案 A：仅把 TTL 调大（例如 2h）→ 风险：崩溃时恢复慢，且仍可能超时。  
- 方案 B：引入 Redisson/Fenced Lock → 依赖与改造面更大。  
**Impact:** 增加少量 Redis 脚本与调度逻辑；需要单测覆盖 owner 校验与续租停止。  

### ADR-003: legacy `/api/search/internal/reindex` 默认禁用并返回明确引导
**Context:** 对外路径命名为 internal 易误用，且需要长期维护安全与运维保护链路。  
**Decision:** gateway 默认禁用该路径并返回明确迁移提示；保留短期开关用于回滚/灰度；脚本与文档统一迁移到 `/api/ops/search/reindex`。  
**Rationale:** 降低攻击面与误用成本，同时给现存调用方一个清晰迁移窗口。  
**Alternatives:**  
- 方案 A：保留但只加 Deprecation header → 仍会被脚本/用户误用。  
- 方案 B：立即删除 → 可能破坏现存依赖，回滚成本高。  
**Impact:** gateway 增加拦截逻辑与配置；同步更新脚本与文档。  

## Security and Performance
- **Security:**
  - internal-token：保持按 segment 分域（`*_INTERNAL_TOKEN`），避免全局 token 爆炸半径扩大；
  - ops guard：默认 deny（break-glass），开启必须同时满足 enabled + allowlist + token + Redis；
  - legacy 入口：默认禁用并可观测（日志/指标），避免“静默仍可用”。
- **Performance:**
  - outbox：SKIP LOCKED 降低锁等待，提升多实例吞吐稳定性；
  - reindex：续租避免并发重建，保护 ES 与下游，减少系统抖动。

### Security Check Summary（本次变更）
- ✅ internal/ops：新增启动期校验（prod 下 fail-closed），避免 break-glass 开启但 token/allowlist/Redis 漏配导致“403 风暴”或不可运维。
- ✅ legacy 入口：`/api/search/internal/reindex` 默认禁用并返回迁移提示，降低误用与攻击面（仍保留可回滚开关用于短期兼容）。
- ✅ dev-only 危险项：prod 下禁止固定验证码与敏感链接回传（启动期阻断误配），降低“dev 配置被带入生产”的事故源。

## Testing and Deployment
- **Testing:**
  - outbox 并发认领：多线程/多实例冒烟（可用 MySQL 8 真实环境验证）；
  - reindex：模拟长任务（sleep/大批次），验证续租生效且并发请求返回 409+jobId；
  - internal/ops：开启 break-glass 时验证启动校验与 doctor.sh 提示；关闭时确认默认 deny。
- **Deployment:**
  1) 先灰度 outbox SKIP LOCKED（可配开关回滚）；  
  2) 再上线 reindex 续租（仅影响运维入口）；  
  3) 最后默认禁用 legacy 路径并发布迁移窗口；  
  4) prod 护栏上线后需提前在配置中心清理 dev-only 配置，避免启动失败。  
