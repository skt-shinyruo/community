# 观测模型

本文档是 SLO/SLI、信号契约、指标维度、trace 命名、告警优先级和观测治理的 SSOT。运行排障步骤继续维护在 [operations.md](operations.md)，本地启动和端口继续维护在 [local-development.md](local-development.md)。

## 目标模型

本项目的观测系统按下面的闭环维护：

```text
业务目标
  -> SLI / SLO
  -> metrics / logs / traces 契约
  -> dashboard / alert
  -> runbook
  -> smoke 或治理测试
```

默认本地路径仍是：

```text
Backend JSON stdout logs
OTLP traces
OTLP metrics
  -> EDOT Collector
  -> Elasticsearch
  -> Kibana
```

生产演进必须保持应用代码只面向 OpenTelemetry、Micrometer、SLF4J 和项目公共观测模块，不直接绑定某个存储后端。

## SLO/SLI Catalog

| Flow | Owner Area | Primary SLIs | Investigation Signals |
| --- | --- | --- | --- |
| Login / refresh token / logout | auth | success rate, P95/P99 latency, authentication error rate, refresh failure rate | HTTP trace, security events, audit logs, Redis and database dependency timing |
| Publish post / comment | content | write success rate, P95/P99 latency, database write failure rate, outbox publication delay | HTTP trace, application result metric, SQL slow/failure events, outbox trace fields |
| Refresh post score / search projection | content/search | job success rate, projection delay, terminal failure count | job events, outbox events, Elasticsearch dependency events |
| WebSocket connect / IM send / fanout | im | connect success rate, send success rate, server processing latency, Kafka lag, fanout failure rate | gateway trace, Kafka publish/consume trace context, messaging events, IM Micrometer gauges |
| OSS upload / download | oss/drive | operation success rate, P95/P99 latency, storage dependency failure rate | HTTP trace, OSS slow/failure events, sanitized endpoint fields |
| Search query / index refresh | search | query success rate, query latency, index delay, Elasticsearch failure rate | HTTP trace, search application metric, Elasticsearch client failure event |
| Market exchange / wallet transaction | market/wallet | operation success rate, idempotent rejection rate, consistency compensation count, async backlog | application metric, outbox/job event, database dependency event, trace across async work |
| Growth task / level update | growth | operation success rate, async backlog, terminal failure count | application metric, job event, outbox event, database dependency event |

Every P0 or P1 alert candidate must map to one of these flows or to a newly documented flow in this table.

## Shared Resource Fields

All backend logs, metrics, and traces should use these resource fields when available:

```text
service.name
service.version
service.namespace=community
deployment.environment
service.instance.id
service.zone
release.track
```

`service.instance.id`, `service.zone`, and `release.track` may be absent in local development, but production-compatible configuration should define them.

## Runtime Event Contract

Runtime events should keep this common shape:

```text
@timestamp
service.name
service.version
service.namespace
deployment.environment
event.category
event.action
event.outcome
trace.id
span.id
duration.ms
threshold.ms
threshold.percent
error.type
error.code
error.message
```

`trace.id` and `span.id` are required when an event belongs to a request, message, outbox item, or job with active OpenTelemetry context. Process-level events may omit trace fields.

Stable `event.category` values:

```text
runtime
database
cache
messaging
access
http_client
job
security
logging
runtime_diagnostics
business
```

`business` is reserved for low-volume application-owned semantic events. It must not replace domain events or cross-domain contracts.

## Metrics Contract

New metrics should use explicit units and bounded dimensions. Approved metric families for new work:

```text
community_http_server_request_duration_seconds
community_http_server_requests_total
community_dependency_request_duration_seconds
community_dependency_requests_total
community_application_usecase_duration_seconds
community_application_usecase_total
community_outbox_backlog
community_outbox_publish_duration_seconds
community_job_duration_seconds
community_job_runs_total
community_kafka_consumer_lag
community_executor_queue_size
community_executor_rejections_total
```

Allowed dimensions:

```text
service.name
deployment.environment
http.method
http.route
http.status_class
dependency.system
dependency.operation
usecase
result
error.code
job.name
event.type
pool.name
consumer.group
```

Forbidden dimensions:

```text
userId
user.id
orderId
objectKey
redisKey
topic.raw
url.full
url.query
client.ip
exception.stacktrace
trace.id
span.id
uuid
timestamp
```

`trace.id` belongs in logs and traces, not in metric labels.

## Trace Contract

Manual spans should be added only at meaningful boundaries:

```text
http <route>
application <domain>.<usecase>
kafka publish <eventType>
kafka consume <eventType>
outbox publish <eventType>
job <jobName>
dependency <system>.<operation>
```

Do not attach payloads, object keys, SQL bind values, Redis keys, JWTs, cookies, authorization headers, request bodies, response bodies, or raw user content to spans.

HTTP behavior:

- Inbound HTTP joins a valid W3C `traceparent` or starts an OTel server span.
- Responses include active `traceparent`.
- JSON `Result.traceId` matches active OTel trace id.

Async behavior:

- Outbox rows store active trace id and `traceparent`.
- Kafka producers inject W3C trace context into headers.
- Kafka consumers extract trace context and create consumer spans.
- Scheduled jobs create a root span when no parent trace exists.
- Outbox workers continue stored trace context when publishing or handling outbox events.

## Instrumentation Boundaries

Instrumentation must follow the repository DDD layering rules:

- Controllers, listeners, handlers, bridges, enqueuers, and jobs may bind or extract trace context and call same-domain `*ApplicationService`.
- Application services own use-case result classification and may record business metrics or low-volume business observability events.
- Domain models and domain services must not depend on observability framework types.
- Infrastructure adapters own dependency timing, slow/failure events, and implementation-specific metrics.
- Shared helpers belong in `community-common-observability` or `community-common/common-core` trace utilities.

## Alert Priority

| Priority | Meaning | Page |
| --- | --- | --- |
| P0 | Core user path is broadly unavailable or data integrity is at risk. | Yes |
| P1 | SLO breach, sustained error-rate increase, sustained latency breach, or growing async backlog. | Yes |
| P2 | Resource or dependency pressure that threatens an SLO. | Work-hours or routed notification |
| P3 | Trend or optimization signal. | No |

Resource-only alerts should not page by themselves unless they are tied to a user-impacting SLI or a near-term exhaustion condition.

## Governance

The contract files under `deploy/observability/contracts` are the machine-readable source for static observability governance tests.

Run static governance:

```bash
bash deploy/tests/observability_contracts.sh
```

Run local smoke after the stack is up:

```bash
./deploy/tests/observability_smoke.sh
```

For a short diagnostics run:

```bash
RUNTIME_DIAGNOSTICS_ENABLED=true RUNTIME_DIAGNOSTICS_INCLUDES='com.nowcoder.community.*' ./deploy/deployment.sh up --topology single
OBSERVABILITY_EXPECT_DIAGNOSTICS=true ./deploy/tests/observability_smoke.sh
```

Runtime diagnostics remains default-off and must not collect payload data or secrets.
