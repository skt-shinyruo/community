# Community Observability System Design

Date: 2026-06-14

## Status

Approved for implementation planning.

## Context

Community already has the main pieces of an observability stack:

- OpenTelemetry Java Agent support in backend service images.
- EDOT Collector receiving OTLP traces and metrics.
- EDOT Collector reading Docker stdout JSON logs and exporting them to
  Elasticsearch.
- Kibana and Elasticsearch in the local observability compose overlay.
- Shared Logback JSON stdout configuration in `community-common-observability`.
- Shared runtime logging for JVM, GC, process resources, executors, data sources,
  SQL, Redis, Kafka, OSS, HTTP clients, scheduled jobs, cache, security, and
  logging-system pressure.
- `runtime-diagnostics-agent` for opt-in short diagnostic sessions.

The first production direction is not to introduce a second observability stack.
The first phase should make the local and demo observability baseline reliable,
queryable, and automatically verifiable while preserving a clean path for later
production-grade SLOs, alerts, and Prometheus/Grafana compatibility.

## Goals

- Deliver a runnable local/demo stability observability baseline.
- Keep Elastic/Kibana as the local UI and storage path for phase 1.
- Keep OpenTelemetry tracing as the request and async correlation source.
- Keep runtime logs as the always-on stability signal for discrete events,
  threshold crossings, and state changes.
- Keep `runtime-diagnostics-agent` disabled by default and documented as a
  short troubleshooting tool.
- Verify the observability baseline with scripts, not manual Kibana inspection.
- Preserve low-cardinality fields and strict sensitive-data exclusion.
- Define production compatibility points for later metrics, SLOs, and alerting
  without adding Prometheus, Grafana, or Alertmanager in phase 1.

## Non-Goals

- Do not replace OpenTelemetry Java Agent tracing.
- Do not introduce Prometheus, Grafana, Loki, or Alertmanager in phase 1.
- Do not collect request bodies, response bodies, SQL bind values, Redis keys or
  values, Kafka payloads, object keys, JWTs, cookies, tokens, credentials, or
  user-generated content.
- Do not log every SQL statement, Redis command, Kafka message, or HTTP access
  event by default.
- Do not make `runtime-diagnostics-agent` a default production behavior.
- Do not add business-domain observability code. Business events stay owned by
  application/domain behavior and shared observability remains infrastructure.

## Signal Responsibilities

### Traces

OpenTelemetry tracing is the always-on timeline signal. It answers:

- Which service, handler, dependency, consumer, or job handled a request or
  async unit of work.
- Where latency was spent.
- Where a traced failure occurred.
- Which logs and metrics share the same `trace.id`.

`trace.id` is the global pivot for request-related investigation.

### Runtime Logs

Runtime logs are the always-on stability signal. They answer:

- Whether a service or process is degraded.
- Whether JVM, GC, direct memory, CPU, file descriptors, or disk usage crossed a
  threshold.
- Whether executor, Hikari, Redis, Kafka, HTTP client, OSS, job, cache, security,
  or logging-system behavior crossed a threshold or failed.
- Which stable event occurred at a specific time.

Runtime logs use JSON stdout and flow through the existing collector logs
pipeline. They must prefer threshold and state-change events over high-volume
per-operation logs.

### Runtime Diagnostics Agent

`runtime-diagnostics-agent` is an opt-in short-session diagnostic tool. It is used
only when traces and runtime logs do not provide enough detail. It may provide:

- Method latency summaries and slow-method events.
- Exception observations from instrumented methods.
- Thread snapshots and lock/deadlock summaries.
- JVM summaries.
- Optional HTTP, JDBC, Redis, and Kafka dependency call summaries.

It reads existing OpenTelemetry or MDC trace context when available. It must not
create a second trace system or replace OpenTelemetry instrumentation.

## Target Local Architecture

```text
Backend JSON stdout logs
  -> Docker stdout log files
  -> EDOT Collector filelog/docker_stdout receiver
  -> transform and backend JSON log filter
  -> Elasticsearch logs-community-default
  -> Kibana

OTLP traces and metrics
  -> EDOT Collector OTLP receiver
  -> Elasticsearch traces-* / metrics-*
  -> Kibana

Optional runtime-diagnostics-agent events
  -> JSON stdout
  -> same logs pipeline
  -> Elasticsearch logs-community-default
  -> Kibana
```

Local `deployment.sh up` should keep the observability overlay enabled by
default. Direct `docker compose` use may still default `OTEL_ENABLED=false`, but
the documented local workflow should start services with OTel enabled unless the
operator explicitly disables observability.

## Always-On Baseline

These signals should be available in local/demo runs without extra diagnostic
flags:

- OpenTelemetry Java Agent traces and metrics for backend services.
- JSON stdout logs with stable resource fields.
- Runtime lifecycle events for application start and shutdown.
- JVM and GC threshold events and periodic summaries.
- Process resource threshold events for CPU load, file descriptors, and disk
  usage.
- Executor inventory and saturation events.
- Hikari/data source startup and pool pressure events.
- SQL slow/failure events without bind values.
- Redis slow/failure/connectivity events without raw keys or values.
- Kafka producer, consumer, rebalance, assignment, lag, and failure events where
  available.
- HTTP slow request events with method, route or sanitized path, status, and
  duration.
- HTTP client slow/failure events without request or response bodies.
- OSS slow/failure events without object keys or credentials.
- Scheduled job and outbox failure, delay, terminal-state, or slow-operation
  events.
- Security and logging-system pressure events.

Periodic summaries should default to conservative intervals such as 60 seconds.
Threshold events should be preferred over per-call events.

## Default-Off Diagnostics

These are disabled unless an operator explicitly starts a short diagnostic run:

- `RUNTIME_DIAGNOSTICS_ENABLED=true`.
- Diagnostics method, exception, thread, and JVM probes.
- Diagnostics HTTP, JDBC, Redis, and Kafka dependency probes.
- Lower slow-call thresholds.
- Higher-frequency JVM or thread snapshots.

Recommended safe diagnostic defaults:

```text
RUNTIME_DIAGNOSTICS_ENABLED=true
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL=60s
RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_SAMPLE_RATE=1.0
```

Dependency probes are added only when needed:

```text
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm,http,jdbc,redis,kafka
RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND=20
```

## Field Contract

All always-on runtime events and diagnostic events should stay queryable through
stable low-cardinality fields.

Required common fields where applicable:

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
error.message
```

`trace.id` and `span.id` are required for request or async-work events when an
OpenTelemetry context exists. Process-level background events may omit trace
fields when they do not belong to a request or job.

Stable categories:

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
```

Subsystem fields should remain sanitized and low cardinality:

- JVM: `jvm.memory.area`, `jvm.memory.used.bytes`, `jvm.memory.max.bytes`,
  `jvm.gc.name`, `jvm.gc.pause.ms`.
- Process: `process.cpu.load.percent`, `process.open_file_descriptors`,
  `process.max_file_descriptors`, `disk.usage.percent`.
- Executor: `executor.name`, `executor.active`, `executor.pool.size`,
  `executor.queue.size`, `executor.queue.remaining`.
- Data source: `db.system`, `db.name`, `db.pool.name`, `db.pool.active`,
  `db.pool.idle`, `db.pool.pending`.
- Redis/cache: `cache.system`, `cache.operation`, `cache.mode`,
  `net.peer.name.hash`.
- Kafka/messaging: `messaging.system`, `messaging.destination.name.hash`,
  `messaging.kafka.consumer.group`, `messaging.kafka.partition`.
- HTTP: `http.request.method`, `http.route`, `url.path.sanitized`,
  `http.response.status_code`.
- Job: `job.system`, `job.name`, `job.shard.index`, `job.shard.total`.
- Diagnostics: `diagnostic.agent.name`, `diagnostic.probe`.

## Component Design

### Deploy Configuration

The deployment layer owns local defaults and operator entry points:

- `deployment.sh up` defaults to observability enabled.
- OTel exporter endpoint and protocol remain aligned with the collector HTTP
  receiver.
- Backend services set distinct `OTEL_SERVICE_NAME` values.
- `OTEL_RESOURCE_ATTRIBUTES` includes `deployment.environment` and the backend
  runtime appends `service.version`.
- `OTEL_LOGS_COLLECTION=stdout` remains the local log collection mode.
- `RUNTIME_DIAGNOSTICS_ENABLED=false` remains the default.
- Environment examples document safe diagnostic commands.

### Collector

The collector remains the local observability boundary:

- OTLP receiver handles traces and metrics.
- Docker stdout filelog receiver handles backend JSON logs.
- JSON log parsing should parse map bodies only when stdout line bodies are JSON.
- Backend log filtering should keep service JSON logs and avoid collecting
  arbitrary container noise in phase 1.
- Resource processors should upsert `service.namespace=community`.
- Exporters write traces, metrics, and logs to Elasticsearch.

### Common Observability

`community-common-observability` owns always-on runtime event creation:

- Continue shared Logback JSON stdout configuration.
- Keep runtime event creation in shared infrastructure modules.
- Ensure every backend deployable includes shared Logback config and depends on
  the common observability module where appropriate.
- Fill stability event gaps before adding new diagnostic tools.
- Keep thresholds configurable through `community.observability.runtime-logging`.

### Runtime Diagnostics Agent

The diagnostics agent is operationally separate from always-on observability:

- Keep the agent jar available in backend images.
- Keep startup controlled by `RUNTIME_DIAGNOSTICS_ENABLED`.
- Keep includes narrow during troubleshooting.
- Emit diagnostic events to stdout with `event.category=runtime_diagnostics`.
- Reuse existing trace context only.
- Avoid payload, key, bind value, or secret collection.

### Documentation

The handbook should contain scenario-oriented runbooks:

- JVM or memory pressure.
- GC pause spikes.
- CPU or FD pressure.
- Thread pool saturation.
- Hikari/database pool pressure.
- Redis instability or slow operations.
- Kafka lag, rebalance, producer failure, or consumer failure.
- Slow HTTP requests.
- Job/outbox failure or delay.
- When and how to enable runtime diagnostics.

Each runbook should use this shape:

```text
Symptom
Relevant Kibana query
Fields to inspect
Expected interpretation
Next action
When to enable runtime diagnostics
```

## Verification Design

Observability must be tested as a runnable capability.

### Baseline Smoke

After local services start:

- Confirm collector, Elasticsearch, Kibana, and backend services are running.
- Call one backend route through the normal gateway path.
- Confirm an Elasticsearch trace document exists for the request.
- Confirm a JSON runtime log exists with `service.name`,
  `service.namespace=community`, and `deployment.environment`.

### Stability Event Smoke

Use startup events, periodic summaries, or temporarily lowered thresholds to
confirm at least one event from these groups:

- JVM or GC.
- Process resources.
- Executor.
- Data source.
- Redis or cache where available.
- Kafka where available.

### Correlation Smoke

For a normal request:

- Capture `traceId` from response body or `traceparent` header.
- Query traces by `trace.id`.
- Query logs by the same `trace.id`.
- Confirm request-related logs and spans share the same id.

Background process events without trace context should be documented as
process-level events and should still include service and environment fields.

### Diagnostics Smoke

Start one short diagnostic run with narrow includes:

- `RUNTIME_DIAGNOSTICS_ENABLED=true`.
- `RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*`.
- `RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm`.

Verify:

- `event.category=runtime_diagnostics` appears in logs.
- `diagnostic.probe` is populated.
- Trace fields appear when an OTel or MDC context exists.
- Sensitive fields do not appear.

### Automated Tests

Add or extend `deploy/tests` scripts to verify:

- Compose config renders OTel and diagnostics defaults correctly.
- Collector pipelines contain logs, traces, and metrics paths.
- Backend services include `OTEL_SERVICE_NAME` and stdout log collection.
- Runtime diagnostics remains disabled by default.
- Elasticsearch queries find required baseline fields after smoke traffic.

Unit tests continue to cover field names, redaction, thresholds, and runtime
logger behavior.

## Production Compatibility

Phase 1 does not install production alerting. It should still keep production
future work clean:

- Keep stable fields aligned with common OpenTelemetry and Elastic semantic
  conventions where practical.
- Keep metrics available through OTLP so a later Prometheus/Grafana path can be
  added without changing application code.
- Define SLO candidates in the handbook:
  - HTTP availability and latency by service and route.
  - Kafka consumer lag and processing failure rate.
  - Database pool pending and acquisition latency.
  - Redis operation error rate and slow operation rate.
  - JVM memory pressure and GC pause rate.
  - Executor saturation.
  - Outbox backlog and terminal failure rate.
- Define alert candidates as threshold rules over metrics or runtime events, but
  do not create a second alerting stack in phase 1.

## Implementation Phases

### Phase 1: Baseline Audit and Default Convergence

Audit deployment, compose environment, OTel agent startup, JSON stdout logs,
collector pipelines, and Kibana/Elasticsearch query targets.

Success criteria:

- Local documented startup path enables observability.
- Backend services emit JSON stdout in compose-like profiles.
- Collector writes logs, traces, and metrics to Elasticsearch.
- `service.name`, `service.namespace`, `deployment.environment`, and
  `service.version` are stable.

### Phase 2: Stability Event Coverage

Fill missing runtime stability event coverage without adding high-volume logs.

Success criteria:

- JVM/GC, process resources, executor, Hikari/database, Redis, Kafka, HTTP slow,
  job/outbox, and logging-system pressure have event contracts and queries.
- Sensitive fields remain excluded.
- Thresholds are documented and configurable.

### Phase 3: Automated Observability Verification

Turn observability availability into scripts.

Success criteria:

- Smoke scripts verify indexes, fields, and trace/log correlation through
  Elasticsearch HTTP APIs.
- Default configuration tests catch accidental OTel, diagnostics, logback, or
  collector regressions.
- Failures report missing service, index, field, or event names.

### Phase 4: Runbooks and Production Compatibility

Document operator workflows and future production integration points.

Success criteria:

- `docs/handbook` contains scenario-oriented runbooks.
- The handbook explains when to use traces, runtime logs, metrics, and runtime
  diagnostics.
- Production SLO and alert candidates are explicit.
- The decision to defer Prometheus/Grafana/Alertmanager is documented.

## Completion Definition

The phase 1 observability system is complete when:

- A local/demo operator can start the stack and use Kibana to inspect runtime
  stability events without extra manual collector setup.
- A request `traceId` can pivot between traces and request-related logs.
- Runtime stability problems have predictable event categories, actions, fields,
  and runbook queries.
- `runtime-diagnostics-agent` can be enabled for short sessions with safe
  defaults and clear queries.
- Automated tests or smoke scripts verify the baseline.
- Later production alerting work can build on the same field and signal contract
  without replacing the local baseline.
