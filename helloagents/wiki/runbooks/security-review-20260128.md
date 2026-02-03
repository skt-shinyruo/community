# 安全检查记录：2026-01-28（后端架构治理）

## 范围
- internal-token（按服务 token、轮转窗口、全局兜底）
- internal 运维接口（/internal/** 的访问边界、误配置风险）
- gateway 采集链路（指标高基数、内存有界性、traceId 透传）
- 内部调用（降级语义、错误码透传、日志是否泄露敏感信息）

## 关键结论（基于代码审阅）
1. internal 接口已由 `InternalTokenFilter` 统一兜底，但若生产环境仍设置 `INTERNAL_TOKEN`，会形成“全局 token”兜底路径，爆炸半径扩大。
2. Outbox 运维接口（health/replay）属于高权限能力，必须确保仅在 internal-token 保护下暴露，且避免写入敏感日志（token/payload）。
3. gateway analytics 去重需保持有界（TTL/size/采样），避免高基数导致内存膨胀；并确保采集失败可观测（指标），且不影响主链路。
4. 内部调用降级（fail-open）必须显式化：仅限非关键读路径，写路径校验默认 fail-closed。

## 待办整改清单
- [ ] 生产环境逐步移除全局 `INTERNAL_TOKEN`，改为按服务 token（并配套轮转 runbook）。
- [ ] internal 运维接口增加来源网段/网关侧 allowlist（可选），减少误暴露风险。
- [ ] 确认 outbox payload 不包含敏感信息（或进行脱敏/最小化）。
- [ ] 指标 tag 避免高基数（不要把 userId/ip 作为 tag）。

## 验证建议
- 演练 internal-token 轮转：current + previous 灰度窗口 → 调用方逐步切换 → 清理 previous。
- 演练 outbox：Kafka 短暂不可用 → 写入不丢 → 恢复后补发 → failed 可观测可重放。

## 补充记录：2026-02-03（Outbox/幂等/定时任务加固）
1. **Schema drift 修复**：对齐 Outbox/幂等表关键索引形态（`idx_outbox_status_next(status, next_retry_at, id)`、`idx_consumed_event_at(consumed_at, id)`、`idx_search_consumed_at(consumed_at, id)`），并增加 drift 自修复，避免轮询/清理退化为扫表。
2. **消费幂等语义落地**：消费侧以 `event_id` 唯一约束为准（insert-first），重复投递只会被数据库约束吸收，副作用不重复执行。
3. **多实例定时任务风险收敛**：新增 Redis single-flight 工具 `SingleFlightTaskGuard`，并在 cleanup/reconcile 类任务上提供可选开关，降低多实例重复执行放大下游压力的风险。
4. **迁移路径**：提供 `scripts/mysql-migrate-ops-harden-schema.sql` 用于上线前预检/去重指导/条件 DDL，避免在已有数据上直接加唯一约束导致失败或长时间锁表。
