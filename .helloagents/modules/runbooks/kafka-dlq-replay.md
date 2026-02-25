# Runbook：Kafka DLQ 回放（安全流程）

本 Runbook 用于将 `<topic>.dlq` 中的消息按“受控方式”回放到目标 topic（通常为原 topic），用于离线修复后重新触发消费，或在错误修复后补齐最终一致数据。

> ⚠️ 重要：DLQ 回放属于高风险操作，可能触发重复副作用（重复通知/重复索引更新/重复计数）。执行前必须确认消费端具备幂等保障（例如 `eventId` 去重表）。

---

## 1. 适用范围

- 事件 Topic（示例）：
  - `community.event.post.v1` / `community.event.comment.v1` / `community.event.social.v1` / `community.event.moderation.v1`
  - DLQ：`<topic>.dlq`
- 消费方（典型）：
  - `search-service`：ES 索引更新（应具备 `search_consumed_event` 幂等表）
  - `message-service`：通知生成（应具备 `consumed_event` 幂等表）

---

## 2. 前置检查（必须）

1) **确认失败原因已修复**
- 例如：反序列化失败（schema 修复）、下游依赖故障（ES/DB 恢复）、业务校验缺陷（bug 修复）。

2) **确认消费端幂等**
- 如果幂等缺失，禁止回放（或必须先实现幂等再回放）。
- 幂等建议以“持久化去重”为准（DB/Redis），不要依赖 JVM 内存去重。

3) **确认回放影响面与回滚策略**
- 本次回放消息数量（小批量优先）。
- 是否会触发外部副作用（例如通知推送、计数变化）。
- 回放后如何验证（指标、日志、抽样核对）。

4) **确认目标 topic**
- 通常回放到原 topic：`<topic>`
- 不建议跨 topic 回放（除非明确知道消费逻辑与契约兼容）

---

## 3. 回放方式（Docker Compose）

项目提供脚本：`scripts/kafka-replay-dlq.sh`（通过 `docker compose exec` 在 kafka 容器内执行 consumer/producer）。

### 3.1 Dry-run（建议先做）

目标：只读取 DLQ，不写回（验证 topic、key 分隔、数量与内容格式）。

建议环境变量：
- `DLQ_TOPIC`：例如 `community.event.post.v1.dlq`
- `TARGET_TOPIC`：例如 `community.event.post.v1`
- `MAX_MESSAGES`：小批量，例如 `50`
- `SLEEP_MS`：例如 `50`（节流，避免瞬间打爆消费端）
- `DRY_RUN=true`

### 3.2 正式回放（小批量滚动）

建议策略：
- 每次回放 `50~200` 条，观察消费端指标与日志，再进行下一批
- 若出现异常（持续 DLQ 增长、消费错误），立即停止并回滚修复

---

## 4. 风控要点（强制遵守）

1) **不要一次性回放大量消息**
- 易造成消费端瞬时压力、DB/ES 雪崩、触发二次故障。

2) **不要在“幂等不确定”的服务上回放**
- 典型风险：重复通知、重复索引更新造成脏数据或资源浪费。

3) **优先保证“可观测”**
- 回放期间重点观察：
  - `kafka_dlq_published_total`（是否继续写入 DLQ）
  - 消费端的错误日志（按 traceId/错误类型聚合）
  - outbox backlog（若相关服务启用 outbox）

4) **避免在高峰期执行**
- 建议在低峰窗口，必要时临时对消费组进行限速或扩容。

---

## 5. 验证清单（回放后）

- [ ] DLQ backlog 是否下降到可接受水平
- [ ] 下游系统状态是否恢复（ES 索引一致、通知生成符合预期）
- [ ] 是否出现重复副作用（抽样核对 + 幂等表命中情况）
- [ ] 是否需要补充监控/告警阈值（避免下次“发现太晚”）
