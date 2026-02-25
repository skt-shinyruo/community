# Task List: ops_hardening_outbox_reindex

Directory: `.helloagents/archive/2026-02/202602031035_ops_hardening_outbox_reindex/`

---

## 1. Schema 对齐（Outbox / Consumed Event）
- [√] 1.1 补齐 content-service outbox 索引/唯一约束：更新 `deploy/mysql-init/020_schema_content.sql`，对齐 `outbox_event` 的 `uk_outbox_event_id(event_id)` 与 `idx_outbox_status_next(status, next_retry_at, id)`（verify why.md#core-scenarios -> Outbox 轮询不扫表）
  > Note: `event_id` 已存在 unique 约束；本次重点修复 `idx_outbox_status_next` 形态，并增加 drift 重建逻辑（历史索引缺列时 drop+recreate）。
- [√] 1.2 补齐 message-service 幂等表唯一约束与清理索引：更新 `deploy/mysql-init/030_schema_message.sql`，为 `consumed_event` 增加 `uk_consumed_event_id(event_id)` 与 `idx_consumed_event_at(consumed_at, id)`（verify why.md#core-scenarios -> Kafka 重复投递不产生重复副作用）
  > Note: `event_id` 已存在 unique 约束；本次将清理索引调整为 `(consumed_at, id)` 以匹配分批删除与避免扫表。
- [√] 1.3 补齐 search-service 幂等表唯一约束与清理索引：更新 `deploy/mysql-init/040_schema_search.sql`，为 `search_consumed_event` 增加 `uk_search_consumed_event_id(event_id)` 与 `idx_search_consumed_event_at(consumed_at, id)`（verify why.md#core-scenarios -> Kafka 重复投递不产生重复副作用）
  > Note: `event_id` 已存在 unique 约束；本次将清理索引调整为 `(consumed_at, id)` 以匹配分批删除与避免扫表。
- [√] 1.4 提供已有环境迁移脚本：新增 `scripts/mysql-migrate-ops-harden-schema.sql`（包含重复数据预检、去重策略、ALTER TABLE 以及回滚提示），并在 `scripts/doctor.sh` 增加入口提示（depends on 1.1-1.3）

## 2. internal 运维入口治理（break-glass + 审计）
- [√] 2.1 统一运维入口清单与路径规范：校验 `/internal/search/reindex`、`/internal/*/outbox/replay`、`/internal/*/likes/backfill` 均被 `common/.../InternalOpsGuardFilter.java` 覆盖；必要时补齐分类规则与日志字段（verify why.md#core-scenarios -> internal 运维入口默认关闭且可控开启）
  > Note: 路径规范与 guard 分类规则已覆盖上述三类高风险入口；like backfill 入口为 `/internal/content/likes/backfill`，符合 `endsWith(\"/likes/backfill\")`。
- [√] 2.2 gateway 侧 legacy 路由兜底：确认 `POST /api/search/internal/reindex` 默认禁用并返回 410（如未实现则补齐），并确保 `POST /api/ops/search/reindex` 走管理员鉴权（verify runbook：`.helloagents/modules/runbooks/internal-ops.md`）
  > Note: gateway 已实现 `410 Gone` 提示迁移（legacy `/api/search/internal/reindex`）并对 `/api/ops/**` 做 ADMIN 鉴权收敛。

## 3. 定时任务治理（多实例可控）
- [√] 3.1 message/search 幂等清理任务分批化：更新 `message-service/.../ConsumedEventCleanupJob.java` 与 `search-service/.../SearchConsumedEventCleanupJob.java`，将 `delete where consumed_at < ?` 改为“分批 delete + limit + 循环”，并暴露删除计数指标（depends on 1.2-1.3）
- [√] 3.2 选择 single-flight 锁方案并落地（ADR-001）：在 `common` 新增轻量锁组件（优先复用 MySQL 或按需使用 Redis），对“必须 single-flight”的 reconcile/cleanup 任务加锁开关（verify how.md#adr-001-scheduled-single-flight-方案）
  > Note: 选用 Redis single-flight（setIfAbsent + TTL + compare-and-del 释放），并接入 message/search cleanup 与 content/message projection reconcile。

## 4. Security Check
- [√] 4.1 执行安全检查（G9）：确认迁移脚本不会泄露敏感信息；internal ops 日志不输出 token；break-glass 默认关闭且 fail-closed；配置项由 `StartupValidation` 在 prod 下校验。
  > Note: `deploy/docker-compose.yml` Tab 缩进已修复，`docker compose ... config` 可通过；全量 `mvn test` 仍存在 `auth-service` 的既有用例失败，本次以改动相关模块测试为主（见 6.1）。

## 5. Documentation Update
- [√] 5.1 更新知识库：补充 schema 对齐与迁移 runbook（更新 `.helloagents/data.md`、`.helloagents/modules/runbooks/backend-upgrade-rollback.md`），并在 `.helloagents/modules/runbooks/security-review-*.md` 记录本次加固点；同步更新模块变更记录与 `.helloagents/CHANGELOG.md`。

## 6. Testing
- [√] 6.1 幂等语义回归测试：`message-service`/`search-service` 已覆盖“重复 eventId 不产生重复副作用”与“失败不应提前标记 consumed”场景（H2/Mock 组合）；并已执行 `mvn -pl common,content-service,message-service,search-service -am test` 验证通过。
