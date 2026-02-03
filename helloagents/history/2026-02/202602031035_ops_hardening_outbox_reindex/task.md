# Task List: 运维与一致性加固（Outbox 并发 + Reindex 单飞 + internal/ops 护栏）

Directory: `helloagents/plan/202602031035_ops_hardening_outbox_reindex/`

---

## 1. Outbox 并发认领与吞吐（content/social/user）
- [√] 1.1 content-service：outbox 认领 SQL 支持 `SKIP LOCKED`（保留回退开关），验证 why.md#requirement-outbox-认领并发与吞吐outbox-claim-concurrency-scenario-多实例-relay-并发时不头阻塞skip-locked
- [√] 1.2 social-service：outbox 认领 SQL 支持 `SKIP LOCKED`（保留回退开关），验证 why.md#requirement-outbox-认领并发与吞吐outbox-claim-concurrency-scenario-多实例-relay-并发时不头阻塞skip-locked
- [√] 1.3 user-service：outbox 认领 SQL 支持 `SKIP LOCKED`（保留回退开关），验证 why.md#requirement-outbox-认领并发与吞吐outbox-claim-concurrency-scenario-多实例-relay-并发时不头阻塞skip-locked

## 2. Outbox 运维能力一致性（user-service 补齐）
- [√] 2.1 user-service：新增 outbox 运维入口 `/internal/users/outbox/health|replay`，与 content/social 对齐，验证 why.md#requirement-outbox-运维能力一致性outbox-ops-parity-scenario-三个服务均支持-outbox-healthreplay-且受-break-glass-保护ops-endpoints-parity
- [√] 2.2 deploy：补齐 user-service 的 ops 配置模板（`ops.users.token` + `ops.guard.outbox-replay.*`），并更新 `deploy/.env.example` 增加 `OPS_USERS_TOKEN*`，验证 why.md#requirement-internalops-配置一致性与启动校验internal-ops-config-drift-scenario-break-glass-开关开启但关键配置缺失时-fail-fastfail-fast-when-enabled
- [√] 2.3 scripts：更新 `scripts/doctor.sh` 增加 `OPS_USERS_TOKEN` 检查与提示，验证 why.md#requirement-internalops-配置一致性与启动校验internal-ops-config-drift-scenario-break-glass-开关开启但关键配置缺失时-fail-fastfail-fast-when-enabled

## 3. Reindex single-flight 锁续租（search-service）
- [√] 3.1 search-service：为 `ReindexJobService` 增加锁续租/心跳（owner=jobId + 原子续租），验证 why.md#requirement-reindex-single-flight-锁续租reindex-lock-renewal-scenario-reindex-运行超过-30-分钟也不会并发重建lock-renewal
- [√] 3.2 search-service：补齐单测/冒烟（并发触发返回 409 + jobId；长任务续租不失效；崩溃后 TTL 兜底），验证 why.md#requirement-reindex-single-flight-锁续租reindex-lock-renewal-scenario-reindex-运行超过-30-分钟也不会并发重建lock-renewal

## 4. internal/ops 护栏：配置漂移治理（common + deploy/scripts）
- [√] 4.1 common：补齐启动校验（当 `OPS_OUTBOX_REPLAY_ENABLED/OPS_SEARCH_REINDEX_ENABLED` 开启时强校验 allowlist/token/Redis），验证 why.md#requirement-internalops-配置一致性与启动校验internal-ops-config-drift-scenario-break-glass-开关开启但关键配置缺失时-fail-fastfail-fast-when-enabled
- [√] 4.2 deploy：清理/收敛无效或误导配置（例如 docker-compose 中多处透传但实际不生效的 `INTERNAL_TOKEN`），验证 why.md#requirement-internalops-配置一致性与启动校验internal-ops-config-drift-scenario-break-glass-开关开启但关键配置缺失时-fail-fastfail-fast-when-enabled

## 5. Legacy 入口收敛（gateway/search/scripts/docs）
- [√] 5.1 gateway：默认禁用 `POST /api/search/internal/reindex` 并返回明确迁移提示（保留短期开关可启用兼容），验证 why.md#requirement-legacy-对外入口收敛legacy-search-internal-reindex-scenario-legacy-路径默认不可用且给出迁移引导disable-and-guide
- [√] 5.2 scripts：更新 `scripts/search-reindex.sh` 统一使用 `/api/ops/search/reindex` 并提示 ops guard 前置条件，验证 why.md#requirement-legacy-对外入口收敛legacy-search-internal-reindex-scenario-legacy-路径默认不可用且给出迁移引导disable-and-guide
- [√] 5.3 docs/KB：同步更新 `docs/*` 与 `helloagents/wiki/*`（弃用窗口、运维 runbook、迁移指引），验证 why.md#requirement-legacy-对外入口收敛legacy-search-internal-reindex-scenario-legacy-路径默认不可用且给出迁移引导disable-and-guide

## 6. dev/prod 护栏（auth-service + docs）
- [√] 6.1 auth-service：prod profile 下禁止固定验证码与敏感链接回传（fail-fast），验证 why.md#requirement-devprod-护栏dev-prod-guardrails-scenario-prod-下禁止固定验证码与敏感链接回传prod-guardrails
- [√] 6.2 docs：收敛默认演示账号/口令说明到 dev-only 文档，并在根 README 强提示“生产不可用”，验证 why.md#requirement-devprod-护栏dev-prod-guardrails-scenario-prod-下禁止固定验证码与敏感链接回传prod-guardrails

## 7. Security Check
- [√] 7.1 执行安全检查（按 G9：权限与 token、allowlist、legacy 路径暴露面、EHRB 风险），并记录关键结论到变更说明（how.md）

## 8. Testing
- [?] 8.1 outbox：验证 Kafka down → outbox 入库不丢 → 恢复后补发；多实例 relay 并发下无明显阻塞（需联调环境）
- [√] 8.2 reindex：验证长任务续租、并发冲突 409、崩溃 TTL 兜底
- [?] 8.3 回归：已运行 `mvn test` + `scripts/doctor.sh`；`scripts/smoke-*` 需服务启动后验证
