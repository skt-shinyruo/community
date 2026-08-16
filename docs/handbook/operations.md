# 运行与排障

本文档覆盖本地 observability、Kibana、XXL-Job、outbox worker、scheduler 和常见故障检查。本地启动命令见 [local-development.md](local-development.md)，压测套件见 [performance-testing.md](performance-testing.md)，可靠性机制见 [reliability.md](reliability.md)。

观测模型、SLO/SLI、信号契约、指标维度、trace 命名和告警优先级的 SSOT 是 [observability.md](observability.md)。本文只维护运行和排障入口。

## Observability

本地观测路径通过共享 overlay 提供：

```text
deploy/compose/overlays/observability.yml
```

启动：

```bash
./deploy/deployment.sh up --stack single --observability
./deploy/deployment.sh up --stack cluster
```

single 默认关闭 observability，需用 `--observability` 开启；cluster 默认开启，可用 `--no-observability` 关闭。infra 不支持完整 overlay。

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

先确认用户路径和 SLI，再查询日志或 trace。核心链路和 SLI 目录见 [observability.md#slosli-catalog](observability.md#slosli-catalog)。

日志数据流：

```text
backend structured logs (JSON stdout / OTLP logs)
  -> EDOT collector logs pipeline
  -> Elasticsearch
  -> Kibana
```

traces / metrics：

- 继续通过 OTLP -> EDOT collector -> Elastic。
- 启用 observability overlay 时，`deployment.sh` 默认设置 `OTEL_ENABLED=true`，后端服务会加载 OTel Java agent。
- single 使用 `./deploy/deployment.sh up --stack single --observability`；cluster 普通启动即启用。
- 如需保留 observability overlay 但临时关闭 tracing，使用 `OTEL_ENABLED=false ./deploy/deployment.sh up --stack single --observability`。

Kibana saved objects：

```text
deploy/observability/kibana/saved-objects.ndjson
deploy/observability/kibana/README.md
```

当前不再维护 Grafana / Loki / Prometheus / Alertmanager overlay。

## 日志检索口径

排障优先使用结构化字段，而不是纯文本 grep：

- `trace.id` / `traceparent`：串联一次请求或异步链路。
- `service.name`：定位 `community-app`、`community-gateway`、`community-im-gateway`、`community-oss`、`im-core`、`im-realtime`。
- `event.category`：只用于已有的 `security`、`business`、`async`、`access`、`exception` 和 `yierloom` 语义日志。
- `event.action`：定位具体动作，例如 pollOnce、persistPrivateMessage。
- `event.outcome`：区分 success、failed、skipped、retry、dead。

链路排障时：

- `trace.id` 用于技术链路串联。
- 结构化日志的 MDC 只有 `trace.id` / `span.id`；不要按旧 MDC key `traceId` 检索。
- HTTP `Result.traceId`、outbox 运维结果和事件字段中的 `traceId` 是业务/传输字段，可用于找到对应的 `trace.id`，但不是第二个 MDC key。
- `requestId`、事件 id、幂等 key 用于业务重放和消息确认，不作为 trace parent。
- 对 outbox 或 job 发起的链路，如果没有上游请求，系统会生成 job/outbox 处理 trace。

对外 HTTP 响应会回写 `traceparent`，前端或 curl 拿到 trace 后优先在 Kibana 里按 trace 查。


### YierLoom Agent

YierLoom is an optional JVM Agent for short troubleshooting sessions. It is disabled by default. Enable it per deployment with `YIERLOOM_ENABLED=true`; the built-in `method`, `exception`, `thread`, and `jvm` plugins then start by default.

Built-in core plugins:

- `method`: method latency summaries and slow-call events.
- `exception`: exception type events from instrumented methods without raw messages or stack traces.
- `thread`: thread state snapshots, deadlock count, and lock-wait count.
- `jvm`: runtime, heap, non-heap, GC, class loading, and thread count summaries.

Useful Kibana filters:

```text
event.category : yierloom
diagnostic.plugin.id : method
event.action : method_latency_summary
event.action : exception_observed
event.action : thread_snapshot
event.action : jvm_runtime_summary
trace.id : "<trace id>"
```

Dependency plugin filters:

```text
event.category : yierloom
event.action : jdbc_call_summary
event.action : redis_call_summary
event.action : kafka_produce_summary
event.action : http_call_summary
diagnostic.plugin.id : jdbc
trace.id : "<trace id>"
```

Dependency plugins are opt-in: use `YIERLOOM_PLUGIN__HTTP__ENABLED=true`, `YIERLOOM_PLUGIN__JDBC__ENABLED=true`, `YIERLOOM_PLUGIN__REDIS__ENABLED=true`, or `YIERLOOM_PLUGIN__KAFKA__ENABLED=true` only for the dependency under investigation. Plugin duration settings accept values such as `2s`; for example, set `YIERLOOM_PLUGIN__HTTP__SLOW_THRESHOLD=2s`. Sample and rate-limit settings use the same plugin-scoped form, such as `YIERLOOM_PLUGIN__HTTP__SAMPLE_RATE` and `YIERLOOM_PLUGIN__HTTP__MAX_EVENTS_PER_SECOND`. Kafka topic names stay hashed unless `YIERLOOM_PLUGIN__KAFKA__TOPIC_NAMES_ENABLED=true` is set explicitly.

Keep method includes narrow during captures and keep the event queue bounded:

```text
YIERLOOM_PLUGIN__METHOD__INCLUDES=com.nowcoder.community.*
YIERLOOM_EVENTS_QUEUE_CAPACITY=8192
```

The queue is non-blocking for instrumented application work: when it is full, YierLoom drops new observations or events. The Agent reads existing OTel/MDC trace context when present and does not create a new trace root. It must not collect method arguments, return values, request or response bodies, SQL bind values, Redis keys or values, Kafka payloads, credentials, cookies, or headers.

#### Trusted External Plugin Installation

Treat every external plugin as trusted code. Build one fat JAR per plugin with exactly one `YierLoomPlugin` ServiceLoader provider and the plugin's private dependencies. Do not bundle YierLoom API, YierLoom SDK, or Byte Buddy classes.

Verify the finished JAR through the `yierloom-plugin-testkit` Java API `PluginContractVerifier.verifyOrThrow(Path)` before it reaches a runtime image or volume:

```java
PluginContractVerifier.verifyOrThrow(Path.of("/path/to/plugin.jar"));
```

`PluginContractVerifier` has no CLI. Mount or copy a verified JAR into `/opt/yierloom/plugins`; when another directory is required, configure it with `YIERLOOM_PLUGINS_DIR`. Restart the target JVM after adding, replacing, or removing a JAR. Hot reload and runtime attach are not supported.

## Stability Observability Runbooks

Use metrics and traces for runtime and dependency health. The application no longer emits a parallel set of custom runtime events.

### Runtime And Dependency Signals

| 问题 | 首选信号 |
| --- | --- |
| JVM 内存、GC | `jvm_memory_*`、`jvm_gc_pause_*` |
| 线程池、调度 | `executor_*` 和 job trace |
| 数据库连接池 | `hikaricp_connections_*` |
| HTTP 延迟/错误 | `http_server_requests_*` 和 server spans |
| Redis、Kafka、OSS | 对应客户端指标和 OTel dependency spans |

具体 meter 名称以目标服务当前 `/actuator/prometheus` 输出为准，避免把实现细节写成日志事件。

### Semantic Logs

只查询仍由代码生成的语义日志：

```text
service.namespace : "community" and
(event.category : (access or async or security or business or exception) or message : "[audit]*")
```

用 `trace.id` 关联请求和依赖 spans；不要再查询 `jvm_memory_pressure`、`sql_slow_query`、`redis_command_slow`、`kafka_rebalance` 或 `http_slow_request`。

### Content Hot Path Degradation

```text
community_cache_requests_total{cache="hot_feed",result=~"degraded|singleflight_busy"}
```

确认 `HotPathPrewarmJob` 使用 `content.hot-path.prewarm.enabled=true`，再按需运行：

```bash
cd tests/k6
K6_BOARD_ID=<board-uuid> K6_POST_ID=<post-uuid> npm run hot-path
```

### When To Enable YierLoom

Enable YierLoom only after metrics, traces, and audit logs do not explain the symptom. Keep includes narrow:

```bash
YIERLOOM_ENABLED=true \
YIERLOOM_PLUGIN__METHOD__INCLUDES='com.nowcoder.community.*' \
./deploy/deployment.sh up --stack single
```

Query:

```text
event.category : yierloom and diagnostic.plugin.id : *
```

Disable it after the capture window and restart the target services:

```bash
YIERLOOM_ENABLED=false ./deploy/deployment.sh up --stack single
```

Phase 1 keeps Elastic/Kibana as the local UI. Production alerting should use traces for timelines, metrics for trends and SLOs, semantic logs for audit/security context, and YierLoom only for short deep dives.

## Content Platform Degradation

Runtime toggles for the high-traffic content platform:

- `CONTENT_FEED_LATEST_FALLBACK_ENABLED=true` keeps global and board feeds available when hot ranking lags.
- `SEARCH_PROJECTION_ENABLED=false` stops search projection writes without blocking owner writes.
- `NOTICE_PROJECTION_ENABLED=false` pauses in-app projection while content and social writes continue.
- Analytics request capture always publishes to Kafka, keeping Redis writes off the request path and allowing independent consumer throttling.

Dual-region failover order:

1. Freeze old primary writes.
2. Confirm replay boundary for Kafka/outbox consumers.
3. Promote the new primary.
4. Switch Kafka producers and consumers.
5. Warm feed, comment, detail, search, and notice caches.
6. Reopen writes.

## IM 压测

IM 的正确性设计是 “WebSocket best-effort 推送 + HTTP 断线补拉”。压测流量推荐统一通过 gateway：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：使用 session response `wsUrl`，稳定为 `ws://localhost:12880/ws/im`
- HTTP：`http://localhost:12880/api/im/**`

推荐压测分层：

1. 长连容量：连接数、内存、CPU、GC、连接稳定性，必须先走 `POST /api/im/sessions` 获取 ticket，再连接返回的 `wsUrl`。
2. 私信写入：`im-core` 落库吞吐与延迟、Kafka backplane、`im-realtime` 推送延迟。
3. 慢连接 / 回压：验证慢消费者不会拖垮整体。
4. 断线补拉：验证断线后通过 `im-core` history API 补齐。

## Outbox Worker

Outbox worker 是共享可靠投递底座，当前主要承担：

- search post projection。
- IM policy projection：user punishment / social block -> IM Kafka policy topic。

运行入口：

- `OutboxWorkerScheduler`
- `OutboxWorker`
- `JdbcOutboxEventStore`
- topic-specific `OutboxHandler`

状态：

- `PENDING`
- `PROCESSING`
- `SUCCEEDED`
- `DEAD`

排障顺序：

1. 查看应用是否启用 `events.outbox.enabled=true`。
2. 查 `community.outbox_event` 中 `PENDING` / `PROCESSING` / `DEAD` 数量。
3. 查 worker 日志中 `pollOnce`、`tryClaimProcessing`、`recoverExpiredLeases`、handler exception。
4. `PROCESSING` 长时间不动时，确认 lease TTL 和恢复任务是否运行。
5. `DEAD` 事件需要人工确认业务副作用是否已落地，再决定重放、修数据或忽略。

content media reference command 是当前自动恢复特例：reconciler 以相同确定性 event ID 重新调度时，publisher 可把对应 row 原位从 `DEAD` 重排为 `PENDING`。只有该条件更新成功才记录 `scheduled`；其他 topic 的 `DEAD` 仍按下方治理 API 和人工 triage 处理。

完整语义见 [reliability.md](reliability.md)。

## Outbox DEAD Triage

1. 用 `GET /api/ops/outbox/backlog` 查看 backlog。
2. 用 `GET /api/ops/outbox/events?status=DEAD&topic=<topic>&limit=50` 列出终态行。
3. 检查 `eventId`、`topic`、`eventKey`、`lastError`、`traceId`、`createdAt` 和 `updatedAt`。
4. 先修复依赖或 handler 问题，再执行 replay。
5. 用 `POST /api/ops/outbox/events/{outboxId}/replay` 并提供非空 `reason` 重新排队单条事件。
6. 确认该行进入 `SUCCEEDED`，或者回到 `DEAD` 并带有新的 `lastError`。

批量 replay 只用于已确认同一 topic、同一时间窗口的 `DEAD` 行可以交回 worker 处理的场景：

1. 先用 `GET /api/ops/outbox/events?status=DEAD&topic=<topic>&createdFrom=<from>&createdTo=<to>&limit=<n>` 抽样确认 `lastError` 和 handler 修复状态。
2. 执行 `POST /api/ops/outbox/replay-batch`，body 必须包含 `topic`、`status="DEAD"`、`createdFrom`、`createdTo`、`limit` 和非空 `reason`。
3. 检查响应里的逐行 `result`。`REPLAYED` 表示已回到 `PENDING`；`REJECTED` 和 `NOT_REQUEUED` 需要按行继续人工判断。
4. 观察 `community_outbox_batch_replay_total{topic,result}` 和 `community_governance_action_total{action="OUTBOX_REPLAY_BATCH",result}`。
5. 再次查看 backlog，确认 `PENDING` 被 worker 消化，失败行有新的 `lastError`。

Projection lag 可通过 `GET /api/ops/projections/lag` 查看当前 outbox-backed projection topics，本次收敛后主要是 `projection.im.policy`。Search 需要查 `content.events` consumer lag / `.dlq` 和 ES alias，必要时用 content owner 当前事实 reindex；它不再是 projection outbox topic。hot-feed 读路径降级继续通过 `community_cache_requests_total{cache="hot_feed",result,scope}` 观察。

## Compensation Trigger Runbook

管理员触发入口：

```text
POST /api/ops/compensations/{jobName}/trigger
```

请求体必须包含 `limit` 和非空 `reason`。允许列表：

- `outboxRecoverExpiredLeases`
- `searchPostProjectionRepair`
- `hotFeedProjectionRepair`
- `growthTaskProjectionRepair`
- `noticeProjectionRepair`

操作步骤：

1. 先定位症状：outbox lease 卡住、projection lag、缓存热榜异常或 notice/growth 投影缺口。
2. 选择允许列表里的最小 job，并把 `limit` 控制在本次排障需要的范围内。
3. 触发后检查响应里的 `accepted`、`processedCount`、`repairedCount`、`skippedCount`、`result` 和 `message`。
4. `outboxRecoverExpiredLeases` 会尝试回收过期 `PROCESSING` lease；其余 projection repair 必须依赖 owner action API。未接入 owner repair action 的作业会返回 `SKIPPED`，不要在 ops 侧绕过 owner 层直接修数据。
5. 检查 `community_compensation_trigger_total{job.name,result}` 和治理审计。

## Hot-Cache Governance Runbook

入口：

- `GET /api/ops/hot-cache/status?scope=global|board&boardId=<uuid?>`
- `POST /api/ops/hot-cache/prewarm`
- `GET /api/ops/hot-cache/degradation`
- `POST /api/ops/hot-cache/degradation`

操作步骤：

1. 热榜异常时先查 status。`scope=board` 必须带 `boardId`。
2. 如果缓存为空或 rank version 落后，执行 prewarm，body 包含 `scope`、可选 `boardId`、`limit` 和非空 `reason`。
3. 如果 Redis 或 summary cache 明显不稳定，可以设置降级信号；恢复后用同一入口清除。
4. 观察 `community_hot_cache_governance_total{operation,result,scope}`、`community_governance_action_total{action,result}` 和读路径的 `community_cache_requests_total{cache="hot_feed",result,scope}`。
5. 预热和降级都只改变运行态缓存/信号，不改变帖子、评论、点赞、分数等业务事实。

## Scheduler 和 XXL-Job

后台任务分两类：

- 本地 `@Scheduled`：应用内持续型任务，例如 outbox worker、hot-path 预热和 counter snapshot flush。
- XXL-Job：控制面触发的离散任务，例如 `marketOrderAutoConfirm`、`marketWalletActionProcessor`、`marketWalletActionRecovery`。

约束：

- job / scheduler 不拼业务规则。
- 入口必须回到 owner `ApplicationService` 或 owner action API。
- 需要集群单实例执行的任务使用 single-flight 或 owner 内部锁。
- 清理/补偿任务必须尽量幂等。

Market scheduler jobs：

- `marketOrderAutoConfirm`：扫描到期订单，由 market owner 判断是否可自动确认，只写 release command。
- `marketWalletActionProcessor`：批量 claim due `market_wallet_action`，调用 wallet owner API，并推进 market saga 状态。
- `marketWalletActionRecovery`：恢复过期 processing lease，补齐缺失 action，并把已有 `wallet_txn_id` 重新应用到订单 / 争议状态。
- 这些 job 都可以重跑；重复执行依赖 `market_wallet_action.request_id`、`wallet_txn.request_id` 和订单条件更新保证幂等。

默认控制面由 `deploy/database/xxl-job/020_seed_local.sh` 幂等维护：

| Handler | 调度 | 默认状态 | 路由 / 阻塞策略 |
| --- | --- | --- | --- |
| `searchReindex` | 手动 | 停止 | `FIRST` / `SERIAL_EXECUTION` |
| `marketWalletActionProcessor` | 每 5 秒 | 启用 | `FIRST` / `SERIAL_EXECUTION` |
| `marketWalletActionRecovery` | 每分钟第 15 秒 | 启用 | `FIRST` / `SERIAL_EXECUTION` |
| `marketOrderAutoConfirm` | 每分钟第 30 秒 | 启用 | `FIRST` / `SERIAL_EXECUTION` |

市场资金动作使用自身的 request id、处理 lease 和恢复任务控制重试，因此 XXL 层不额外重试，错过调度时使用 `DO_NOTHING`，避免控制面重放与业务层重试叠加。新增或删除 `@XxlJob` handler 时必须同步 seed；运行 `./deploy/tests/contracts/database/xxl_job_seed_contract.sh` 检查源码与部署控制面是否一致。

XXL-JOB Admin 本地入口：

```text
http://localhost:12887/xxl-job-admin
```

## Community 前向 Schema 迁移

空库仍由 `deploy/database/business/current-state/010_current_schema.sql` 一次性建立最终结构。已有 `community` 数据由 `community-db-migrations` one-shot 执行 `deploy/database/business/migrations/VNNN__*.sql`；它使用独立 `${COMMUNITY_MIGRATION_USERNAME}`，runtime 账号始终只有 DML 权限。当前序列从 `V016` 开始，能接管之前没有 history 的快照环境，也能在已经包含目标结构的新空库上幂等登记。

### 发布步骤

1. 备份 `community`，确认可恢复；涉及数据清理或与旧写路径互斥时，先停止 `community-app` 和 Mock Data Studio 写入。V016 执行前必须停止并排空所有旧 refresh rotation writer，禁止旧二进制跨迁移恢复并改写带 lease 的 pending session。V022 必须在旧版收藏 writer 全部停止并排空后执行，直到全部实例切换到事务内 durable marker 版本前不得恢复收藏写入。
2. 为本次发布准备独立强口令 `COMMUNITY_MIGRATION_PASSWORD`，确认迁移用户名与 `MYSQL_USER` 不同。不要把这两个迁移变量注入 runtime service。
3. 修改当前态快照最终定义，同时追加新的、不可变的 `VNNN__description.sql`；同步 H2 fixture 和 schema / migration 契约。
4. 执行 `./deploy/deployment.sh up --stack <single|cluster>`。账号 bootstrap 先收敛权限；cluster 再建立 GTID 复制；随后 one-shot 执行迁移。`community-app` 只会在迁移退出码为 0 后启动。
5. 检查 `community-db-migrations` 日志和 `community_forward_schema_history` 的 version、script、SHA-256、installed_by。cluster 还要确认 replica 已追上迁移 GTID，再恢复业务写入。

迁移只向前，不提供 down migration。发布失败时保持 runtime 停止，修复尚未成功登记的迁移，使其仍可从任一部分完成状态重跑；不要手工插入 history，不要修改已登记文件，也不要把 DDL 权限临时授给 application 账号。需要回退应用镜像时，必须先确认旧版本与已前向升级的 schema 兼容。

`reset-mysql` 仍是可丢弃环境的破坏性 clean break，并永久删除目标 Stack 的 MySQL 数据。保留数据的环境不得使用。

### Development Seed

`community-dev-seed` 使用 DML-only community 账号执行 `deploy/database/business/seed/090_seed_identity.sql`。只有 `COMMUNITY_DEV_SEED_ENABLED=true` 且 `DEPLOYMENT_ENVIRONMENT=development` 时才运行；生产环境误开开关会失败关闭。该 SQL 不属于当前态 schema，不会污染 production 初始化。

### 故障定位

- 新快照没有执行：确认目标 MySQL volume 是否确实为空；普通 restart 不会重放 `/docker-entrypoint-initdb.d`。
- runtime 未启动：依次检查 `community-db-user-bootstrap`、cluster 的 `mysql-replication-bootstrap` 和 `community-db-migrations`。
- checksum mismatch：已登记 migration 被修改；恢复发布时的原文件并新增更高版本修正，禁止改 history checksum。
- migration 中断：确认没有 runtime 写入后直接重跑同一 one-shot；V016 及后续迁移的 DDL 和数据清理逐项幂等，history 只记录完整成功。
- cluster replica 缺表或缺引用数据：保持 runtime 停止，检查 primary 初始化日志、GTID 状态和 replication bootstrap 日志。
- development seed 失败：确认部署环境精确为 `development`，开关为 `true`，且当前态快照已经创建 `user` 等目标表。
- 结构漂移：可丢弃环境修正快照后 reset；保留数据环境新增前向迁移，不要手工补 DDL。

契约验证：

```bash
./deploy/tests/run-contracts.sh database compose
```

## Startup Fail-closed

启动期校验分两层：

1. prod profile 下的 `StartupValidation` 聚合各模块 `StartupValidator`。
2. bean 创建期 fail-closed，例如安全基础设施和 outbox 自动装配。

典型校验：

- access verifier 缺少至少 2048-bit RSA public key 会阻断启动；`community-app` 还要求匹配的 private key。service JWT HMAC secret 为空、过短或为已知占位值也会阻断启动。
- trusted proxy 开启但 CIDR 为空或全信任会阻断 prod 启动。
- refresh cookie 在 prod 下必须满足安全属性。
- 找回密码和注册邮件在 prod 下必须可用，禁止泄漏 reset link / registration code；SMTP 必须使用隐式 SSL，或同时启用并强制 STARTTLS。密码重置、注册请求和验证码 quota 不允许用非正数关闭。
- Redis connect / command timeout 必须为正数且不超过 5 秒；command timeout 还必须小于登录密码检查 lease 的四分之一，避免一次阻塞命令耗尽续租间隔。
- OriginGuard 必须启用、fail-closed，并配置非空且格式合法的 Origin allowlist。
- 固定验证码禁止出现在 prod。
- Prometheus basic auth 如果启用但凭据缺失，会在 bean 创建期失败。
- outbox 开启时必须能拿到 JDBC store；prod / production 还必须启用 outbox worker，否则启动失败。

这些规则的设计目标是：关键能力一旦声明启用，就不能 silently degrade 到危险默认值。

profile 名按大小写无关方式识别：`prod`、`PROD`、`production` 和大小写混合形式都会启用同一套启动校验。即使误把 profile 留在 `dev`，只要 `DEPLOYMENT_ENVIRONMENT` 或 Nacos discovery deployment metadata 是 `prod` / `production`，同一套校验也会 fail-closed，不能靠 profile 错配绕过生产守卫。

### 认证邮件与 Cookie 配置所有权

`nacos-config-bootstrap` 在发布 `community-app.yaml` 时渲染 `AUTH_REFRESH_COOKIE_SECURE`、`AUTH_REFRESH_COOKIE_SAME_SITE`、`AUTH_MAIL_ENABLED`、`AUTH_MAIL_FROM` 和 `AUTH_REGISTRATION_EXPOSE_CODE`。`SameSite` 只接受 `Lax` / `Strict` / `None`，且 `None` 必须配合 `Secure=true`。这些值属于 Nacos 配置，不重复注入 `community-app` runtime。

SMTP endpoint、username/password、auth、STARTTLS/SSL 与三个 timeout 直接注入 `community-app` 容器；密码不写入 Nacos 发布内容。生产外部 SMTP 的典型配置是 `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`、设置 `SPRING_MAIL_USERNAME` / `SPRING_MAIL_PASSWORD`，并同时设置 STARTTLS enable/required，或启用隐式 SSL。MailHog、`.local` host / from 和 TLS 降级配置会被生产启动校验拒绝。三个 timeout 必须在 1..30000 ms，注册验证码 operation lease 必须覆盖三者总和并额外保留 10 秒。

`SPRING_DATA_REDIS_CONNECT_TIMEOUT` 和 `SPRING_DATA_REDIS_TIMEOUT` 分别控制 Redis 建连和单条命令等待时间，部署模板默认都是 `2s`。生产环境中两者都必须大于 0 且不超过 `5s`，并保证 command timeout 小于 `auth.login-rate-limit.password-check-lease-seconds / 4`；缩短密码检查 lease 时必须同步复核该约束。

`AUTH_PASSWORD_RESET_IDENTIFIER_HMAC_SECRET` 使用双向兼容轮换。假设当前密钥为 K1、新密钥为 K2，顺序必须是：

1. 全部 K1 节点先保持 `current=K1`，并把 K2 放入 `AUTH_PASSWORD_RESET_PREVIOUS_IDENTIFIER_HMAC_SECRETS`。该属性名是历史命名，实际语义是 worker 可接受的辅助密钥列表。
2. 确认所有节点都接受 K1/K2 后，再滚动为 `current=K2`、辅助列表包含 K1。此时新旧节点都能处理两个 key ID，不会因滚动发布而饿死邮件。
3. 只有确认 K1 已无携带 payload 的 `PENDING` / `PROCESSING` / 可重放 `DEAD` 邮件，且用 K1 签发的 reset token TTL 已结束后，才能从辅助列表移除 K1。`SUCCEEDED` 和不可恢复的 terminal `DEAD` 会原子清空 payload，不再要求保留对应密钥。

`AUTH_PASSWORD_RESET_QUOTA_HMAC_SECRET` 是密码重置和注册滥用防护的稳定伪名密钥，必须与 delivery identifier secret、service JWT secret 分离。正常发布不得轮换它；强制轮换会创建一套新 Redis quota key，相当于重置窗口。确需轮换时，应先暂停相关公开入口，至少等待所有 request/resend quota 的最长窗口到期，再一次性切换全部实例。

登录风控的 IP `v2`、临时输入 `input:v3` 和 authoritative subject `subject:v3` key 复用该稳定 quota 密钥，但分别使用 `login-ip`、`login-input` 和 `login-subject` HMAC scope。`user:v2` 不会被 v3 双读或迁移：从 `auth:login:fail:user:v2-*` 切换到 `input:v3` / `subject:v3`，以及强制轮换 quota 密钥时，都禁止新旧 writer 混合接收登录流量，否则两套独立预算可在同一窗口内被合并利用。

切换步骤是：暂停公开登录并排空在途请求，停止全部旧版 `community-app`，确认已无 `user:v2` writer 后，等待至少 `max(auth.login-rate-limit.window-seconds, auth.login-rate-limit.password-check-lease-seconds) + 1` 秒，使旧失败计数和 lease 全部过期；再一次性启动全部 v3 实例并恢复登录。回滚同样必须先停流、停止 v3 writer 并等待相同 drain 时间，不能直接滚动混跑。

启用严格用户名策略前必须审计存量 `user.username`。新版本会对请求值和查询返回值分别校验；不安全旧行会被当成不存在账号，因此未治理的用户将无法重新登录。下面的 MySQL 8 查询保守列出控制/format 字符、owner 策略显式拒绝的不可见码点以及纯 Unicode separator 用户名；结果只显示 ID 和 UTF-8 hex，避免终端把不可见字符伪装成普通用户名：

```sql
WITH RECURSIVE username_codepoints AS (
  SELECT id, username, 1 AS position,
         SUBSTRING(username, 1, 1) AS character_value
  FROM user
  WHERE CHAR_LENGTH(username) > 0
  UNION ALL
  SELECT id, username, position + 1,
         SUBSTRING(username, position + 1, 1)
  FROM username_codepoints
  WHERE position < CHAR_LENGTH(username)
), flagged_users AS (
  SELECT id, username
  FROM username_codepoints
  WHERE REGEXP_LIKE(character_value, '[\\p{Cc}\\p{Cf}]')
     OR HEX(character_value) = 'CD8F'
     OR HEX(character_value) BETWEEN 'E1859F' AND 'E185A0'
     OR HEX(character_value) BETWEEN 'E19EB4' AND 'E19EB5'
     OR HEX(character_value) BETWEEN 'E1A08B' AND 'E1A08F'
     OR HEX(character_value) = 'E385A4'
     OR HEX(character_value) BETWEEN 'EFB880' AND 'EFB88F'
     OR HEX(character_value) = 'EFBEA0'
     OR HEX(character_value) BETWEEN 'F09BB2A0' AND 'F09BB2A3'
     OR HEX(character_value) BETWEEN 'F09D85B3' AND 'F09D85BA'
     OR HEX(character_value) BETWEEN 'F3A08080' AND 'F3A0BFBF'
  UNION
  SELECT id, username
  FROM user
  WHERE username = ''
     OR REGEXP_LIKE(username, '^[\\p{Zs}\\p{Zl}\\p{Zp}]+$')
)
SELECT HEX(id) AS user_id_hex, HEX(username) AS username_utf8_hex
FROM flagged_users
ORDER BY user_id_hex;
```

结果非空时保持登录/注册停流，逐条检查安全目标名在 `utf8mb4_unicode_ci` 下没有冲突，再通过受审的 user owner 管理流程重命名；无法确认归属的账号先禁用并撤销 refresh family，走人工身份恢复。禁止直接删除不可见码点后批量更新，因为多个旧名可能折叠到同一个唯一键。审计结果为空并完成 v3 drain 后，才恢复登录流量。

真实 SMTP 密码应由部署平台 Secret 注入。`docker compose config` 会展开普通环境变量，不能把其输出当作可公开日志；若只能使用 Compose env file，还需按 Compose 规则处理密码中的 `$`。

## 注册验证码 Redis v2 切换

`auth:regcode:v2:{<userId>}` 把注册码、delivery ID、失败次数和 UUID lease 存为 Hash。旧版真实 key 是 `auth:regcode:<userId>`，值为没有 lease 的 8 字段 String。两个 key 不在同一 Redis Cluster slot，因此桥接先在 legacy key 上用单个 Lua 原子执行 `GET + PTTL + DEL`，再执行 v2 单 key 条件导入，从不对跨 slot key 执行一个 Lua。

这个 bridge 只保证停机切换后的存量验证码可继续使用，不支持新旧 writer 滚动混跑。旧实例会忽略 v2，新实例也无法约束旧脚本，因此混跑可能出现两个同时可验证的 code。

发布步骤：

1. 暂停注册、验证码验证和重发入口，等待所有在途请求结束。
2. 停止全部旧版 `community-app`，确认没有旧实例、listener 或任务再写 `auth:regcode:<userId>`。
3. 启动 v2 实例后恢复入口。无需提前扫描或改写 Redis；每个用户的首个操作会在“旧 writer 已停止”的发布约束下安全桥接有效 legacy 值。legacy pending 只恢复此前 active code，replacement 不会被猜测为已投递。
4. 观察注册签发、重发和验证错误率。非法、过期、无 TTL 的 legacy 值会 fail-closed 清理；显式注册 cleanup 会同时删除 v2 和 legacy key。

不得通过重新启动旧实例直接回滚。回滚时先再次关闭入口并停止全部 v2 writer，使仍在途的 registration draft/code 失效，再启动旧版并要求用户重新发起注册；否则旧版会忽略 v2 状态并破坏 lease fencing。

## Captcha Redis key 切换

验证码使用 `captcha:{<32位十六进制 captchaId>}:value` 与同 slot 的 `:fail` key，并在一个 Lua 中原子校验、累计失败和消费。旧版 `captcha:<captchaId>` 与新 key 不兼容，也没有双读协议；新旧实例混跑会互相拒绝对方签发的验证码。

发布该 key 协议时，先暂停 captcha 签发以及依赖 captcha 的登录、注册、重发和密码重置入口，等待至少一个 `auth.captcha.ttl-seconds` 并排空在途请求，再停止全部旧实例、启动全部新实例后恢复入口。回滚遵循相同停流和 TTL 排空步骤，禁止直接滚动混跑。

## 常见本地故障

### Gateway 502

```bash
./deploy/deployment.sh ps --stack cluster
./deploy/deployment.sh logs --stack cluster community-gateway-1
./deploy/deployment.sh logs --stack cluster community-app-1
./deploy/deployment.sh logs --stack cluster im-realtime-1
```

同时检查 Nacos 是否有目标服务实例。

### IM WebSocket worker 不可用

```bash
curl -fsS "http://localhost:38848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

如果 worker 列表为空，查看 `im-realtime-*` 启动日志和 Nacos 注册 metadata。

### Nacos Config Verification

List a seeded config:

```bash
curl -fsS "http://localhost:38848/nacos/v1/cs/configs?dataId=community-gateway.yaml&group=COMMUNITY"
```

List IM worker registration metadata:

```bash
curl -fsS "http://localhost:38848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

If a required config import is missing in production-like mode, the service must
fail startup before serving traffic. Check `NACOS_CONFIG_IMPORT_SHARED`,
`NACOS_CONFIG_IMPORT_SERVICE`, `NACOS_NAMESPACE`, and `NACOS_CONFIG_GROUP`.

### Kafka health 长时间 starting

```bash
./deploy/deployment.sh logs --stack cluster kafka-1
```

如果是旧拓扑残留数据，执行：

```bash
./deploy/deployment.sh down --stack cluster -- -v
./deploy/deployment.sh up --stack cluster
```
`-v` 要放在 `--` 后面，才会被透传给 `docker compose down`。默认 cluster volume namespace 是 `community_cluster`，所以 MySQL 数据卷名是 `community_cluster_mysql_primary_data`。

### Kibana 没有日志

检查：

- single 启动命令是否带了 `--observability`，或 cluster 是否没有带 `--no-observability`。
- backend 是否在 `docker compose logs <service>` 中输出 JSON stdout（包含 `service.name`、`trace.id` 等字段）。
- EDOT collector 是否正常运行，并挂载了 `/var/lib/docker/containers`。
- Kibana saved objects 是否已导入。
- 日志查询时间范围是否覆盖当前时间。

### 搜索索引缺失或旧数据

检查：

- `events.outbox.enabled=true`。
- `content.events` 的 search consumer lag 和 `content.events.dlq`。
- `SearchPostProjectionKafkaListener` / `SearchPostProjectionApplicationService` 是否报错。
- ES alias `community_posts_alias` 指向哪个真实索引。

确认 content 当前事实和投影消费问题已恢复、`search.projection-enabled=true` 后，可在 XXL-JOB Admin 手动执行停用状态的 `searchReindex`。任务使用 Redis single-flight、content owner 游标扫描和隔离版本索引；成功后才切换 alias，失败不会覆盖当前可查询索引。观察日志中的 `executionId`、`indexedCount` 或 `already running`；执行 lease 和重建目标都由心跳续租，`search.reindex.lock-ttl` 最小为 3 秒。`search.index.keep-history` 控制 active index 之外保留的历史索引数。

### 市场订单资金状态卡住

检查：

- `market_order.status` 是否处于 `ESCROW_PENDING`、`ESCROW_CANCEL_PENDING`、`RELEASE_PENDING`、`REFUND_PENDING`、`DISPUTE_RELEASE_PENDING` 或 `DISPUTE_REFUND_PENDING`。
- `market_wallet_action` 是否存在对应 `order_id + action_type`。
- action 是否长时间停在 `PENDING` / `RETRYING`；若是，检查 `marketWalletActionProcessor` XXL job 和应用日志。
- action 是否长时间停在 `PROCESSING`；若是，检查 `processing_lease_until` 是否过期，并运行或排查 `marketWalletActionRecovery`。
- action 是否已有 `wallet_txn_id` 但状态不是 `SUCCEEDED`；恢复 job 应尝试继续推进 market saga 状态。
- action 为 `FAILED` 时，根据 `failure_code` / `last_error` 判断是业务失败、钱包余额/状态问题，还是需要人工修数据后重试。

## 常用验证命令

文档或代码改动后按影响面选择：

```bash
git diff --check -- docs/handbook
cd backend && mvn test
cd backend && mvn -q -DskipTests -pl :community-app -am package
cd frontend && npm test
cd frontend && npm run build
```

全栈联调仍优先走 [local-development.md](local-development.md) 的 `deployment.sh`。只改 handbook 时，至少运行 `git diff --check -- docs/handbook`。
