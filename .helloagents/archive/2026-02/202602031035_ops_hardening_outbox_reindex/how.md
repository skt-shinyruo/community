# Technical Design: ops_hardening_outbox_reindex

## Technical Solution

### Core Technologies
- MySQL（唯一约束/索引/DDL 迁移与回滚）
- Spring Boot（定时任务、启动校验、Filter 兜底）
- MyBatis/JdbcTemplate（幂等表写入与清理）
- Redis（internal ops guard：single-flight + rate limit；可选用于 scheduler 锁）
- Kafka（事件投递、DLQ 重放）
- Micrometer/Prometheus（指标与告警）

### Implementation Key Points

#### 1) Schema 统一：让数据库成为语义的“最终裁判”

目标：对齐代码中的幂等语义（insert-first + 唯一约束/duplicate key）与轮询查询的性能要求。

**Outbox（content-service）**
- `outbox_event.event_id` 增加唯一约束（避免重复入库/重复投递）
- 增加索引 `idx_outbox_status_next(status, next_retry_at, id)`（支撑按状态+可重试时间的候选集查询）

**Consumed Event（message-service / search-service）**
- `consumed_event.event_id` / `search_consumed_event.event_id` 增加唯一约束
  - 使 `DataIntegrityViolationException` / `DuplicateKeyException` 路径可达
  - 确保重复投递不会重复写副作用
- 增加 `consumed_at` 索引（支撑清理任务避免扫表）
- 对清理任务建议改为“分批 delete + limit”（降低长事务与锁竞争）

**迁移策略（已有环境）**
1. 预检：检查 `event_id` 是否存在重复
2. 去重：保留最早写入的记录，删除重复行（并记录审计）
3. 加索引/约束：执行 `ALTER TABLE` 增量变更
4. 验证：通过“重复投递演练”验证消费侧幂等分支生效

> 注：DDL 具体语句放入迁移脚本（`scripts/`）并提供回滚策略；生产执行遵循低峰窗口与备份策略。

#### 2) 防漂移：把“Schema/配置一致性”做成可自动验证

目标：避免再次出现“代码依赖唯一约束，但初始化脚本缺失”的隐性回归。

推荐新增两类校验：
1. **静态 drift check（不连库）**：在 CI 或 `scripts/doctor.sh` 中校验 `deploy/mysql-init/*` 是否包含关键索引/唯一约束片段。
2. **动态 drift check（连库）**：在集成测试环境启动后，通过 `information_schema.statistics` / `show index` 校验真实库包含预期索引（可选）。

#### 3) internal 运维入口：以 runbook 为准的 break-glass 强保护

现状（与知识库一致）：
- `/internal/**` 最小权限由 `InternalTokenFilter` 兜底（按 segment token）
- 高风险动作由 `InternalOpsGuardFilter` 二次校验：
  - 默认关闭（`ops.guard.<op>.enabled=false`）
  - 必须配置 allowlist
  - 必须通过 `X-Ops-Token`（按 segment 分域）
  - Redis 提供 single-flight 与 rate limit，Redis 不可用时 fail-closed（503）

需要补齐/强化的工程化措施：
1. **运维入口清单与路径规范**：确保高风险入口统一匹配 guard 的路由分类（reindex/outbox replay/like backfill）。
2. **审计与告警**：统一关键日志关键字（例如 `[internal-ops]`），并提供指标（成功/拒绝/限流/冲突）。
3. **网关侧旁路约束**：对 legacy 路由返回 410 并提示迁移；对 `/api/ops/**` 做管理员鉴权与速率限制（与 runbook 对齐）。

#### 4) 定时任务治理：按“是否天然可水平扩展”分层处理

将 `@Scheduled` 任务分成两类：
1. **天然可水平扩展**（不需要额外集群锁）：
   - 基于队列/集合的 pop（例如 Redis set pop）
   - 基于 DB 行锁/`SKIP LOCKED` 的 claim（Outbox relay）
2. **必须 single-flight**（需要集群锁/单实例执行）：
   - 全表扫描类 reconcile
   - 大范围 delete 清理（幂等表清理、历史清理）
   - 其他运维/批处理类任务

**ADR（锁方案选型）**
### ADR-001: Scheduled Single-Flight 方案
**Context:** 多实例部署时，部分定时任务重复执行会放大 DB/下游压力；需要低侵入的 single-flight 能力。
**Decision:** 采用“可选启用”的集群锁机制，对 single-flight 任务加锁；默认只对非关键/可跳过任务启用。
**Rationale:**
- 不引入新的基础设施优先（复用 MySQL/Redis）
- 锁 TTL + 失败快速返回，避免死锁与阻断主链路
**Alternatives:**
- 使用外部调度（K8s CronJob）→ 拓扑依赖强、与本地/Compose 不一致
- 引入通用三方库（如 ShedLock）→ 依赖管理成本更低但需要跨模块统一接入
- Redis 自研锁（setIfAbsent）→ 实现简单但要求所有环境具备 Redis
**Impact:**
- 需要在任务执行入口增加锁判断与指标
- 需要为锁机制提供开关与可观测性

## Security and Performance

- **Security:**
  - internal 最小权限（`X-Internal-Token`）与 ops 强保护（`X-Ops-Token`）双层控制
  - break-glass 默认关闭 + allowlist 强制配置（fail-closed）
  - `StartupValidation` 在 prod profile 下校验关键安全配置（JWT secret、internal token、ops guard 前置条件）
- **Performance:**
  - 通过索引修复消除 outbox/幂等清理的扫表风险
  - 清理任务分批执行，避免长事务与锁表
  - 多实例任务治理降低故障期放大效应

## Testing and Deployment

- **Testing:**
  - 增加“重复投递”测试：验证 message/search 幂等分支可达（依赖唯一约束）
  - 增加 schema drift check：初始化脚本层面的静态校验（阻断回归）
  - 演练 internal ops：按 `.helloagents/modules/runbooks/internal-ops.md` 走完整流程（默认关闭→临时开启→执行→关闭）
- **Deployment:**
  - DDL 迁移：先备份，再预检重复数据，再执行迁移，再验证，再回滚开关到默认安全态
  - 变更发布建议拆分为两阶段：
    1) 先上 DB 迁移（补齐唯一约束/索引）
    2) 再上代码改造（清理分批/锁机制/观测与告警增强）
