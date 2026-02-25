# Change Proposal: 生产可用 P0 加固（事件一致性/幂等/DLQ/同步调用韧性/MySQL 多 Schema）

## Requirement Background

本项目已经具备微服务骨架（gateway 统一入口、Kafka 事件、DLQ、Prometheus/Loki/Grafana），但在“生产可用”维度仍存在若干高风险点，会导致：

1. **消息消费出现“永久丢通知/重复副作用”**：message-service 的消费流程存在事务边界不可靠与幂等写入时机问题（`message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventConsumer.java:35`、`message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventConsumer.java:40`、`message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventConsumer.java:77`、`message-service/src/main/java/com/nowcoder/community/message/kafka/NoticeEventConsumer.java:92`）。
2. **生产端可能产生“幽灵事件”**：在 DB 事务未提交甚至回滚时已发送 Kafka 事件（典型路径：`content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java:60` → `content-service/src/main/java/com/nowcoder/community/content/service/CommentService.java:122`）。
3. **同步调用缺少确定性的超时与可观测**：user-service 调用 social-service 仅做 try/catch 兜底，但缺少强制超时与指标采集，易导致级联慢请求/雪崩（`user-service/src/main/java/com/nowcoder/community/user/service/SocialServiceClient.java:17`、`user-service/src/main/java/com/nowcoder/community/user/config/UserRestClientConfig.java:14`）。
4. **DLQ 只有“写入能力”，缺少“运营闭环”**：已具备 DLQ 转储（`message-service/src/main/java/com/nowcoder/community/message/kafka/KafkaErrorHandlerConfig.java:19`、`search-service/src/main/java/com/nowcoder/community/search/kafka/KafkaErrorHandlerConfig.java:19`），但缺少进入/积压告警、回放脚本与 Runbook。
5. **MySQL 数据所有权不硬**：旧实现中多个服务在 compose 里共用同一个 schema，阻碍演进与权限最小化；P0 需要支持“同实例多 schema + 每服务独立账号最小权限”（最小表结构与拆分脚本位于 `deploy/mysql-init/010_schema_identity.sql` + `deploy/mysql-init/020/030/040_schema_*.sql`）。

本方案包聚焦 **P0（0–2 周）止血**：优先把“可证明正确”的一致性/幂等/回滚能力补齐，为后续 P1 Outbox、身份域解耦等打基础。

## Change Content

1. 修复 message-service 消费端“幂等 + 事务 + ack”的正确性：保证 ack 之前业务副作用已成功，且幂等记录与业务写入同事务。
2. 修复生产端“事务内直接 send Kafka”的幽灵事件：至少做到 **事务提交后再发送（After-Commit）**，先消除“回滚却发出事件”的硬伤（P0 不解决 100% 可靠投递，P1 用 Outbox 补齐）。
3. 同步调用（user-service → social-service）补齐 **超时 + 降级 + 指标**，避免级联故障拖垮核心链路。
4. DLQ 补齐 **监控告警 + 回放流程**：进入可告警、可定位、可回放（带边界与审批流程）。
5. MySQL 拆分为同实例多 schema（先拆非身份域）：`community_content` / `community_message` / `community_search`，并配套最小权限与备份/恢复脚本改造，保证可回滚。

## Impact Scope

- **Modules：**
  - `message-service`（消费端事务与幂等）
  - `content-service`、`social-service`（生产端 After-Commit 发送）
  - `common`（事务后回调工具、通用约定）
  - `user-service`（同步调用超时/指标/降级）
  - `deploy/`（MySQL 多 schema、Prometheus 告警）
  - `scripts/`（备份/恢复、DLQ 回放）
  - `docs/`（数据模型/观测/故障演练文档）

- **APIs：**
  - P0 不新增对外业务 API；可能新增“内部运维脚本/Runbook”使用的操作流程。

- **Data：**
  - MySQL：新增 schema 并迁移表归属（不改变表字段结构，P0 仅调整归属与连接配置）。
  - Kafka：DLQ 主题不变（`<topic>.dlq`），新增 DLQ 指标与告警规则。

## Core Scenarios

### Requirement: R1-message-consume-atomicity
<a id="r1-message-consume-atomicity"></a>
**Module:** message-service  
**Goal:** message-service 消费处理具备“可证明正确”的幂等与事务一致性：重复投递不重复写、失败重试不丢写、成功才 ack。

#### Scenario: R1S1-duplicate-eventId-no-duplicate-notice
<a id="r1s1-duplicate-eventid-no-duplicate-notice"></a>
当 Kafka 同一条事件（同 `eventId`）被重复投递/重复消费：
- 只创建 1 条 notice（不会重复通知）
- `consumed_event` 只会有 1 条记录

#### Scenario: R1S2-fail-between-idempotency-and-side-effect-no-data-loss
<a id="r1s2-fail-between-idempotency-and-side-effect-no-data-loss"></a>
当处理过程中发生异常（例如 DB 写入失败/JSON 异常/notice 写入失败）：
- 该条消息不应被 ack
- 重试后能再次执行（不会因幂等记录“提前写入”而永久跳过）
- 超过重试阈值后进入 DLQ

#### Scenario: R1S3-unknown-type-safe-skip
<a id="r1s3-unknown-type-safe-skip"></a>
当收到未知 `type` / 不支持版本：
- 按约定记录已消费（避免反复重试占用消费能力）
- 不产生业务副作用

---

### Requirement: R2-producer-after-commit
<a id="r2-producer-after-commit"></a>
**Module:** content-service / social-service / common  
**Goal:** 当业务写入在事务中发生时，Kafka 事件只允许在事务 commit 后发送，避免“回滚却发出事件”的幽灵事件。

#### Scenario: R2S1-db-rollback-no-event
<a id="r2s1-db-rollback-no-event"></a>
当写库事务回滚（校验失败/异常）：
- Kafka 不应收到该事件

#### Scenario: R2S2-db-commit-then-send
<a id="r2s2-db-commit-then-send"></a>
当写库事务提交成功：
- Kafka 事件在 commit 后发送
- 若发送失败，P0 仅要求：记录错误日志 + 指标可观测（P1 再用 Outbox 保障最终投递）

---

### Requirement: R3-sync-call-resilience
<a id="r3-sync-call-resilience"></a>
**Module:** user-service  
**Goal:** user-service 同步调用 social-service 具备确定性的超时、降级语义与可观测性，避免级联故障。

#### Scenario: R3S1-social-down-fast-degrade
<a id="r3s1-social-down-fast-degrade"></a>
当 social-service 不可用或网络异常：
- user-service 用户主页接口仍能返回（使用默认值/缓存值）
- 响应时间受控（不被挂死）
- 指标可观测（降级次数、错误次数、耗时）

---

### Requirement: R4-dlq-observability-and-replay
<a id="r4-dlq-observability-and-replay"></a>
**Module:** message-service / search-service / deploy / scripts  
**Goal:** DLQ 进入、积压、回放具备“可观测 + 可操作 + 有边界”的运营闭环。

#### Scenario: R4S1-dlq-publish-alert
<a id="r4s1-dlq-publish-alert"></a>
当消费失败触发 DLQ：
- 应有可抓取指标（Prometheus）
- 告警规则能够在短时间内触发并定位到 topic/服务

#### Scenario: R4S2-dlq-replay-runbook
<a id="r4s2-dlq-replay-runbook"></a>
当需要回放 DLQ：
- 有脚本支持限速/限量/过滤的回放
- 有 Runbook 说明操作步骤、风险与回滚（避免误回放/无限回放）

---

### Requirement: R5-mysql-multi-schema-split
<a id="r5-mysql-multi-schema-split"></a>
**Module:** deploy / content-service / message-service / search-service  
**Goal:** MySQL 同实例多 schema 拆分（先非身份域），配套最小权限与备份/恢复，使“可回滚”可执行。

#### Scenario: R5S1-compose-boot-with-schemas
<a id="r5s1-compose-boot-with-schemas"></a>
全新环境（清空数据卷）启动 compose：
- 自动创建 `community_content` / `community_message` / `community_search`
- 三个服务能正确连到各自 schema 并正常启动

#### Scenario: R5S2-backup-restore-multi-db
<a id="r5s2-backup-restore-multi-db"></a>
备份/恢复演练：
- 支持一次性备份/恢复多个 schema
- 支持单 schema 回滚（最小化影响面）

## Risk Assessment

- **风险 1：After-Commit 只能消除幽灵事件，不能保证 100% 投递成功（网络抖动仍可能丢事件）。**  
  - **缓解：** P0 增加“发送失败指标 + 告警 + 人工补偿流程”；P1 引入 Outbox 保障最终投递。
- **风险 2：MySQL 多 schema 迁移可能造成运行期数据不一致或回滚复杂。**  
  - **缓解：** 先做演练（全新卷 + 备份恢复），上线前做完整备份，旧 schema 保留只读一段时间。
- **风险 3：同步调用增加超时后可能暴露更多下游问题（短期内错误更多但更快可见）。**  
  - **缓解：** 通过降级兜底 + 指标观察逐步调参（超时阈值、重试策略）。
