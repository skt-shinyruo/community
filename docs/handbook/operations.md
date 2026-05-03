# 运行与排障

本文档覆盖本地 observability、Kibana、IM 压测、XXL-Job、search reindex、outbox worker、scheduler 和常见故障检查。本地启动命令见 [local-development.md](local-development.md)，可靠性机制见 [reliability.md](reliability.md)。

## Observability

本地观测路径通过共享 overlay 提供：

```text
deploy/compose.observability.yml
```

启动：

```bash
./deploy/deployment.sh up --topology single --observability
./deploy/deployment.sh up --topology cluster --observability
```

默认端口：

- Elasticsearch：`http://localhost:12888`
- Kibana：`http://localhost:12889`

日志数据流：

```text
backend structured JSON file appender
  -> shared observability_logs volume
  -> EDOT collector filelog receiver
  -> Elasticsearch
  -> Kibana
```

traces / metrics：

- 继续通过 OTLP -> EDOT collector -> Elastic。
- 普通启动默认 `OTEL_ENABLED=false`。
- 使用 `--observability` 时，`deployment.sh` 默认设置 `OTEL_ENABLED=true`，后端服务会加载 OTel Java agent。
- 如需在 observability overlay 下临时关闭 tracing，使用 `OTEL_ENABLED=false ./deploy/deployment.sh up --topology single --observability`。

Kibana saved objects：

```text
deploy/observability/kibana/saved-objects.ndjson
deploy/observability/kibana/README.md
```

当前不再维护 Grafana / Loki / Prometheus / Alertmanager overlay。

## 日志检索口径

排障优先使用结构化字段，而不是纯文本 grep：

- `trace.id` / `traceparent`：串联一次请求或异步链路。
- `service.name`：定位 `community-app`、`community-gateway`、`im-core`、`im-realtime`。
- `community.category`：区分 auth、content、search、outbox、scheduler、im 等类别。
- `community.action`：定位具体动作，例如 reindex、pollOnce、persistPrivateMessage。
- `community.outcome`：区分 success、failed、skipped、retry、dead。

链路排障时：

- `trace.id` / `trace_id` 用于技术链路串联。
- `requestId`、事件 id、幂等 key 用于业务重放和消息确认，不作为 trace parent。
- 对 outbox 或 job 发起的链路，如果没有上游请求，系统会生成 job/outbox 处理 trace。

对外 HTTP 响应会回写 `X-Trace-Id` / `traceparent`，前端或 curl 拿到 trace 后优先在 Kibana 里按 trace 查。

## IM 压测

IM 的正确性设计是 “WebSocket best-effort 推送 + HTTP 断线补拉”。压测流量推荐统一通过 gateway：

- Session bootstrap：`POST http://localhost:12880/api/im/sessions`
- WebSocket：使用 session response `wsUrl`，稳定为 `ws://localhost:12880/ws/im`
- HTTP：`http://localhost:12880/api/im/**`

工具：

```text
tools/im-load/
```

模式：

- `connect-only`：只测长连与鉴权，适合容量压测。
- `private`：私信链路压测，覆盖 WS -> Kafka -> 落库 -> Kafka -> WS，并包含断线重连和 backfill。

推荐压测分层：

1. 长连容量：连接数、内存、CPU、GC、连接稳定性，使用 `connect-only`。
2. 私信写入：`im-core` 落库吞吐与延迟、Kafka backplane、`im-realtime` 推送延迟，使用 `private`。
3. 慢连接 / 回压：验证慢消费者不会拖垮整体，使用 `--slowConsumerPct`。
4. 断线补拉：验证断线后通过 `im-core` history API 补齐，使用 `--reconnectEverySec`。

注意：`tools/im-load` 当前仍是旧 `auth` 首帧协议的压测 harness，直接连接传入的 `--wsUrl`，不能代表当前浏览器客户端的 `/api/im/sessions` ticket bootstrap 行为。使用前先看 `tools/im-load/README.md` 的兼容性说明；如需压测当前生产语义，应先升级工具或另写 session-bootstrap 压测脚本。

## Search Reindex

搜索重建索引可通过两条入口触发：

- HTTP：`POST /api/ops/search/reindex`，ADMIN-only。
- XXL-Job：`searchReindex`。

执行模型：

```text
OpsController / XXL handler
  -> ops / search action API
  -> SearchReindexApplicationService
  -> SingleFlightTaskGuard
  -> PostIndexManager
  -> scan content owner
  -> bulk write new ES index
  -> atomic alias switch
```

关键点：

- 使用 Redis-backed single-flight，避免集群内多个 reindex 并发。
- 长任务启动后台心跳续期，避免 lock TTL 过期。
- ES alias `community_posts_alias` 是业务读写入口。
- 真实索引是 `community_posts_vYYYYMMDDHHmmss[_n]`。
- 默认保留最近 2 个历史索引，便于人工回滚 alias。
- 重建失败不影响旧 alias 继续服务。

常见排查：

- reindex 一直无法开始：查 `sf:task:search:reindex` single-flight lock。
- ES 写入失败：查 search application 日志和 ES 健康。
- 搜索结果缺失：先查 outbox worker 是否在消费 `projection.search.post`，再视情况触发 reindex。

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

## Scheduler 和 XXL-Job

后台任务分两类：

- 本地 `@Scheduled`：应用内持续型任务，例如 outbox worker、帖子热度刷新。
- XXL-Job：控制面触发的离散任务，例如 `pendingRegistrationUserCleanup`、`searchReindex`、`marketOrderAutoConfirm`、`marketWalletActionProcessor`、`marketWalletActionRecovery`。

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

### Kafka health 长时间 starting

```bash
./deploy/deployment.sh logs --topology cluster kafka-1
```

如果是旧拓扑残留数据，执行：

```bash
./deploy/deployment.sh down --topology cluster -v
./deploy/deployment.sh up --topology cluster
```

### Kibana 没有日志

检查：

- 启动命令是否带 `--observability`。
- backend 是否写入 shared `observability_logs` volume。
- EDOT collector 是否正常运行。
- Kibana saved objects 是否已导入。
- 日志查询时间范围是否覆盖当前时间。

### 搜索索引缺失或旧数据

检查：

- `events.outbox.enabled=true`。
- `community.outbox_event` 是否有 `projection.search.post`。
- `PostOutboxHandler` 是否报错。
- ES alias `community_posts_alias` 指向哪个真实索引。
- 必要时触发 `POST /api/ops/search/reindex`。

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
