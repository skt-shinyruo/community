# Technical Design: 生产可用 P0 加固（事件一致性/幂等/DLQ/同步调用韧性/MySQL 多 Schema）

## Technical Solution

### Core Technologies
- Spring Boot 3.2.x + Spring Cloud 2023.x + Spring Cloud Alibaba Nacos
- Spring Kafka（手动 ack，失败重试 + DLQ）
- Spring Transaction（DB 事务 + After-Commit 回调）
- MySQL 8（同实例多 schema，最小权限）
- Prometheus / Alertmanager（告警）+ Grafana（观测）
- Docker Compose（单机可恢复部署）

### Implementation Key Points

#### R1-message-service：消费端幂等 + 事务 + ack 正确性

1. **拆分 listener 与业务处理**  
   Listener（`@KafkaListener`）只负责：解析、调用处理器、成功后 ack。  
   业务处理放到独立 `@Service` 中，使用 `@Transactional`，避免“同类内部调用绕过事务代理”的风险。

2. **幂等策略：insert-first（以唯一约束为准）**  
   现有 `consumed_event(event_id unique)` 已具备幂等基础。建议将流程从“count → insert”改为：
   - 先尝试插入 `consumed_event(event_id)`（作为幂等锁）
   - 若插入失败（duplicate key），直接返回（已经消费过）
   - 插入成功后再执行 notice 写入
   - 所有 DB 写入在同一事务里提交

3. **异常策略与 ack 策略**  
   - 业务异常/DB 异常：抛出异常，交给 Spring Kafka error handler 重试；重试耗尽进入 DLQ；不 ack。
   - 不支持的事件类型/版本：视为“可跳过”，但仍应记录幂等（避免无限重试占用能力）；可正常 ack。

#### R2-生产端 After-Commit：消除幽灵事件

1. **新增通用 After-Commit 工具（common）**  
   在 `common` 增加一个小型工具：当检测到事务活跃时，注册 `afterCommit()` 回调执行发送；无事务时直接执行发送。

2. **Kafka 发送策略（P0）**  
   - DB 事务中：事务 commit 后发送 Kafka
   - 发送失败：P0 不强制保证最终投递（Outbox 在 P1），但必须做到：
     - 记录结构化日志（包含 eventId/type/producer/traceId/topic）
     - 暴露指标（send_fail_total），用于告警与人工补偿

3. **把“写库与发布”收敛到同一事务域**  
   - 对于发帖路径：当前 controller 直接写库并发布事件；为保证 after-commit 生效，需要把写库/发布放到 `@Transactional` 的 service 方法中。
   - 对于评论路径：已处于 `@Transactional`，更新 publisher 后即可变为 after-commit 发送。

#### R3-user-service 同步调用韧性：超时 + 降级 + 指标

1. **强制超时**  
   在 `RestTemplate` Bean 构造时设置 connect/read timeout，并通过配置项可调（默认值先偏保守，例如 200ms/500ms）。

2. **降级语义**  
   保留 `safe*` 方法的默认值兜底（0/false），并在降级发生时记录指标与日志（避免“静默失败不可见”）。

3. **可观测性**  
   使用 Micrometer `Timer/Counter`：
   - `user_social_client_requests_total{api=..., outcome=success|error|degraded}`
   - `user_social_client_latency_seconds{api=...}`

#### R4-DLQ：监控告警 + 回放流程

1. **DLQ 发布指标**  
   在 `KafkaErrorHandlerConfig` 的 recoverer 中，为每次 DLQ publish 增加 Counter（带 originalTopic / errorType 标签）。

2. **Prometheus 告警规则**  
   在 `deploy/observability/alerts.yml` 新增：
   - 5 分钟内 DLQ publish 增量 > 0（warning/critical 按阈值分级）

3. **回放脚本与 Runbook**  
   - 新增 `scripts/kafka-replay-dlq.sh`：支持从 `<topic>.dlq` 拉取并回推原 topic（需限量/限速/过滤）。
   - 更新文档：明确回放前置条件、风险、回滚手段（避免误操作扩大影响）。

#### R5-MySQL 同实例多 schema 拆分（先非身份域）

1. **schema 规划**
- `community`：身份域暂留（auth/user），保持现状（P1 再做身份域解耦）
- `community_content`：`discuss_post`、`comment`
- `community_message`：`message`、`consumed_event`
- `community_search`：`search_consumed_event`

2. **最小权限**
- 每个服务使用独立 DB 用户（仅授权自己的 schema）
- root 仅用于初始化与运维脚本（不进入业务容器配置）

3. **Compose 改造**
- `deploy/mysql-init/`：拆分建库/建表脚本（可清卷重建）
- `deploy/docker-compose.yml`：为 content/message/search 配置各自 DB URL/用户名/密码
- `scripts/mysql-backup.sh` / `scripts/mysql-restore.sh`：扩展为多 schema 备份与恢复

## Architecture Decision ADR

### ADR-008: P0 选择 After-Commit 而非 Outbox（先止血）
**Context:** 当前最大风险是“事务回滚但事件已发出”的幽灵事件，以及消费端事务不可靠导致的丢通知。  
**Decision:** P0 先引入 After-Commit 机制，确保事务 commit 后再发送事件；并补齐发送失败可观测。  
**Rationale:** After-Commit 改造范围小、能快速消除最危险的不一致；Outbox 需要新增表、后台发布器与运维流程，适合在 P1 完整落地。  
**Alternatives:**  
- Outbox（更可靠）→ Rejection reason: P0 周期内改动面大且需要额外运营能力支撑。  
**Impact:**  
- 正向：消除幽灵事件；改造快速、风险可控。  
- 负向：仍可能出现“commit 成功但 send 失败导致下游缺数据”的一致性缺口，需要 P1 Outbox 补齐。

## API Design

P0 不新增外部业务 API。内部运维能力以脚本 + Runbook 形式提供（DLQ 回放、MySQL 多 schema 备份/恢复）。

## Data Model

P0 不新增业务字段；主要变更为“表归属 schema 调整”与“多 schema 备份/恢复能力补齐”。

## Security and Performance

- **Security：**
  - DB 最小权限：每服务独立账号仅授权自身 schema
  - 业务容器不配置 root 密码（仅 mysql-init/运维脚本使用）
  - DLQ 回放脚本需明确操作边界（限量/限速/白名单 topic）

- **Performance：**
  - 同步调用设置超时，避免线程长期阻塞导致雪崩
  - 关键降级路径通过指标观测，避免隐性性能退化

## Testing and Deployment

- **Testing：**
  - message-service：补充重复 eventId 不重复通知测试；补充失败重试/事务一致性相关测试
  - content-service：回归发帖/评论写路径，确保事务回滚不产生事件
  - user-service：回归 user 主页接口，验证 social-service 不可用时降级与耗时

- **Deployment（Docker Compose 单机）：**
  1. 先在演练环境清空数据卷启动 compose 验证多 schema 初始化
  2. 使用备份/恢复脚本完成一次完整演练
  3. 生产上线前全量备份；按 schema 逐步迁移；保留旧 schema 只读一段时间
