# 安全检查记录：2026-01-28（后端架构治理）

> ⚠️ 现状更新（2026-02-08）：当前代码已移除 `/internal/**` 的 header token 鉴权机制（不校验 `X-Internal-Token` / `X-Ops-Token`，调用方也不再发送）。
> 因此本文件中与 internal-token 相关的结论/待办仅作为当时审阅记录，不再作为现行策略；现行策略与排障以 `helloagents/wiki/runbooks/internal-ops.md` 为准。

## 范围
- internal 接口边界（/internal/** 的访问边界、误配置风险）
- internal 运维接口（/internal/** 高成本写入口：reindex/outbox replay 等）
- gateway 采集链路（指标高基数、内存有界性、traceId 透传）
- 内部调用（降级语义、错误码透传、日志是否泄露敏感信息）

## 关键结论（基于代码审阅）
1. internal 接口当前为服务端侧 `permitAll`（不做 header token 鉴权），因此安全边界完全依赖部署/网关/网络隔离：必须确保服务端口不对公网暴露，且 gateway/反代显式拒绝 `/internal/**`，避免误配“直接变公网 API”。
2. Outbox 运维接口（health/replay）属于高成本/高权限写入口，必须通过网络边界封锁；可选增加并发/频率上限与审计日志（避免写入敏感 payload），降低误触发/DoS 风险。
3. gateway analytics 去重需保持有界（TTL/size/采样），避免高基数导致内存膨胀；并确保采集失败可观测（指标），且不影响主链路。
4. 内部调用降级（fail-open）必须显式化：仅限非关键读路径，写路径校验默认 fail-closed。

## 待办整改清单
- [ ] 生产环境确保各服务实例端口仅内网可达（K8S：ClusterIP；Docker：不 publish 端口；云环境：安全组/ACL）。
- [ ] gateway 与外部反代/NLB 显式拒绝 `/internal/**`（建议加回归测试/探针，防止策略漂移）。
- [ ] internal 高成本入口增加并发/频率限制与审计（可选；在“不引入 token 鉴权”的前提下做 DoS 缓解）。
- [ ] 确认 outbox payload 不包含敏感信息（或进行脱敏/最小化）。
- [ ] 指标 tag 避免高基数（不要把 userId/ip 作为 tag）。

## 验证建议
- 演练“端口误暴露/反代误配”：从公网入口访问 `/internal/**` 应被拒绝（403/404），并可观测/可告警。
- 演练 outbox：Kafka 短暂不可用 → 写入不丢 → 恢复后补发 → failed 可观测可重放（仅在内网触发 replay）。
- 演练 reindex（如有）：确认并发/频率限制生效，避免重复触发压垮 ES/DB。

## 补充记录：2026-02-03（Outbox/幂等/定时任务加固）
1. **Schema drift 修复**：对齐 Outbox/幂等表关键索引形态（`idx_outbox_status_next(status, next_retry_at, id)`、`idx_consumed_event_at(consumed_at, id)`、`idx_search_consumed_at(consumed_at, id)`），并增加 drift 自修复，避免轮询/清理退化为扫表。
2. **消费幂等语义落地**：消费侧以 `event_id` 唯一约束为准（insert-first），重复投递只会被数据库约束吸收，副作用不重复执行。
3. **多实例定时任务风险收敛**：新增 Redis single-flight 工具 `SingleFlightTaskGuard`，并在 cleanup/reconcile 类任务上提供可选开关，降低多实例重复执行放大下游压力的风险。
4. **迁移路径**：提供 `scripts/mysql-migrate-ops-harden-schema.sql` 用于上线前预检/去重指导/条件 DDL，避免在已有数据上直接加唯一约束导致失败或长时间锁表。
