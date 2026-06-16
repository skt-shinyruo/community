# Community Observability Upgrade Design

Date: 2026-06-16

## Status

Draft for review.

## Context

Community already has an OpenTelemetry-first local observability baseline:

- Backend service images can start the OpenTelemetry Java Agent.
- Backend services emit structured JSON logs to stdout through the shared
  `community-common-observability` Logback configuration.
- `community-common-observability` emits always-on runtime stability events for
  JVM, GC, process resources, executors, data sources, SQL, Redis, Kafka, OSS,
  HTTP clients, scheduled jobs, cache, security, and logging-system pressure.
- EDOT Collector receives OTLP traces and metrics, reads Docker stdout JSON
  logs, normalizes resource fields, and exports to Elasticsearch.
- Kibana and Elasticsearch provide the local and demo inspection path.
- `runtime-diagnostics-agent` is available in backend images but is disabled by
  default for short troubleshooting sessions only.
- `deploy/tests/observability_smoke.sh` verifies baseline log, trace, and
  correlation availability.

The next upgrade should not replace this stack. The priority is to turn the
current signal collection baseline into an operational closed loop: business
objective, service-level indicator, alert, dashboard, trace/log investigation,
and runbook.

## Goals

- Define service and business SLO/SLI ownership before adding more signals.
- Standardize logs, metrics, traces, event names, and forbidden fields.
- Keep OpenTelemetry as the collection and propagation standard.
- Keep EDOT Collector as the local observability boundary and preserve a clean
  production path for agent/gateway Collector deployment.
- Add focused business metrics and spans at application and infrastructure
  boundaries without polluting domain models.
- Improve trace continuity across HTTP, Kafka, outbox, and scheduled-job
  boundaries.
- Make runtime stability events queryable through stable low-cardinality fields.
- Add governance tests for field presence, sensitive-data exclusion, and
  high-cardinality label rejection.
- Keep Elastic/Kibana as the local and demo UI for this phase while defining
  production compatibility points for Prometheus/Mimir/Grafana/Alertmanager.

## Non-Goals

- Do not replace the existing EDOT Collector, Elasticsearch, or Kibana local
  baseline in this upgrade.
- Do not introduce Loki, Tempo, Jaeger, Mimir, Grafana, or Alertmanager as
  required local dependencies in this upgrade.
- Do not make `runtime-diagnostics-agent` always-on.
- Do not add method-level instrumentation across all business code.
- Do not add observability code to domain models or domain services.
- Do not collect request bodies, response bodies, SQL bind values, Redis keys or
  values, Kafka payloads, object keys, JWTs, cookies, tokens, credentials,
  authorization headers, or user-generated content.
- Do not create metrics or log labels containing user IDs, order IDs, UUIDs,
  full URLs, IP addresses, stack traces, timestamps, or other high-cardinality
  values.

## Target Operating Model

Observability should be managed as a product capability, not as scattered
logging code.

```text
Business objective
  -> SLI and SLO
  -> metric, trace, and runtime-event contract
  -> dashboard and alert
  -> runbook query and next action
  -> automated smoke or governance test
```

Each P0 or P1 alert must have a documented runbook. Each runbook should answer:

- Which user path or async workflow is affected.
- Which SLI breached.
- Which Kibana or future Grafana query confirms the symptom.
- Which `trace.id`, event category, metric, or dependency field narrows the
  failure domain.
- Which next action an operator should take.
- When to enable `runtime-diagnostics-agent`.

## Core SLO/SLI Catalog

The first catalog should cover these user and operational paths.

### Authentication

- Flows: login, token refresh, logout.
- SLIs: success rate, P95 and P99 latency, authentication error rate, session
  refresh failure rate.
- Required correlation: HTTP trace, security runtime events, audit logs, Redis
  and database dependency timing where applicable.

### Content

- Flows: publish post, publish comment, refresh post score, moderation-sensitive
  writes.
- SLIs: write success rate, P95 and P99 latency, database write failure rate,
  outbox publication delay, search indexing delay where applicable.
- Required correlation: HTTP trace, application result metric, SQL slow/failure
  event, outbox event trace.

### IM

- Flows: establish WebSocket session, send message, consume command, fan out
  room message.
- SLIs: send success rate, server-side processing latency, Kafka lag,
  WebSocket connection error rate, fanout failure rate.
- Required correlation: gateway trace, Kafka publish and consume trace context,
  messaging runtime events, IM-specific Micrometer gauges where already present.

### OSS and Drive

- Flows: upload object, download object, create drive object, resolve storage
  metadata.
- SLIs: success rate, P95 and P99 operation latency, storage dependency failure
  rate, upload failure rate.
- Required correlation: HTTP trace, OSS slow/failure runtime events, sanitized
  storage endpoint fields.

### Search

- Flows: search query, index update, index refresh.
- SLIs: query success rate, query latency, index delay, Elasticsearch dependency
  failure rate.
- Required correlation: HTTP trace, search application result metric,
  Elasticsearch client failure event if available.

### Wallet, Market, and Growth

- Flows: market exchange, wallet transaction, reward progress, level update.
- SLIs: business operation success rate, duplicate/idempotent rejection rate,
  consistency compensation count, async backlog, terminal failure count.
- Required correlation: application metric, outbox/job runtime event, database
  dependency event, trace across async work.

## Signal Contract

### Shared Resource Fields

All backend logs, metrics, and traces should use these resource fields:

```text
service.name
service.version
service.namespace=community
deployment.environment
service.instance.id
service.zone
release.track
```

`service.instance.id`, `service.zone`, and `release.track` may be absent in local
development, but production-compatible configuration should define them.

### Runtime Event Fields

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

`trace.id` and `span.id` are required when an event belongs to a request,
message, outbox item, or job with an active OpenTelemetry context. Process-level
events may omit trace fields.

### Stable Event Categories

The stable event categories are:

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

`business` is reserved for low-volume, application-owned semantic events and
must not replace domain events or contracts. Business-domain state changes still
belong to application/domain behavior; the observability event is only an
operator-facing signal.

### Metrics Naming

Metrics should use explicit units and bounded dimensions. For OTel metric
attributes, backend exporters may map keys to their own label syntax. Recommended
families:

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

Allowed common dimensions:

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

### Span Naming

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

Span attributes must use sanitized, low-cardinality values. Do not attach
payloads, object keys, SQL bind values, Redis keys, JWTs, cookies, or raw user
content.

## Application Instrumentation Boundaries

Instrumentation must follow the repository's DDD layering rules.

- Controllers, listeners, handlers, bridges, enqueuers, and jobs remain inbound
  adapters. They may bind/extract trace context and call same-domain
  `*ApplicationService`, but they must not perform foreign-domain collaboration
  or direct infrastructure access.
- Application services own use-case result classification and may record
  business metrics or low-volume business observability events.
- Domain models and domain services must remain free of observability framework
  dependencies.
- Infrastructure adapters own dependency timing, slow/failure events, and
  implementation-specific metrics for MyBatis, Redis, Kafka, OSS, HTTP clients,
  and outbox persistence.
- Shared observability helpers belong in `community-common-observability` or
  `community-common/common-core` trace utilities.

## Trace Continuity

The correlation contract is one visible `trace.id` per request or async unit of
work.

HTTP behavior:

- Inbound HTTP should join a valid W3C `traceparent` or start an OTel server
  span.
- Responses should include the active `traceparent`.
- JSON `Result.traceId` should match the active OTel trace id.

Async behavior:

- Outbox rows should store the active trace id and `traceparent`.
- Kafka producers should inject W3C trace context into headers.
- Kafka consumers should extract trace context and create consumer spans.
- Scheduled jobs should create a root span when no parent trace exists.
- Outbox workers should continue the stored trace context when publishing or
  handling outbox events.

Validation should prove a trace can be followed through HTTP, outbox, Kafka, and
job logs where those boundaries are present.

## Runtime Event Coverage

The always-on event baseline should prioritize state changes, threshold
crossings, failures, and slow operations.

Required event coverage:

- HTTP server slow request and access threshold events.
- HTTP client slow and failure events.
- SQL slow and failure events without SQL bind values.
- DataSource startup and pool pressure events.
- Redis slow, failure, and connectivity events without raw keys or values.
- Kafka producer failure, slow send, rebalance, assignment, consumer failure,
  and lag events where available.
- OSS slow and failure events without object keys.
- Scheduled job start, success, failure, slow run, and delay events.
- Outbox backlog, publish delay, terminal failure, and retry exhaustion events.
- JVM, GC, direct memory, CPU, file descriptor, and disk pressure events.
- Executor inventory, saturation, and rejection events.
- Security and logging-system pressure events.

Default behavior should avoid per-operation logs for normal SQL, Redis, Kafka,
HTTP, and OSS calls.

## Collector Evolution

### Local and Demo

Keep the current local path:

```text
Docker stdout JSON logs
OTLP traces
OTLP metrics
  -> EDOT Collector
  -> Elasticsearch
  -> Kibana
```

`deployment.sh up` should continue to enable the observability overlay by
default. `--no-observability` should continue to disable the overlay and OTel.

### Production-Compatible Shape

Add configuration templates for a future two-layer Collector shape:

```text
Service OTLP and stdout
  -> node or sidecar Collector
      -> memory limiter
      -> batch
      -> local redaction
      -> resource detection
  -> gateway Collector
      -> tail sampling
      -> cardinality and field governance
      -> routing
      -> Elasticsearch / log backend
      -> Prometheus remote write or Mimir
      -> trace backend
```

This upgrade should document the shape and keep application code independent of
the chosen storage backends. Actual production backend rollout can be a later
plan.

## Dashboards, Alerts, and Runbooks

The repository should own dashboard and alert definitions where practical.

Minimum dashboard sections per backend service:

- Request rate, error rate, and P50/P95/P99 latency.
- JVM heap, non-heap, direct memory, GC count, and GC pause.
- Thread pool active count, queue size, and rejection count.
- DataSource active, idle, pending, and acquisition latency.
- Redis latency and failure rate.
- Kafka producer failures, consumer failures, lag, and rebalance events.
- HTTP client and OSS dependency latency and failure rate.
- Recent deployments or service versions.
- Error event Top N.
- Slow trace examples or trace query links.

Alert priority model:

- P0: core user path is broadly unavailable or data integrity is at risk.
- P1: SLO breach, sustained error-rate increase, sustained latency breach, or
  growing async backlog.
- P2: resource or dependency pressure that threatens an SLO.
- P3: trend or optimization signal.

Resource-only alerts should not page by themselves unless they are tied to a
user-impacting SLI or a near-term exhaustion condition.

## Verification and Governance

Extend automated verification in phases.

Baseline smoke:

- Collector has logs, traces, metrics, and OTLP logs pipelines.
- Backend services include `OTEL_SERVICE_NAME`.
- Runtime diagnostics remains disabled by default.
- JSON stdout logs include required resource fields.
- Request `traceId` appears in both `traces-*` and `logs-community-default`.

Signal governance tests:

- Required runtime event fields are present for each category.
- Forbidden sensitive fields do not appear in logs or spans.
- Metric label names do not include known high-cardinality fields.
- Logback JSON output does not emit legacy `traceId` fields in addition to
  `trace.id`.
- Collector transform and filter stages preserve only backend JSON logs in the
  local logs pipeline.

Scenario smoke:

- HTTP request correlation.
- Outbox or Kafka trace propagation.
- One runtime stability event from JVM/process/executor.
- One dependency slow or failure event with sanitized fields.
- Optional diagnostics event when `RUNTIME_DIAGNOSTICS_ENABLED=true`.

## Phasing

### Phase 1: SLO and Contract Foundation

Deliver:

- SLO/SLI catalog for core flows.
- Signal field contract and forbidden field list.
- Metrics naming and label contract.
- Initial runbook skeletons for P0 and P1 symptoms.

Success criteria:

- Every priority flow has at least success rate, latency, and failure
  classification.
- Every P0/P1 alert candidate maps to a user path and a runbook.
- Governance rules are clear enough to implement tests.

### Phase 2: Runtime Event and Metrics Coverage

Deliver:

- Missing runtime event coverage for HTTP, SQL, Redis, Kafka, OSS, jobs, outbox,
  and logging-system pressure.
- Application-service metrics for selected core use cases.
- Dependency metrics with bounded labels.

Success criteria:

- Kibana can query each stable event category.
- Metrics expose RED-style service indicators and core dependency indicators.
- Sensitive and high-cardinality data remains excluded.

### Phase 3: Trace Continuity and Sampling Design

Deliver:

- Explicit trace propagation checks for HTTP, Kafka, outbox, and scheduled jobs.
- Manual spans only at approved application and infrastructure boundaries.
- Tail-sampling policy design for gateway Collector production use.

Success criteria:

- A request-related `trace.id` can pivot across traces, logs, outbox, and Kafka
  where applicable.
- Failed and slow operations have trace samples.
- Normal traffic can be sampled without losing error and slow-request evidence.

### Phase 4: Dashboards, Alerts, and Production Compatibility

Deliver:

- Versioned dashboard definitions or saved objects.
- Alert candidate definitions and runbooks.
- Collector agent/gateway configuration templates.
- Production metrics backend compatibility notes.

Success criteria:

- Operators can investigate common incidents without ad hoc Elasticsearch
  queries.
- Local/demo remains one-command runnable.
- Production can add Prometheus/Mimir/Grafana/Alertmanager without application
  code changes.

## Completion Definition

The observability upgrade is complete when:

- Core business flows have documented SLOs, SLIs, dashboard sections, and
  runbooks.
- Logs, metrics, and traces use one consistent field and naming contract.
- `trace.id` remains the pivot across HTTP, async work, traces, and logs.
- Runtime stability events cover JVM, process, executor, database, Redis, Kafka,
  HTTP, OSS, jobs, outbox, security, and logging pressure.
- Automated checks catch missing pipeline pieces, missing required fields,
  sensitive-data leaks, and high-cardinality metric labels.
- Local Elastic/Kibana observability remains stable.
- Production Collector and metrics-backend evolution is possible without
  rewriting application instrumentation.
