# Community Runtime Observability Logs Design

Date: 2026-05-13

## Status

Draft. This spec defines the target shape for adding non-business runtime and infrastructure logs to the existing local observability path.

## Context

The current local observability path is centered on application JSON logs and OpenTelemetry data:

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

The EDOT collector currently reads only:

```text
/var/log/community/*.json.log
```

The Java services set `COMMUNITY_LOGGING_DIR=/var/log/community` and `COMMUNITY_LOGGING_FILE_NAME=...json.log` in compose. `community-app`, `community-gateway`, `im-core`, and `im-realtime` have `logback-spring.xml` file appenders that can write these files when the `volume-log-export` profile is active.

The observability gap is that important non-business runtime signals are either only available as metrics, only visible through `docker logs`, or not recorded at all as structured logs. During incidents this creates blind spots: Kibana can show application events, but not enough context about JVM pressure, thread pool saturation, connection pool exhaustion, database latency, Kafka consumer state, Redis latency, HTTP access behavior outside gateway, or infra component failures.

## Problem Statement

The platform needs a consistent, queryable log trail for operational diagnosis. Today, engineers often need to combine Kibana logs, Prometheus-style metrics, actuator output, container logs, and manual shell commands to answer basic runtime questions:

- Was the JVM under memory pressure when errors started?
- Did GC pauses or heap growth precede request latency?
- Was a scheduler delayed because its executor was saturated?
- Did Hikari exhaust connections or wait on database acquisition?
- Were Redis, Kafka, or Elasticsearch clients reconnecting or timing out?
- Did an HTTP endpoint become slow before downstream failures appeared?
- Did XXL-Job write useful executor logs outside the collected log path?
- Did an infra container emit errors that never reached Elastic?

Metrics remain the right primary source for time-series dashboards and alerts, but they do not replace structured operational log events. Logs should capture discrete runtime state changes, threshold crossings, slow operations, and failure context with stable fields.

## Goals

- Add structured runtime logs for JVM, GC, memory, threads, thread pools, scheduler lag, HTTP access, database, Redis, Kafka, Elasticsearch, object storage, and XXL-Job.
- Preserve the current Elastic / EDOT / shared-volume architecture for local compose.
- Keep metrics and logs complementary: metrics for continuous values, logs for events and threshold crossings.
- Standardize operational fields so Kibana searches do not depend on free-form messages.
- Ensure every Java backend deployable that mounts `/var/log/community` actually writes JSON logs there.
- Keep runtime logging in infrastructure/shared modules, not in domain code.
- Avoid logging secrets, SQL bind values, JWTs, cookies, object-store credentials, request bodies, or user-generated content.
- Keep logging overhead bounded and configurable.
- Update handbook documentation so operations runbooks reflect the new log categories and queries.

## Non-Goals

- Do not replace OpenTelemetry traces or Micrometer metrics.
- Do not build a full alerting system in this spec.
- Do not introduce Grafana, Loki, Prometheus, or Alertmanager back into the default local stack.
- Do not log every SQL statement or every Redis command by default.
- Do not log high-cardinality labels such as raw user IDs, full URLs with query strings, object keys, Kafka message payloads, or Redis keys unless explicitly redacted or bucketed.
- Do not add business-domain logging rules here. Business events continue to be owned by their domain/application code.

## Current Gaps

### JVM and GC

Current JVM flags choose G1 and memory ratios, but do not enable GC file logging. There is no structured startup event that records JVM version, container memory, heap settings, active processors, timezone, default charset, or important JVM flags.

Expected additions:

- JVM startup summary.
- Periodic JVM memory pressure event.
- GC pause threshold events.
- Optional JVM unified GC log file collection.
- JVM shutdown reason when observable by Spring lifecycle hooks.

### Thread Pools and Scheduler

Spring scheduler and application executors are not logged as runtime resources. Logs include a thread name, but there is no event for saturation, queue depth, rejection, long-running scheduled tasks, or scheduler lag.

Expected additions:

- Named executor inventory at startup.
- Periodic executor saturation events for `ThreadPoolTaskExecutor`, `ThreadPoolTaskScheduler`, Kafka listener containers where accessible, and custom executors.
- Scheduled task run duration and lag threshold events for local scheduler jobs.
- Rejected execution events where a bounded executor is used.

### Database, Hikari, and MyBatis

Hikari pool metrics may be available through Micrometer, but there are no structured logs for pool exhaustion, connection acquisition latency, slow transactions, slow repository calls, or database connectivity state changes.

Expected additions:

- DataSource startup summary with sanitized JDBC URL host/database and pool sizing.
- Hikari pool pressure threshold events.
- Connection acquisition slow event.
- Slow SQL or slow data-access operation event without SQL bind values.
- Database connectivity state-change events from health checks or client exceptions.

### Redis

Redis usage failures surface only through framework logs or business exception paths. There are no structured client events for reconnects, command latency threshold, topology changes, or fallback behavior.

Expected additions:

- Redis client startup summary with sanitized host/cluster mode.
- Redis connectivity state-change events.
- Slow Redis operation event where instrumentation can wrap project-owned Redis adapters.
- Lettuce reconnect / command timeout logger level review.

### Kafka

`im-core` has structured DLQ recovery logging, but the wider Kafka runtime is not consistently covered. Consumer lag, rebalance, assignment, commit failures, producer send latency, and DLQ behavior are not standardized across services.

Expected additions:

- Kafka producer and consumer startup summary.
- Consumer rebalance and partition assignment events.
- Consumer processing failure and DLQ events with stable fields.
- Producer send failure and slow send events.
- Periodic consumer lag threshold events if lag can be obtained cheaply.

### HTTP and WebSocket Access

`community-gateway` has an access log filter. Other servlet services do not have a uniform access log. Tomcat / Netty native access logs are not enabled in the collected JSON path. WebSocket open/close/error events are partially logged in IM runtime code but not standardized as operational access events across gateway and realtime worker.

Expected additions:

- Shared servlet access log filter for backend services, excluding health and static noise by default.
- WebFlux access log parity for gateway-style services.
- Request duration threshold event with status, route pattern, method, and sanitized path template.
- WebSocket connect, close, error, and backpressure events with stable fields.

### XXL-Job

XXL-Job executor log path defaults to `/tmp/community-app/xxl-job`, which is outside `/var/log/community`. `XxlJobHelper.log(...)` output is therefore not in the current filelog receiver path.

Expected additions:

- Move or mirror XXL-Job executor logs under `/var/log/community/xxl-job`.
- Add collector include rules for job logs or bridge job lifecycle events into the JSON app log.
- Record job start, success, failure, duration, shard info, and reason code.

### OSS and Object Store

`community-oss` appears to mount `/var/log/community` and set logging env vars, but it does not currently have a visible `logback-spring.xml` file appender. Object-store client operation failures and latency are not standardized.

Expected additions:

- Ensure `community-oss` writes JSON file logs to the shared volume.
- Object-store client startup summary with sanitized endpoint and mode.
- Slow object-store operation and failure events without object keys or credentials.

### Missing File Appenders

`community-oss` and `community-im-gateway` set `COMMUNITY_LOGGING_DIR` and `COMMUNITY_LOGGING_FILE_NAME` in compose but do not have a visible module-local `logback-spring.xml`. They may only write console logs, leaving the EDOT filelog receiver blind to those services.

Expected additions:

- Introduce a shared logging configuration resource or copy the existing file appender config to every backend deployable.
- Add tests or runtime config checks that fail if a backend service with `volume-log-export` lacks a JSON file appender.

### Infrastructure Containers

MySQL, Redis, Kafka, Nacos, Garage, Nginx, Elasticsearch, Kibana, and the collector itself are not collected into Elastic logs by the current filelog path. Operators must use `deployment.sh logs` or raw Docker logs.

Expected additions:

- Phase 2 container log collection through EDOT `filelog` or Docker log receiver if acceptable.
- Separate dataset/category fields for infra logs.
- Conservative parsing and redaction for infra logs.

## Logging Model

Runtime logs must use structured fields. The message should remain human-readable, but queries must rely on fields.

Required common fields:

| Field | Meaning |
| --- | --- |
| `service.name` | Logical service name, matching OTel service name. |
| `service.version` | Build/runtime version. |
| `service.namespace` | `community` for backend services. |
| `deployment.environment` | `local-compose` by default. |
| `host.name` | Container hostname where available. |
| `event.category` | `runtime`, `database`, `cache`, `messaging`, `access`, `job`, `infra`. |
| `event.action` | Stable operation name, for example `jvm_gc_pause_threshold`. |
| `event.outcome` | `success`, `failure`, `degraded`, `threshold`, `skipped`. |
| `community.category` | Backward-compatible category. Runtime logs should set this to `runtime`, `database`, `cache`, `messaging`, `access`, `job`, or `infra`. |
| `community.action` | Backward-compatible stable action. |
| `community.outcome` | Backward-compatible outcome. |
| `trace.id` | Present when the event belongs to a request or async trace. Runtime background events may omit it. |
| `error.type` | Exception class or normalized error type when applicable. |
| `error.message` | Sanitized error message. |
| `duration.ms` | Duration for threshold events. |
| `threshold.ms` / `threshold.percent` | Configured threshold that produced the event. |

Subsystem-specific fields should be stable and low cardinality:

- JVM: `jvm.memory.area`, `jvm.memory.used.bytes`, `jvm.memory.max.bytes`, `jvm.gc.name`, `jvm.gc.pause.ms`.
- Thread pool: `executor.name`, `executor.active`, `executor.pool.size`, `executor.queue.size`, `executor.queue.remaining`, `executor.rejected.count`.
- Database: `db.system`, `db.name`, `db.operation`, `db.pool.name`, `db.pool.active`, `db.pool.idle`, `db.pool.pending`.
- Redis: `cache.system=redis`, `cache.operation`, `cache.mode`, `net.peer.name`.
- Kafka: `messaging.system=kafka`, `messaging.destination.name`, `messaging.kafka.consumer.group`, `messaging.kafka.partition`, `messaging.kafka.offset`.
- HTTP: `http.request.method`, `url.path`, `http.route`, `http.response.status_code`, `client.address`.
- Job: `job.system`, `job.name`, `job.shard.index`, `job.shard.total`.
- Infra: `infra.component`, `container.name`, `log.source`.

## Architecture

The implementation should be a shared infrastructure capability, not domain behavior.

Recommended package/module shape:

```text
backend/community-common/common-observability
  src/main/java/com/nowcoder/community/common/observability
    logging
      RuntimeLogEvent
      RuntimeLogFields
      RuntimeLogWriter
      RuntimeLoggingProperties
    jvm
      JvmStartupLogger
      JvmRuntimeSnapshotLogger
      GcPauseThresholdLogger
    executor
      ExecutorInventoryLogger
      ExecutorSaturationLogger
      SchedulerLagLogger
    http
      ServletAccessLogFilter
      WebFluxAccessLogFilter
    data
      DataSourceRuntimeLogger
      DataAccessSlowOperationLogger
    redis
      RedisRuntimeLogger
    kafka
      KafkaRuntimeLogger
    job
      JobRuntimeLogger
    autoconfig
      ObservabilityLoggingAutoConfiguration
```

Application services and domain models must not depend on this module. Backend deployables import it through Spring Boot auto-configuration. Existing domain-specific structured logs can keep their local helpers, but new runtime logs should use the shared writer and field vocabulary.

The writer should use SLF4J/Logback and MDC so events continue through the current JSON file appender. It must not write directly to Elasticsearch, EDOT, or collector APIs.

## Collection Design

### Phase 1: Backend Runtime Logs

Phase 1 keeps the current filelog path:

```text
/var/log/community/*.json.log
```

Required changes:

- Ensure all backend deployables write JSON file logs when `volume-log-export` is active.
- Add runtime structured events through shared Spring auto-configuration.
- Add GC unified logging to a separate file path only if the collector is configured to read it safely:

```text
/var/log/community/gc/*.gc.log
```

GC unified logs are useful for deep diagnosis, but they are not JSON. The preferred Phase 1 baseline is structured GC threshold events from JVM notifications or Micrometer observations. Raw GC file ingestion can be added after parser and volume controls are in place.

### Phase 2: Job and Infra Logs

Phase 2 broadens filelog collection:

```text
/var/log/community/*.json.log
/var/log/community/xxl-job/*.log
/var/log/community/infra/**/*.log
```

Required changes:

- Move XXL-Job executor logs under `/var/log/community/xxl-job`.
- Add EDOT receivers or include rules for job logs and infra logs.
- Add resource attributes that distinguish `service.namespace=community` application logs from infra logs.
- Keep infra logs in a separate Elastic data stream or index if field shape differs significantly from app JSON logs.

### Phase 3: Saved Searches and Runbooks

Add Kibana saved searches or documented KQL examples for:

- JVM memory pressure and GC threshold events.
- Executor saturation.
- Hikari pool pressure.
- Slow data access.
- Redis timeout/connectivity events.
- Kafka DLQ and consumer rebalance.
- HTTP slow requests.
- Job failures and long-running jobs.
- Infra component errors.

## Configuration

Runtime logging must be enabled by default in local compose but configurable.

Suggested properties:

```yaml
community:
  observability:
    runtime-logging:
      enabled: true
      startup-summary-enabled: true
      periodic-summary-enabled: true
      periodic-summary-interval: 60s
      jvm:
        enabled: true
        memory-threshold-percent: 85
        gc-pause-threshold-ms: 200
      executors:
        enabled: true
        saturation-threshold-percent: 85
        scheduler-lag-threshold-ms: 1000
      http:
        access-log-enabled: true
        slow-request-threshold-ms: 1000
        exclude-paths:
          - /actuator/health
          - /actuator/info
      datasource:
        enabled: true
        acquisition-threshold-ms: 200
        pool-pending-threshold: 1
      redis:
        enabled: true
        operation-threshold-ms: 100
      kafka:
        enabled: true
        send-threshold-ms: 250
        consumer-lag-threshold: 1000
      job:
        enabled: true
```

Service-specific thresholds may override shared defaults through environment variables in compose.

## Redaction and Cardinality Rules

Runtime logging must follow these rules:

- Do not log request bodies.
- Do not log cookies, JWTs, refresh tokens, access tokens, passwords, API keys, or object-store secrets.
- Do not log SQL bind values.
- Do not log Redis key values by default; log key family or operation type only.
- Do not log Kafka payloads.
- Do not log object-store keys unless replaced by a stable low-cardinality usage type.
- Prefer route templates over raw URLs.
- Limit error messages to sanitized framework messages. Use error class and reason code for queries.
- Do not emit periodic logs faster than once per minute per service unless a threshold is crossed.

## Error Handling

Observability logging must be best-effort. A logging failure must not fail business requests, scheduled jobs, Kafka listeners, or startup.

If runtime instrumentation cannot inspect a subsystem, it should log a one-time `runtime_instrumentation_skipped` event at startup with the reason, then stay silent.

If a log event would exceed a safe size, it should be truncated before reaching Logback. Truncation should set `event.truncated=true`.

## Testing Strategy

Unit tests:

- Verify structured runtime event writers set required MDC fields.
- Verify redaction rules for URLs, headers, SQL, Redis keys, and error messages.
- Verify threshold logic emits events only when configured limits are exceeded.
- Verify missing optional beans do not fail auto-configuration.

Slice/integration tests:

- Start each backend module with `volume-log-export` and assert a JSON file appender is active.
- Verify `community-oss` and `community-im-gateway` write expected JSON file logs.
- Verify access log filters exclude health endpoints and include slow request fields.
- Verify scheduler/job events include duration and outcome.

Deployment tests:

- Extend existing deploy tests to assert the rendered compose config includes logging volume, filename, and runtime logging env vars for every backend service.
- Validate EDOT collector config accepts added filelog receivers.
- Run local single topology and verify Elasticsearch receives at least one runtime startup event per backend service.

Manual verification:

```bash
./deploy/deployment.sh up --topology single
curl -fsS http://localhost:12888/logs-community-default/_search
```

Kibana should be able to find:

```text
community.category : runtime
community.category : database
community.category : cache
community.category : messaging
community.category : access
community.category : job
```

## Documentation Updates

Implementation should update:

- `docs/handbook/operations.md`: observability data flow, log categories, KQL examples, troubleshooting steps.
- `docs/handbook/local-development.md`: local verification commands and observability toggles.
- `docs/handbook/system-design.md`: operational observability architecture.
- `deploy/.env.single.example` and `deploy/.env.cluster.example`: runtime logging thresholds and GC/log collection notes.
- `deploy/observability/kibana/README.md`: saved object import and query descriptions.

## Rollout Plan

1. Fix backend service JSON file logging parity, especially `community-oss` and `community-im-gateway`.
2. Add shared runtime logging field vocabulary and writer.
3. Add JVM startup and threshold events.
4. Add HTTP access and slow request events for servlet and WebFlux services.
5. Add executor, scheduler, and job lifecycle events.
6. Add database, Redis, Kafka, Elasticsearch, and object-store threshold events.
7. Move or mirror XXL-Job logs into the collected path.
8. Expand EDOT collector filelog receivers for job logs and, later, infra logs.
9. Add Kibana searches and handbook runbooks.

## Acceptance Criteria

- Every backend service that participates in local compose writes structured JSON logs to `/var/log/community`.
- A fresh local single topology produces at least one `community.category=runtime` startup event per backend service in Elasticsearch.
- GC threshold events are queryable when a pause exceeds the configured threshold.
- Slow HTTP requests, Hikari pressure, Redis timeouts, Kafka DLQ/rebalance, scheduler lag, and job failures have stable KQL-searchable fields.
- XXL-Job executor logs or equivalent lifecycle events are available through the observability path.
- Infra logs are either explicitly collected in Phase 2 or documented as out of Phase 1.
- Runtime logging can be disabled or threshold-tuned through configuration.
- No tests or sample logs expose secrets, request bodies, SQL bind values, Kafka payloads, Redis keys, or object-store credentials.

## Open Decisions

- Whether raw JVM unified GC logs should be ingested in Phase 1 or kept as local files while structured GC events go to Elastic.
- Whether infra container logs should share `logs-community-default` or use a separate Elastic index/data stream.
- Whether slow SQL should be implemented through datasource proxy instrumentation, MyBatis interceptors, Hikari metrics threshold logging, or repository-level operation timers.
- Whether Kafka consumer lag should be computed by each service or collected externally from Kafka broker/admin APIs.
