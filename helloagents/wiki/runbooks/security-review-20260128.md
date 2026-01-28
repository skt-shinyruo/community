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
