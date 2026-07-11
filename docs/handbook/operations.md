# 运行与排障

本文档覆盖本地 observability、Kibana、XXL-Job、outbox worker、scheduler 和常见故障检查。本地启动命令见 [local-development.md](local-development.md)，压测套件见 [performance-testing.md](performance-testing.md)，可靠性机制见 [reliability.md](reliability.md)。

观测模型、SLO/SLI、信号契约、指标维度、trace 命名和告警优先级的 SSOT 是 [observability.md](observability.md)。运行态 hook 的代码接入点见 [Runtime Observability](core-logic/runtime-observability.md)。本文只维护运行和排障入口。

## Observability

本地观测路径通过共享 overlay 提供：

```text
deploy/compose.observability.yml
```

启动：

```bash
./deploy/deployment.sh up --topology single
./deploy/deployment.sh up --topology cluster
```

observability 默认启用；如需关闭整个 overlay，追加 `--no-observability`。

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
- 普通启动默认加载 observability overlay，并由 `deployment.sh` 设置 `OTEL_ENABLED=true`，后端服务会加载 OTel Java agent。
- 如需关闭整个 overlay，使用 `./deploy/deployment.sh up --topology single --no-observability`。
- 如需保留 observability overlay 但临时关闭 tracing，使用 `OTEL_ENABLED=false ./deploy/deployment.sh up --topology single`。

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
- `event.category`：区分 auth、content、search、outbox、scheduler、im、runtime、database、access 等类别。
- `event.action`：定位具体动作，例如 pollOnce、persistPrivateMessage。
- `event.outcome`：区分 success、failed、skipped、retry、dead。

链路排障时：

- `trace.id` 用于技术链路串联。
- 结构化日志的 MDC 只有 `trace.id` / `span.id`；不要按旧 MDC key `traceId` 检索。
- HTTP `Result.traceId`、outbox 运维结果和事件字段中的 `traceId` 是业务/传输字段，可用于找到对应的 `trace.id`，但不是第二个 MDC key。
- `requestId`、事件 id、幂等 key 用于业务重放和消息确认，不作为 trace parent。
- 对 outbox 或 job 发起的链路，如果没有上游请求，系统会生成 job/outbox 处理 trace。

对外 HTTP 响应会回写 `traceparent`，前端或 curl 拿到 trace 后优先在 Kibana 里按 trace 查。

### 运行态日志

主要后端 deployable 默认启用业务无关运行态日志，包括 `community-app`、`community-oss`、`im-core`、`im-realtime`、`community-gateway` 和 `community-im-gateway`。日志以共享 Logback JSON stdout 为 operator 可读入口，同时由 EDOT Collector 的 logs pipeline 接收 stdout 和 OTLP logs，归一化后写入 Elasticsearch。运行态日志只记录启动摘要、阈值事件和慢请求事件，不记录请求 body、cookie、Authorization、SQL bind、Redis key、Kafka payload 或完整 object key。

当前覆盖：

- JVM 启动摘要：`event.category: runtime AND event.action: jvm_startup`
- 应用生命周期：`app_startup`、`app_ready`、`app_shutdown`、`graceful_shutdown_timeout`
- JVM 内存压力：`event.category: runtime AND event.action: jvm_memory_pressure`
- GC pause 阈值：`event.category: runtime AND event.action: jvm_gc_pause_threshold`
- JVM 扩展摘要：`jvm_direct_memory_pressure`、`jvm_class_loading_summary`
- executor 压力：`event.category: runtime AND event.action: executor_pressure`
- Hikari 连接池等待：`event.category: database AND event.action: hikari_pool_pressure`
- MyBatis 慢 SQL：`event.category: database AND event.action: sql_slow_query`
- Redis 技术事件：`redis_connection_pressure`、`redis_command_slow`
- Kafka 技术事件：`kafka_producer_error`、`kafka_consumer_lag_threshold`、`kafka_rebalance`
- 慢 HTTP 请求：`event.category: access AND event.action: http_slow_request`
- 出站 HTTP 客户端：`http_client_slow`、`http_client_error`
- OSS 客户端：`oss_upload_slow`、`oss_download_slow`、`oss_client_error`
- 日志系统：`logging_appender_error`、`logging_queue_pressure`
- 调度任务：`scheduled_job_slow`、`scheduled_job_skipped`、`scheduled_job_error`
- 缓存/安全/限流：`cache_hit_ratio_low`、`rate_limit_triggered`、`auth_filter_error`
- 进程/系统资源：`process_fd_pressure`、`disk_space_pressure`、`cpu_load_threshold`

常用字段：

- lifecycle：`spring.profiles.active`、`server.port`、`duration.ms`
- JVM：`jvm.version`、`jvm.heap.max.bytes`、`jvm.memory.area`、`jvm.memory.used.percent`、`jvm.gc.pause.ms`、`jvm.classes.loaded.delta`
- executor：`executor.name`、`executor.active`、`executor.pool.size`、`executor.queue.size`
- database：`db.pool.name`、`db.pool.active`、`db.pool.idle`、`db.pool.pending`、`db.mybatis.statement`、`db.operation`、`db.rows.bucket`
- Redis/cache：`cache.system`、`cache.operation`、`cache.pool.active`、`cache.pool.idle`、`cache.pool.pending`、`cache.hit.ratio.percent`
- Kafka：`messaging.destination.name`、`messaging.kafka.consumer.group`、`messaging.kafka.partition`、`messaging.kafka.consumer.lag`
- HTTP：`peer.service`、`http.request.method`、`url.path`、`http.response.status_code`、`duration.ms`、`threshold.ms`
- OSS：`oss.bucket`、`oss.operation`、`object.size.bucket`、`error.code`
- process：`process.fd.used.percent`、`disk.used.percent`、`process.cpu.load.percent`

自动触发入口包括 Spring lifecycle events、周期性 JVM/进程快照、GC notification、Servlet access filter、MyBatis interceptor、Spring `RestClient.Builder` / `WebClient.Builder` customizer、Kafka producer listener / rebalance listener / record interceptor、`community-app` 的 OSS client wrapper，以及 `community-oss` 的 `ObjectStore` wrapper。Redis、缓存命中率、任务调度、限流和 auth filter 也有专用 logger API；接入具体业务入口时必须继续保持字段克制，不记录 key、token、请求体或业务结果。

可通过环境变量调整：

```text
COMMUNITY_OBSERVABILITY_RUNTIME_LOGGING_ENABLED=false
COMMUNITY_OBSERVABILITY_RUNTIME_PERIODIC_SUMMARY_INTERVAL=60s
COMMUNITY_OBSERVABILITY_RUNTIME_JVM_MEMORY_THRESHOLD_PERCENT=85
COMMUNITY_OBSERVABILITY_RUNTIME_JVM_GC_PAUSE_THRESHOLD_MS=200
COMMUNITY_OBSERVABILITY_RUNTIME_JVM_DIRECT_MEMORY_THRESHOLD_PERCENT=85
COMMUNITY_OBSERVABILITY_RUNTIME_EXECUTORS_SATURATION_THRESHOLD_PERCENT=85
COMMUNITY_OBSERVABILITY_RUNTIME_DATASOURCE_POOL_PENDING_THRESHOLD=1
COMMUNITY_OBSERVABILITY_RUNTIME_SQL_SLOW_QUERY_THRESHOLD_MS=500
COMMUNITY_OBSERVABILITY_RUNTIME_REDIS_POOL_PENDING_THRESHOLD=1
COMMUNITY_OBSERVABILITY_RUNTIME_REDIS_SLOW_COMMAND_THRESHOLD_MS=100
COMMUNITY_OBSERVABILITY_RUNTIME_KAFKA_CONSUMER_LAG_THRESHOLD=1000
COMMUNITY_OBSERVABILITY_RUNTIME_OSS_SLOW_OPERATION_THRESHOLD_MS=1000
COMMUNITY_OBSERVABILITY_RUNTIME_HTTP_CLIENT_SLOW_REQUEST_THRESHOLD_MS=1000
COMMUNITY_OBSERVABILITY_RUNTIME_JOBS_SLOW_JOB_THRESHOLD_MS=30000
COMMUNITY_OBSERVABILITY_RUNTIME_CACHE_HIT_RATIO_THRESHOLD_PERCENT=80
COMMUNITY_OBSERVABILITY_RUNTIME_LOGGING_SYSTEM_QUEUE_PRESSURE_THRESHOLD_PERCENT=80
COMMUNITY_OBSERVABILITY_RUNTIME_SYSTEM_FD_USAGE_THRESHOLD_PERCENT=80
COMMUNITY_OBSERVABILITY_RUNTIME_SYSTEM_DISK_USAGE_THRESHOLD_PERCENT=90
COMMUNITY_OBSERVABILITY_RUNTIME_SYSTEM_CPU_LOAD_THRESHOLD_PERCENT=85
COMMUNITY_OBSERVABILITY_RUNTIME_HTTP_SLOW_REQUEST_THRESHOLD_MS=1000
```

### Runtime Diagnostics Agent

`runtime-diagnostics-agent` is an optional JVM diagnostic agent for short troubleshooting sessions. It is disabled by default and is enabled per deployment with `RUNTIME_DIAGNOSTICS_ENABLED=true`.

Safe Phase 1 probes:

- `method`: method latency summaries and slow-call events.
- `exception`: exception type events from instrumented methods without raw messages or stack traces.
- `thread`: thread state snapshots, deadlock count, and lock-wait count.
- `jvm`: runtime, heap, non-heap, GC, class loading, and thread count summaries.

Useful Kibana filters:

```text
event.category : runtime_diagnostics
event.action : method_latency_summary
event.action : exception_observed
event.action : thread_snapshot
event.action : jvm_runtime_summary
trace.id : "<trace id>"
```

Dependency probe filters:

```text
event.category : runtime_diagnostics
event.action : jdbc_call_summary
event.action : redis_call_summary
event.action : kafka_produce_summary
event.action : http_call_summary
diagnostic.probe : jdbc
trace.id : "<trace id>"
```

Tune dependency thresholds with `RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS`, `RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS`, `RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS`, and `RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS`. Matching sample and rate-limit settings use the corresponding `RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE`, `RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND`, `RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND`, `RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND`, and `RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND` variables. Kafka topic names stay hashed unless `RUNTIME_DIAGNOSTICS_KAFKA_TOPIC_NAMES_ENABLED=true` is set explicitly.

Keep includes narrow during diagnostic runs:

```text
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
```

The agent reads existing OTel/MDC trace context when present and does not create a new trace root. It must not be used to collect payload data or secrets.

## Stability Observability Runbooks

Use these runbooks in Kibana with the local observability overlay. Prefer structured fields over message text.

### JVM Or Memory Pressure

Query:

```text
service.namespace : "community" and event.category : "runtime" and
event.action : ("jvm_memory_pressure" or "jvm_direct_memory_pressure" or "jvm_gc_pause_threshold")
```

Inspect: `service.name`, `jvm.memory.area`, `jvm.memory.used.percent`, `jvm.gc.name`, `jvm.gc.pause.ms`, `threshold.percent`, `threshold.ms`.

Interpretation: repeated memory pressure or GC pause threshold events mean the service is under runtime pressure, even if request traces only show downstream latency.

Next action: check `process.cpu.load.percent`, recent deploy version, traffic shape, and whether one service produces most pressure events. Enable `runtime-diagnostics-agent` `thread,jvm` probes only if the standard runtime logs do not show enough detail.

### Thread Pool Or Scheduler Saturation

Query:

```text
service.namespace : "community" and
((event.category : "runtime" and event.action : "executor_pressure") or
 (event.category : "job" and event.action : ("scheduled_job_slow" or "scheduled_job_skipped" or "scheduled_job_error")))
```

Inspect: `executor.name`, `executor.active`, `executor.pool.size`, `executor.queue.size`, `duration.ms`, `threshold.ms`, `job.name`.

Interpretation: executor pressure means request, Kafka, or scheduled work may be queued before it appears slow in traces.

Next action: identify the executor or job name, then pivot by `service.name` and time range to request traces and downstream dependency events.

### Database Pool Pressure

Query:

```text
service.namespace : "community" and event.category : "database" and
event.action : ("hikari_pool_pressure" or "sql_slow_query")
```

Inspect: for `hikari_pool_pressure`, check `db.pool.name`, `db.pool.active`, `db.pool.idle`, `db.pool.pending`. For `sql_slow_query`, check `db.mybatis.statement`, `db.operation`, `duration.ms`, `threshold.ms`.

Interpretation: Hikari pending count indicates pool wait. Slow SQL events show statement identity without bind values.

Next action: compare with traces for the same time window. Enable diagnostics `jdbc` only for a short run if traces and SQL slow events are not enough.

### Redis Instability Or Slow Operations

Query:

```text
service.namespace : "community" and event.category : "cache" and
event.action : ("redis_connection_pressure" or "redis_command_slow" or "cache_hit_ratio_low")
```

Inspect: `cache.system`, `cache.operation`, `cache.pool.active`, `cache.pool.pending`, `cache.hit.ratio.percent`, `duration.ms`, `threshold.ms`.

Interpretation: Redis slow operations or pool pressure can cause application latency before database metrics change.

Next action: check whether the issue is isolated to one service and compare with request traces. Enable diagnostics `redis` only for a short run; raw keys and values must remain absent.

### Content Hot Path Degradation

Query:

```text
community_cache_requests_total{cache="hot_feed",result=~"degraded|singleflight_busy"}
```

Inspect: `result`, `scope`, Redis runtime events, Hikari pending, and hot-path k6 p95/p99. Do not inspect raw Redis keys or payload values.

Interpretation: `singleflight_busy` during a Redis flush or cold start means one node is allowed to rebuild a hot feed page while peers avoid a repository stampede. `degraded` means the cache path failed or rank version fell back.

Next action: confirm `HotPathPrewarmJob` is enabled with `content.hot-path.prewarm.enabled=true`, wait one scheduled interval, then run:

```bash
cd tests/k6
K6_BOARD_ID=<board-uuid> K6_POST_ID=<post-uuid> npm run hot-path
```

If Hikari pending rises during warm-cache runs, reduce k6 arrival rate or prewarm page/board limits before changing repository queries.

### Kafka Lag Or Rebalance

Query:

```text
service.namespace : "community" and event.category : "messaging" and
event.action : ("kafka_consumer_lag_threshold" or "kafka_rebalance" or "kafka_producer_error")
```

Inspect: `messaging.destination.name`, `messaging.kafka.consumer.group`, `messaging.kafka.partition`, `messaging.kafka.consumer.lag`, `error.type`.

Interpretation: lag and rebalance events explain delayed projections, IM fanout, and outbox delivery even when HTTP traces look healthy.

Next action: check outbox state and consumer services. Enable diagnostics `kafka` only for short producer/consumer investigation; payloads must remain absent.

### Slow HTTP Requests

Query:

```text
service.namespace : "community" and event.category : "access" and
event.action : "http_slow_request"
```

Inspect: `trace.id`, `service.name`, `http.request.method`, `url.path`, `http.response.status_code`, `duration.ms`, `threshold.ms`.

Interpretation: use `trace.id` to pivot into traces and request-correlated logs.

Next action: if traces identify a dependency, inspect the matching database, cache, messaging, or HTTP client event category for the same time window. Use `event.category : "http_client"` for outbound HTTP client events such as `http_client_slow` and `http_client_error`.

### When To Enable Runtime Diagnostics

Enable diagnostics only after always-on traces and runtime logs do not explain the symptom. Keep includes narrow:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true \
RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' \
RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm' \
./deploy/deployment.sh up --topology single
```

Query:

```text
event.category : runtime_diagnostics and diagnostic.probe : *
```

Inspect: `diagnostic.probe`, `event.action`, `service.name`, `trace.id`, `duration.ms`, `threshold.ms`.

Interpretation: runtime diagnostics is for focused deep dives; it should explain one unresolved symptom, not replace always-on traces or runtime logs.

Next action: disable diagnostics after the capture window. Use dependency probes only for focused short sessions:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true \
RUNTIME_DIAGNOSTICS_PROBES='method,exception,thread,jvm,http,jdbc,redis,kafka' \
./deploy/deployment.sh up --topology single
```

### Production Compatibility

Phase 1 intentionally keeps Elastic/Kibana as the local UI and does not add Prometheus, Grafana, Loki, or Alertmanager. Production alerting can later use the same signal split: traces for timelines, metrics for trends and SLOs, runtime logs for discrete stability events, and runtime diagnostics for short deep dives.

Query:

```text
service.namespace : "community" and event.category : ("access" or "runtime" or "database" or "cache" or "messaging" or "http_client")
```

Inspect: `service.name`, `event.category`, `event.action`, `event.outcome`, `duration.ms`, `threshold.ms`, `trace.id`.

Interpretation: production compatibility depends on stable fields and signal categories, not the local Kibana UI.

Next action: candidate SLOs are HTTP availability/latency, Kafka lag, database pool pending, Redis error/slow-operation rate, JVM memory/GC pressure, executor saturation, and outbox backlog or dead-letter rate.

## Content Platform Degradation

Runtime toggles for the high-traffic content platform:

- `CONTENT_FEED_LATEST_FALLBACK_ENABLED=true` keeps global and board feeds available when hot ranking lags.
- `SEARCH_PROJECTION_ENABLED=false` stops search projection writes without blocking owner writes.
- `NOTICE_PROJECTION_ENABLED=false` pauses in-app projection while content and social writes continue.
- `ANALYTICS_INGEST_ASYNC_ENABLED=true` keeps request latency off the analytics path and allows independent throttling.

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

XXL-JOB Admin 本地入口：

```text
http://localhost:12887/xxl-job-admin
```

## Startup Fail-closed

启动期校验分两层：

1. prod profile 下的 `StartupValidation` 聚合各模块 `StartupValidator`。
2. bean 创建期 fail-closed，例如安全基础设施和 outbox 自动装配。

典型校验：

- JWT HMAC secret 为空、过短或为已知占位值会阻断 prod 启动。
- trusted proxy 开启但 CIDR 为空或全信任会阻断 prod 启动。
- refresh cookie 在 prod 下必须满足安全属性。
- 找回密码和注册邮件在 prod 下必须可用，禁止泄漏 reset link / registration code。
- 固定验证码禁止出现在 prod。
- Prometheus basic auth 如果启用但凭据缺失，会在 bean 创建期失败。
- outbox 开启时必须能拿到 JDBC store，否则启动失败。

这些规则的设计目标是：关键能力一旦声明启用，就不能 silently degrade 到危险默认值。

## 常见本地故障

### Gateway 502

```bash
./deploy/deployment.sh ps --topology cluster
./deploy/deployment.sh logs --topology cluster community-gateway-1
./deploy/deployment.sh logs --topology cluster community-app-1
./deploy/deployment.sh logs --topology cluster im-realtime-1
```

同时检查 Nacos 是否有目标服务实例。

### IM WebSocket worker 不可用

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

如果 worker 列表为空，查看 `im-realtime-*` 启动日志和 Nacos 注册 metadata。

### Nacos Config Verification

List a seeded config:

```bash
curl -fsS "http://localhost:18848/nacos/v1/cs/configs?dataId=community-gateway.yaml&group=COMMUNITY"
```

List IM worker registration metadata:

```bash
curl -fsS "http://localhost:18848/nacos/v1/ns/instance/list?serviceName=im-realtime-worker"
```

If a required config import is missing in production-like mode, the service must
fail startup before serving traffic. Check `NACOS_CONFIG_IMPORT_SHARED`,
`NACOS_CONFIG_IMPORT_SERVICE`, `NACOS_NAMESPACE`, and `NACOS_CONFIG_GROUP`.

### Kafka health 长时间 starting

```bash
./deploy/deployment.sh logs --topology cluster kafka-1
```

如果是旧拓扑残留数据，执行：

```bash
./deploy/deployment.sh down --topology cluster -- -v
./deploy/deployment.sh up --topology cluster
```
`-v` 要放在 `--` 后面，才会被透传给 `docker compose down`。默认 cluster volume namespace 是 `community_cluster`，所以 MySQL 数据卷名是 `community_cluster_mysql_primary_data`。

### Kibana 没有日志

检查：

- 启动命令是否没有带 `--no-observability`。
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
