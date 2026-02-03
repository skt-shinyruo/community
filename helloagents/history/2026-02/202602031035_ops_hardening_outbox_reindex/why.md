# Change Proposal: ops_hardening_outbox_reindex

## Requirement Background

当前社区系统已具备较完整的事件驱动能力（Kafka + Outbox + 幂等消费表），并配套了 internal 运维入口（reindex / outbox replay / backfill）与 runbook。但在代码与数据库初始化脚本/现网库之间存在“漂移”风险，导致以下问题：

1. **Schema 不一致破坏语义**：部分库缺少 `event_id` 唯一约束或关键索引，直接导致幂等消费与 Outbox 轮询退化（重复副作用/扫表/锁竞争）。
2. **internal 安全边界依赖链复杂**：`/internal/**` 走自定义 filter 兜底（`InternalTokenFilter` + `InternalOpsGuardFilter`），一旦 filter 未生效/配置漂移/旁路暴露，会把高风险运维能力暴露到更大范围。
3. **多实例下定时任务放大负载**：部分 `@Scheduled` 任务在多副本部署时会重复跑（即使业务上幂等也会重复扫描/删除/回填），在故障期更容易放大下游压力。
4. **漂移后的修复能力高风险**：backfill/reindex/replay 本身是必要的兜底，但需要更强的“默认关闭 + 可审计 + 速率/并发控制 + 可观测”治理，否则运维入口本身会成为事故源。
5. **配置分散导致一致性成本高**：跨服务重复的安全/调用配置（JWT、internal token、timeout 等）容易出现“部分服务先变更、部分服务未同步”的灰色故障。

本变更的目标是：将“语义正确性”和“默认安全态”前置到可验证的工程约束上，降低漂移概率，并把运维能力从“高风险入口”升级为“可控的修复工具”。

## Change Content

1. **统一 Outbox / 幂等消费表 Schema（SSOT=初始化脚本 + 迁移脚本）**
   - 补齐缺失的唯一约束与关键索引，确保与代码中的“insert-first + DuplicateKey/唯一约束”语义一致。
   - 为已有环境提供可回滚的迁移脚本（包含重复数据预检与去重策略）。

2. **internal 运维入口安全收敛（break-glass + 双 token + allowlist）**
   - 以 `InternalTokenFilter` 作为 internal 最小权限兜底；
   - 以 `InternalOpsGuardFilter` 对高风险运维动作（reindex/outbox replay/like backfill）进行二次校验与限流/并发控制（fail-closed）。

3. **定时任务治理（多实例可控）**
   - 明确哪些任务天然可水平扩展（队列/锁行语义），哪些任务必须 single-flight；
   - 为 single-flight 任务引入轻量“集群锁”机制（优先复用已有 MySQL/Redis，避免新增基础设施）。

4. **修复工具可观测与可审计**
   - 为 reindex/replay/backfill 增加统一的审计日志、指标与告警维度；
   - 约束执行入口（默认关闭、显式开启、来源 allowlist、速率限制、单飞锁）。

5. **配置治理与防漂移**
   - 统一关键配置项命名规范与校验策略（依托 `StartupValidation`）；
   - 增加“schema/配置漂移检查”脚本或 CI 校验，阻断隐性退化。

## Impact Scope

- **Modules:**
  - `deploy/mysql-init/*`（DB 初始化脚本）
  - `scripts/*`（迁移/校验/演练脚本）
  - `common`（internal 保护器/启动校验/通用能力增强）
  - `content-service` / `message-service` / `search-service`（Outbox/幂等/运维入口/定时任务）
  - `helloagents/wiki/runbooks/*`（运维流程与验收清单）
- **Files (planned):**
  - `deploy/mysql-init/020_schema_content.sql`
  - `deploy/mysql-init/030_schema_message.sql`
  - `deploy/mysql-init/040_schema_search.sql`
  - `scripts/*`（新增迁移与 drift check）
  - `common/src/main/java/com/nowcoder/community/common/internal/InternalOpsGuardFilter.java`（必要时补齐接入/覆盖场景）
  - 相关服务的 `*CleanupJob.java` / `Internal*Controller.java`（必要时补齐审计/批处理/保护）
- **APIs:**
  - `/internal/search/reindex`
  - `/internal/*/outbox/replay`
  - `/internal/*/likes/backfill`
- **Data:**
  - `outbox_event`
  - `consumed_event`
  - `search_consumed_event`

## Core Scenarios

### Requirement: Outbox / 幂等消费 Schema 对齐
**Module:** deploy/mysql-init + message/search/content
将“幂等语义”从代码注释升级为数据库强约束，避免重复副作用与性能退化。

#### Scenario: Kafka 重复投递不产生重复副作用
- 事件重复到达时：
  - `message-service` 的通知不会重复写入
  - `search-service` 的索引不会重复执行副作用
- 成功标准：依赖 `event_id` 唯一约束触发 DuplicateKey/唯一冲突路径，消费侧能正确短路返回。

#### Scenario: Outbox 轮询不扫表
- Outbox relay 每 5s 轮询时不产生全表扫描
- 成功标准：具备 `(status, next_retry_at, id)` 索引支撑候选集查询；`event_id` 唯一约束避免重复入库。

### Requirement: internal 运维入口默认关闭且可控开启
**Module:** common + runbooks

#### Scenario: break-glass 未开启时直接拒绝
- 不配置/不开启时：
  - 返回 403（默认拒绝）
  - 不泄露敏感信息（token/payload）

#### Scenario: 临时开启后仅 allowlist + 双 token 允许执行
- 同时满足：
  - `X-Internal-Token` 通过（最小权限）
  - `X-Ops-Token` 通过（运维强保护）
  - `allowlist` 命中（缩小爆炸半径）
  - Redis 可用（single-flight + rate limit）
- 成功标准：可按 runbook 演练并可回滚到默认关闭。

### Requirement: 多实例下定时任务不放大风险
**Module:** message/search/content/common

#### Scenario: 多副本部署时单飞任务只执行一次
- 多实例同时运行时：
  - 清理类/纠偏类任务不会并发重复删除/扫描导致放大负载
  - 发生故障时不形成雪崩（fail-closed / 限速）

## Risk Assessment

- **Risk:** DDL 变更可能因历史重复数据导致失败，或在大表上锁表影响写入。
  - **Mitigation:** 上线前做重复数据预检；提供去重脚本；选择低峰执行；必要时分批/在线变更方案；提供回滚手段。
- **Risk:** internal token 泄露或误配置导致运维入口被滥用。
  - **Mitigation:** break-glass 默认关闭 + allowlist + 双 token + single-flight + rate-limit；日志审计且不打印敏感信息；依托 `StartupValidation` 在 prod 下 fail-closed。
- **Risk:** 多实例任务治理引入新锁机制导致误阻断或死锁。
  - **Mitigation:** 锁 TTL + 失败快速降级；仅对非关键任务启用 single-flight；提供开关与观测指标。
