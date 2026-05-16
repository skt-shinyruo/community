# Community OTel-First Observability Design

Date: 2026-05-16

## Status

Draft for implementation planning. Implementation has not started.

This spec supersedes the file-volume collection parts of
`docs/superpowers/specs/2026-05-13-community-runtime-observability-logs-design.md`.
It keeps that spec's runtime event vocabulary, redaction rules, and low-cardinality
logging goals, but changes the transport and trace correlation model.

## Context

The current local observability path has two separate collection paths:

```text
backend structured JSON file appender
  -> shared observability volume
  -> EDOT collector filelog receiver
  -> Elasticsearch
  -> Kibana

OTLP traces / metrics
  -> EDOT collector
  -> Elasticsearch
```

The current trace id exposed to clients is produced by repository-owned
`TraceIdFilter` / `TraceIdWebFilter` logic. OpenTelemetry Java Agent can also create
and propagate trace ids for spans. These ids can align when a valid upstream
`traceparent` is already present, but they are not guaranteed to be the same for
browser requests that arrive without a `traceparent`.

The target system must let an operator take one trace id from a frontend error, a
backend response, a log event, an outbox row, or a Kafka message and reliably pivot to:

- the complete trace span tree and per-hop duration,
- request-related business, audit, exception, and threshold logs,
- service and infrastructure metrics around the same time window.

Breaking changes are allowed.

## Problem Statement

The platform needs one correlation contract and one collection boundary for all
observability signals. The current file-volume logging path works for local compose,
but it creates avoidable differences from the traces / metrics path:

- logs are file-based while traces and metrics are OTLP-based,
- log trace ids can be project-generated while span trace ids are OTel-generated,
- every service must maintain file appenders, mounted log volumes, and filename env vars,
- Kibana saved objects and runbooks depend on two ingestion shapes,
- adding new deployables risks missing file appenders or volume mounts.

The new design should make OpenTelemetry the source of truth for trace context while
preserving SLF4J as the application logging API.

## Goals

- Make OpenTelemetry trace context the only source of request and async correlation.
- Ensure `Result.traceId`, response `traceparent`, log `trace.id`, outbox trace fields,
  Kafka trace headers, and `traces-*` all refer to the same trace.
- Replace `/var/log/community/*.json.log` collection with stdout JSON logs collected by
  the collector and normalized into the collector logs pipeline.
- Keep SLF4J / Logback as the Java logging API and local console implementation.
- Keep the existing EDOT / Elastic / Kibana local observability stack.
- Reduce duplicate logging by using traces and metrics for continuous access and
  latency data, and logs for semantic events, failures, and threshold crossings.
- Keep runtime logging in shared infrastructure modules, outside business domain code.
- Preserve strict redaction and low-cardinality field rules.
- Provide migration tests that prove one frontend-visible trace id pivots across
  logs and traces.

## Non-Goals

- Do not introduce Grafana, Loki, Prometheus, or Alertmanager into the default stack.
- Do not require application code to call Elasticsearch, Kibana, or collector APIs.
- Do not make direct OTLP log export from the Java process the phase 1 default.
  It can be evaluated later after stdout collection is stable.
- Do not log every SQL statement, Redis command, Kafka message, or HTTP access event.
- Do not log request bodies, response bodies, SQL bind values, Redis keys, Kafka
  payloads, object keys, tokens, cookies, credentials, or user-generated content.

## Key Decisions

### Decision 1: OTel Context Owns Trace Identity

`io.opentelemetry.api.trace.Span.current().getSpanContext()` becomes the source of
truth for trace ids and span ids.

Repository-owned `TraceId` / `TraceContext` ThreadLocal code must no longer generate
or own the primary trace id. It may remain temporarily as a compatibility adapter that
reads the current OTel trace id, but new code must not depend on it as an independent
correlation source.

### Decision 2: Logs Emit To Stdout

Backend services emit structured JSON logs to stdout in compose and production-like
profiles. The shared `/var/log/community` application log volume, `volume-log-export`
profile, `COMMUNITY_LOGGING_DIR`, and `COMMUNITY_LOGGING_FILE_NAME` are removed.

Local and test profiles may keep human-readable text logs when the collector is not
part of the workflow, but compose defaults must use JSON stdout.

### Decision 3: Collector Is The Observability Boundary

The collector receives:

```text
container stdout JSON logs
OTLP traces
OTLP metrics
```

The collector parses logs into the OpenTelemetry log data model, enriches resource
attributes, and exports to Elasticsearch. Traces and metrics continue through OTLP
receivers.

### Decision 4: Logs Are Events, Traces Are Timelines, Metrics Are Trends

Normal request duration and per-hop latency belong in traces and metrics. Logs should
represent semantic events and discrete diagnosis points:

- business events,
- audit events,
- security events,
- exceptions,
- slow operations above configured thresholds,
- resource pressure threshold crossings,
- job, outbox, and projection failures or terminal states.

## Target Architecture

```text
Browser / caller
  -> W3C traceparent when available
  -> community-gateway / backend service
      -> OTel Java Agent creates or joins server span
      -> application code logs through SLF4J
      -> response includes OTel trace id and traceparent
      -> Kafka/outbox/job boundaries propagate OTel traceparent

Backend stdout
  -> Docker JSON logs
  -> EDOT collector logs pipeline
  -> Elasticsearch logs-*
  -> Kibana

OTLP traces / metrics
  -> EDOT collector OTLP receiver
  -> Elasticsearch traces-* / metrics-*
  -> Kibana
```

## Component Design

### Trace Response Bridge

Servlet and WebFlux response infrastructure must expose the current OTel trace id:

- `Result.traceId` is set to the active OTel trace id when a valid span exists.
- `traceparent` response header reflects the active OTel trace context.
- If no active span exists in an internal or scheduled context, the infrastructure
  must create an explicit root span before producing externally visible trace ids.

The bridge replaces the current behavior where `TraceIdCodec.resolveTraceId()` can
generate a repository-owned trace id independently of OTel.

### Log Correlation

Every JSON log emitted during an active span must include:

```text
trace.id
span.id
```

The implementation may use OTel Java Agent log correlation, a Logback provider that
reads the current `Span`, or a shared correlation adapter, but the contract is tested
at runtime. Logs must not rely on a custom independent ThreadLocal trace id.

`trace.id` is omitted only for process-level events that truly have no current span,
such as JVM startup before a root span exists. Runtime snapshot logs can optionally
open a low-cost internal span if correlation is useful.

### Logback Configuration

Each backend deployable should use a shared Logback shape:

- text console for local/test without collector,
- JSON stdout for compose/prod-like profiles,
- no file appenders for application logs,
- no module-specific duplicated field allowlists.

JSON logs must include stable fields:

```text
@timestamp
service.name
service.version
service.namespace
deployment.environment
trace.id
span.id
level
logger
message
stack_trace
event.category
event.action
event.outcome
```

Subsystem fields remain low cardinality, for example:

```text
http.request.method
http.route
http.response.status_code
duration.ms
threshold.ms
db.system
db.operation
db.pool.pending
messaging.system
messaging.destination.name
messaging.kafka.consumer.group
jvm.gc.pause.ms
executor.name
job.name
```

### Collector Logs Pipeline

The collector must read backend container stdout logs rather than application log
files. The local compose implementation should use a container stdout collection
receiver or a filelog receiver over Docker JSON logs, with resource enrichment that
sets:

```text
service.namespace=community
deployment.environment=local-compose
container.name
service.name
```

The collector must parse JSON log bodies, preserve structured fields, and route logs
to a logs index or data stream compatible with existing Kibana searches. Non-JSON
infrastructure container logs may be collected in a separate dataset to avoid field
shape conflicts.

### Trace Propagation

HTTP, WebFlux, Kafka, outbox, scheduled jobs, and XXL-Job boundaries use W3C Trace
Context:

```text
traceparent
tracestate when present
```

Kafka producers and consumers must read and write W3C trace headers. Outbox rows keep
`traceparent` as the canonical stored context. `trace_id` may remain as a derived
query column, but it must be computed from `traceparent` and must not be generated
independently.

Job and outbox workers without an inbound trace must open a new root span with stable
span names such as:

```text
outbox.process projection.search.post
scheduled.marketWalletActionProcessor
xxl.marketOrderAutoConfirm
```

## Logging Policy

### Keep As Logs

- business events with durable meaning,
- security and audit events,
- unexpected exceptions,
- validation and dependency failures that operators must investigate,
- threshold events for slow requests, slow SQL, slow Redis commands, Kafka lag,
  Hikari pressure, JVM memory pressure, GC pause, executor pressure, and disk/fd/cpu
  pressure,
- outbox/job retry, dead, recovery, skip, and failure events.

### Prefer Traces Or Metrics

- full HTTP access logs for every request,
- every SQL statement,
- every Redis command,
- every Kafka send/consume,
- continuous JVM memory or CPU measurements,
- normal success paths that do not carry business or audit meaning.

## Frontend Contract

The frontend continues to display and store a `traceId`, but its semantics change:

```text
traceId == OTel trace id
```

Frontend error toasts and debugging surfaces can use that id to search both:

```text
traces-*: trace.id : "<traceId>"
logs-*:   trace.id : "<traceId>"
```

No browser-side OTel SDK is required for this phase. Browser-generated traceparent can
be evaluated later if frontend spans become necessary.

## Configuration Changes

Remove:

```text
volume-log-export
COMMUNITY_LOGGING_DIR
COMMUNITY_LOGGING_FILE_NAME
observability_logs:/var/log/community for application logs
FILE_JSON Logback appenders
```

Keep:

```text
OTEL_ENABLED
OTEL_EXPORTER_OTLP_ENDPOINT
OTEL_EXPORTER_OTLP_PROTOCOL
OTEL_SERVICE_NAME
OTEL_RESOURCE_ATTRIBUTES
SERVICE_VERSION
community.observability.runtime-logging.*
```

Add or standardize:

```text
COMMUNITY_LOG_FORMAT=json|text
DEPLOYMENT_ENVIRONMENT=local-compose
OTEL_LOGS_COLLECTION=stdout
```

`deployment.sh` should still default `OTEL_ENABLED=true` when the observability
overlay is enabled, and `--no-observability` should disable collector-dependent
features while leaving stdout logs available through `docker compose logs`.

## Migration Plan

1. Add OTel trace context response bridge tests for Servlet and WebFlux.
2. Change `Result.traceId` filling to read active OTel span context.
3. Replace custom trace id generation at HTTP boundaries with OTel-compatible bridge
   behavior.
4. Update Kafka and outbox propagation to treat `traceparent` as canonical.
5. Replace module-local file appenders with shared JSON stdout Logback configuration.
6. Remove compose log volume mounts and logging filename environment variables.
7. Change collector logs pipeline from `/var/log/community/*.json.log` to backend
   stdout collection.
8. Rebuild Kibana saved objects for `logs-*`, `traces-*`, and correlation pivots.
9. Update handbook operations, local development, system design, and deployment docs.
10. Delete obsolete file-volume tests and add stdout/trace-correlation tests.

## Testing Strategy

Unit tests:

- OTel trace id extraction and response trace id filling.
- Logging event writers preserve `event.category`, `event.action`, and
  `event.outcome`.
- Redaction rules for URLs, headers, SQL, Redis keys, Kafka payloads, and object
  storage identifiers.
- Outbox and Kafka traceparent persistence and propagation.

Integration tests:

- Servlet request returns a `traceId` that appears in captured JSON logs.
- WebFlux request returns a `traceId` that appears in captured JSON logs.
- Kafka consumer logs use the producer traceparent when processing a message.
- Outbox worker opens or restores the correct trace context.
- Compose-rendered backend services no longer mount `/var/log/community` for app logs.
- Collector config accepts stdout log collection and OTLP traces / metrics.

End-to-end local test:

1. Start single topology with observability enabled.
2. Send a request through `community-gateway`.
3. Capture `traceId` from the response body or `traceparent` response header.
4. Assert Elasticsearch has at least one document in `traces-*` for that `trace.id`.
5. Assert Elasticsearch has at least one document in `logs-*` for that `trace.id`
   after triggering a semantic or threshold log event.

## Documentation Updates

Implementation must update:

- `docs/handbook/operations.md`
- `docs/handbook/local-development.md`
- `docs/handbook/system-design.md`
- `docs/handbook/architecture.md` if trace shared-infrastructure boundaries change
- `deploy/README.md`
- `deploy/.env.single.example`
- `deploy/.env.cluster.example`
- `deploy/observability/kibana/README.md`
- `deploy/observability/kibana/saved-objects.ndjson`

The 2026-05-13 runtime logging spec should remain as historical context, but its
file-volume transport requirements must not be treated as the future target.

## Acceptance Criteria

- A frontend-visible `traceId` is always an OTel trace id for instrumented backend
  requests.
- The same `traceId` can query both `traces-*` and `logs-*`.
- Backend compose services no longer require `/var/log/community` application log
  volumes.
- Backend JSON logs remain visible through `docker compose logs`.
- Collector restart does not prevent backend services from starting or writing stdout.
- Slow request, slow SQL, Hikari pressure, Kafka lag/rebalance, job failure, outbox
  dead/retry, and JVM pressure logs keep stable structured fields.
- No accepted test fixture or sample log contains secrets, request bodies, SQL bind
  values, Redis keys, Kafka payloads, object keys, cookies, or JWTs.
- Documentation and Kibana saved objects describe stdout logs and OTel trace
  correlation, not the old file-volume path.

## Risks And Mitigations

- Docker stdout collection can collect too much infra noise.
  Mitigation: filter backend containers by compose labels or container names, and put
  infra logs in a separate dataset if collected.
- Trace correlation for reactive logging can be lost if log correlation only depends
  on ThreadLocal MDC.
  Mitigation: test WebFlux correlation explicitly and prefer OTel-aware log
  correlation over repository-owned ThreadLocal state.
- Direct OTLP log export may be tempting but can add application-side backpressure.
  Mitigation: keep stdout collection as the default phase 1 path; evaluate direct OTLP
  logs later behind an explicit profile.
- Removing file appenders breaks existing runbooks.
  Mitigation: update handbook, saved searches, and deployment checks in the same
  implementation plan.

## Future Work

- Evaluate browser OTel instrumentation if frontend spans become useful.
- Evaluate direct OTLP logs from Logback after stdout collection is stable.
- Add collector-side sampling or tail sampling if trace volume becomes high.
- Add alerting only after fields and dashboards stabilize.
