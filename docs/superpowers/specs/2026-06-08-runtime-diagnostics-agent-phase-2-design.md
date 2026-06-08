# Runtime Diagnostics Agent Phase 2 Design

## Context

Phase 2 extends `runtime-diagnostics-agent` from local runtime diagnostics into
external dependency diagnostics. It adds HTTP, JDBC/MyBatis, Redis, and Kafka
summary probes while preserving the safety model from Phase 1.

Source blueprint:

```text
docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-blueprint-design.md
```

Phase 1 dependency:

```text
docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-phase-1-design.md
```

## Goals

- Add dependency-focused probes for HTTP, JDBC/MyBatis, Redis, and Kafka.
- Emit bounded call summaries and slow-call events.
- Preserve existing OTel/MDC trace correlation.
- Avoid duplicating OTel trace roots.
- Avoid sensitive payload leakage.
- Provide operator-oriented Kibana query guidance.

## Non-Goals

- Do not replace OpenTelemetry HTTP, database, Redis, or Kafka instrumentation.
- Do not collect request/response bodies.
- Do not collect SQL bind values.
- Do not collect Redis keys or values.
- Do not collect Kafka payloads.
- Do not infer business root cause.
- Do not enable dependency probes by default in production-like settings without
  explicit operator choice.

## Probe Defaults

Dependency probes should be opt-in, even when the agent is enabled.

Example:

```text
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm,http,jdbc,redis,kafka
RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS=500
RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS=200
RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS=100
RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS=500
```

Each dependency probe gets independent sampling and rate limiting when needed:

```text
RUNTIME_DIAGNOSTICS_HTTP_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_JDBC_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_REDIS_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_KAFKA_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_HTTP_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_JDBC_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_REDIS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_KAFKA_MAX_EVENTS_PER_SECOND=20
```

## OTel Coexistence

Phase 2 must not create duplicate trace roots or compete with OTel span
generation.

Rules:

- Read existing OTel/MDC context only.
- Prefer summary events over per-call event floods.
- Keep dependency events as diagnostic log events, not tracing spans.
- Allow disabling a dependency probe if OTel data is already sufficient.
- Document when the agent adds value beyond OTel: high-level summaries, bounded
  slow-call diagnostics, and local runtime correlation with method/thread/JVM
  events.

## HTTP Probe

Events:

```text
http_call_summary
http_slow_call
```

Allowed fields:

```text
diagnostic.probe=http
http.direction=<inbound|outbound>
http.method
http.route
http.status_code
network.peer.name.hash
duration.ms
threshold.ms
```

Forbidden fields:

- request body
- response body
- query string by default
- authorization headers
- cookies
- full arbitrary headers

Instrumentation should target common Java HTTP client/server surfaces only after
confirming they do not conflict with existing OTel behavior. Route templates are
allowed only when safely available; raw URLs should be avoided or sanitized.

## JDBC/MyBatis Probe

Events:

```text
jdbc_call_summary
jdbc_slow_call
```

Allowed fields:

```text
diagnostic.probe=jdbc
db.system
db.operation
db.statement.hash
db.datasource.hash
duration.ms
threshold.ms
```

Forbidden fields:

- SQL bind values
- full SQL text by default
- user-provided SQL fragments
- database credentials

The probe may derive a normalized operation kind such as `select`, `insert`,
`update`, or `delete`. If statement hashing is used, it should be stable for a
sanitized normalized statement and must not include bind values.

MyBatis support should stay at the infrastructure boundary and should not import
Community mapper or dataobject classes.

## Redis Probe

Events:

```text
redis_call_summary
redis_slow_call
```

Allowed fields:

```text
diagnostic.probe=redis
redis.command
redis.keyspace.hash
duration.ms
threshold.ms
```

Forbidden fields:

- Redis key by default
- Redis value
- serialized payloads
- Lua script contents

The probe should prefer command-level summaries. Keyspace details are optional
and must be hash-based or explicitly allowlisted.

## Kafka Probe

Events:

```text
kafka_produce_summary
kafka_consume_summary
kafka_slow_call
```

Allowed fields:

```text
diagnostic.probe=kafka
messaging.operation=<produce|consume>
messaging.destination.name.hash
messaging.kafka.partition
duration.ms
threshold.ms
```

Forbidden fields:

- Kafka message payload
- arbitrary message headers
- keys by default
- user identity values carried in messages

Topic names may be emitted only if the operator explicitly allows topic names as
non-sensitive. Otherwise use stable hashes.

## Aggregation

Dependency probes should support both slow-call events and interval summaries.

Summary fields:

```text
call.count
duration.avg.ms
duration.max.ms
duration.p95.ms
error.count
```

Cardinality controls:

- route templates instead of raw URLs
- statement hashes instead of SQL text
- topic hashes instead of payload-derived fields
- command names instead of Redis keys
- top-N summaries
- overflow counters

## Failure Handling

- A dependency probe failure disables only that probe.
- Instrumentation advice must never throw into application code.
- Missing optional libraries should skip the related instrumentation.
- Unsupported library versions should be reported once and then ignored.
- Event serialization failure should drop the event and increment a bounded
  counter.

## Tests

Shared tests:

- probe enable/disable behavior
- sampling and rate limiting
- event shape
- trace field preservation
- sensitive payload exclusion
- overflow behavior

HTTP tests:

- request bodies and headers are not logged
- route or URL sanitization works
- slow-call thresholds produce bounded events

JDBC/MyBatis tests:

- bind values are not logged
- statement hash is stable
- operation kind is extracted safely

Redis tests:

- keys and values are not logged by default
- command summaries are emitted

Kafka tests:

- payloads and arbitrary headers are not logged
- produce and consume summaries are distinct

Coexistence tests:

- OTel trace context is preserved when present
- no synthetic trace id is generated
- enabling dependency probes does not prevent the app from starting if target
  libraries are absent

## Documentation

Add an operator guide for dependency troubleshooting:

- slow outbound HTTP investigation
- slow SQL investigation without SQL parameter leakage
- Redis latency spikes
- Kafka producer/consumer lag-adjacent latency signals
- joining dependency events with method, exception, thread, and JVM events by
  `trace.id` when available
- deciding whether to use OTel traces, runtime diagnostics events, or both

## Rollout

1. Add HTTP probe behind an explicit setting.
2. Add JDBC/MyBatis probe behind an explicit setting.
3. Add Redis probe behind an explicit setting.
4. Add Kafka probe behind an explicit setting.
5. Add Kibana query docs and payload-safety tests for each probe.
6. Evaluate event volume and cardinality before recommending broader use.
