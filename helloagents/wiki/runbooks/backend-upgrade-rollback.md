# 后端架构升级灰度/回滚清单

## 1. 阶段与开关（P0 → P1 → P2）

### P0（计数原子化 / traceId / 可信 IP）
- `gateway.trusted-proxy.enabled`：默认 false；启用后需配置 `gateway.trusted-proxy.cidrs`
- `gateway.trusted-proxy.cidrs`：可信代理网段（CIDR）

### P1（幂等治理 / 清理 / ES alias reindex）
- `search.idempotency.cleanup-enabled` / `search.idempotency.retention-days`
- `search.idempotency.cleanup-batch-size` / `search.idempotency.cleanup-max-batches`（分批删除上限，避免单次扫表/长事务）
- `search.idempotency.cleanup-single-flight` / `search.idempotency.cleanup-lock-ttl-seconds`（多实例下可选 single-flight，避免重复清理放大负载）
- `search.idempotency.cleanup-interval-ms`
- `message.idempotency.cleanup-enabled` / `message.idempotency.retention-days`
- `message.idempotency.cleanup-batch-size` / `message.idempotency.cleanup-max-batches`
- `message.idempotency.cleanup-single-flight` / `message.idempotency.cleanup-lock-ttl-seconds`
- `message.idempotency.cleanup-interval-ms`
- `search.index.keep-history`：保留历史索引数
- reindex 触发：`POST /internal/search/reindex`
  - Schema 预检/迁移：执行 `scripts/mysql-migrate-ops-harden-schema.sql`（确保幂等表 `event_id` 唯一约束与 `idx_*_consumed_at(consumed_at, id)` 存在，避免清理/查询退化为扫表）

### P2（Outbox）
- `content.events.outbox.enabled`：开启后写入 outbox，关闭则恢复 After-Commit 直发
- `content.events.outbox.relay-enabled`：单独控制 relay
- `content.events.outbox.batch-size / max-retries / base-delay-ms / max-delay-ms / relay-interval-ms / send-timeout-ms`

## 2. 验证点（灰度观察）
- traceId：401/403/429 响应体 `Result.traceId` 与 `X-Trace-Id` 一致
- 评论计数：并发新增评论后 `comment_count` 不丢更新
- 幂等表：重复 eventId 不产生重复副作用，`*_consumed_event` 按 retention 清理
- 搜索 reindex：alias 切换不影响线上检索；索引数量符合 `keep-history`
- Outbox：`outbox_event` backlog 可控，SENT/RETRY 变化正常

## 3. 回滚步骤（按需）
- 可信代理：`gateway.trusted-proxy.enabled=false`，忽略 XFF
- reindex：手工将 alias 指向上一版本索引（ES alias API）
- Outbox：`content.events.outbox.enabled=false`（恢复 After-Commit 直发）；必要时设置 `relay-enabled=false`
- 清理任务：将 `*.idempotency.cleanup-enabled=false` 暂停删除
