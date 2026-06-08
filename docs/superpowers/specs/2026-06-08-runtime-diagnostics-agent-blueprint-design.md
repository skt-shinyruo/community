# Runtime Diagnostics Agent Blueprint Design

## Context

`backend/method-profiler-agent` currently provides a generic JVM method latency
profiler. It is useful for slow-method diagnostics, but the name and design
boundary are too narrow for the desired troubleshooting role.

The target is a broader diagnostic Java agent that helps operators investigate
runtime problems across execution latency, exceptions, threads, JVM pressure, and
external dependency calls. This blueprint supersedes the future direction of
`docs/superpowers/specs/2026-06-07-generic-method-profiler-agent-design.md`.
The existing method profiler becomes one probe inside the new agent.

## Goals

- Rename the diagnostic module to `runtime-diagnostics-agent`.
- Treat method latency profiling as one probe, not the product boundary.
- Provide a generic JVM `-javaagent` that can be used outside Community.
- Capture bounded, structured diagnostic events for problem tracking.
- Reuse the existing stdout -> EDOT Collector -> Elasticsearch -> Kibana path.
- Preserve existing OpenTelemetry and MDC trace correlation when available.
- Keep all probes disabled by default or explicitly bounded by safe defaults.
- Split implementation into Phase 1 and Phase 2 specs.

## Non-Goals

- Do not replace the OpenTelemetry Java Agent.
- Do not create a separate distributed trace system.
- Do not perform business root-cause judgment or domain-specific diagnosis.
- Do not collect method arguments, return values, request bodies, SQL bind
  values, Redis key/value data, Kafka payloads, JWTs, cookies, or secrets.
- Do not make full runtime diagnostics a default production behavior.
- Do not depend on Spring or Community business classes.
- Do not preserve backward compatibility for the old `method-profiler-agent`
  module name, jar name, package name, or `METHOD_PROFILER_*` settings.

## Module Identity

Target module:

```text
backend/runtime-diagnostics-agent
```

Maven artifact:

```text
runtime-diagnostics-agent
```

Agent jar path in backend service images:

```text
/otel/runtime-diagnostics-agent.jar
```

Base Java package:

```text
com.nowcoder.observability.runtimediagnostics
```

The old `backend/method-profiler-agent` module is removed or replaced during
Phase 1. No compatibility wrapper is required.

## Architecture

The agent remains a generic JVM `-javaagent` with Byte Buddy based
instrumentation where bytecode instrumentation is needed.

Suggested package shape:

```text
com.nowcoder.observability.runtimediagnostics
  RuntimeDiagnosticsAgent
  config
  core
    DiagnosticRuntime
    DiagnosticEvent
    DiagnosticEventLogger
    Probe
    ProbeRegistry
  trace
    TraceContextReader
  match
    ClassMatcher
    MethodMatcher
  probes
    method
    exception
    thread
    jvm
    http
    jdbc
    redis
    kafka
```

Core responsibilities:

- `RuntimeDiagnosticsAgent`: `premain` entry point, config load, probe startup.
- `ProbeRegistry`: resolves enabled probes and starts/stops them safely.
- `Probe`: common lifecycle contract for instrumentation and scheduled probes.
- `DiagnosticRuntime`: hot-path safe runtime holder for aggregators and queues.
- `DiagnosticEvent`: stable structured event model.
- `DiagnosticEventLogger`: emits JSON-compatible fields to SLF4J when available
  and stderr as a fallback.
- `TraceContextReader`: reads current OTel span context and MDC fields by
  reflection when available.

## Probe Map

Phase 1 probes:

```text
method      method latency summary and slow-call events
exception   uncaught or observed exception events from instrumented methods
thread      thread snapshots, deadlock detection, lock-wait summaries
jvm         runtime, GC, memory, CPU, class loading, and thread-count summaries
```

Phase 2 probes:

```text
http        inbound/outbound HTTP call summaries where not already enough via OTel
jdbc        JDBC/MyBatis call summaries without SQL bind values
redis       Redis call summaries without key/value payloads
kafka       Kafka producer/consumer summaries without message payloads
```

## Event Model

All diagnostic events use a shared field family.

Required base fields:

```text
event.category=runtime_diagnostics
event.action=<stable action>
event.outcome=<success|error|threshold|snapshot|dropped>
diagnostic.agent.name=runtime-diagnostics-agent
diagnostic.probe=<method|exception|thread|jvm|http|jdbc|redis|kafka>
```

Correlation fields are best effort:

```text
service.name=<service name when available>
trace.id=<existing trace id when available>
span.id=<existing span id when available>
thread.name=<current thread when useful and safe>
```

The agent must not generate synthetic trace ids. If no OTel or MDC trace exists,
the event is emitted without trace fields.

## Configuration Model

Global settings use `RUNTIME_DIAGNOSTICS_*`.

```text
RUNTIME_DIAGNOSTICS_ENABLED=false
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_EXCLUDES=
RUNTIME_DIAGNOSTICS_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_TOP_N=50
RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS=10000
```

Probe-specific settings use the same prefix:

```text
RUNTIME_DIAGNOSTICS_METHOD_SLOW_THRESHOLD_MS=100
RUNTIME_DIAGNOSTICS_THREAD_SNAPSHOT_INTERVAL=60s
RUNTIME_DIAGNOSTICS_JVM_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_HTTP_SLOW_THRESHOLD_MS=500
RUNTIME_DIAGNOSTICS_JDBC_SLOW_THRESHOLD_MS=200
RUNTIME_DIAGNOSTICS_REDIS_SLOW_THRESHOLD_MS=100
RUNTIME_DIAGNOSTICS_KAFKA_SLOW_THRESHOLD_MS=500
```

Agent args may mirror these settings for non-container usage, but environment
variables are the primary Community deployment surface.

## Data Safety

The agent records diagnostic metadata only.

Forbidden data:

- method arguments and return values
- object identities
- raw exception messages when they may contain user data
- request bodies
- response bodies
- full HTTP headers
- cookies, JWTs, authorization headers, and sessions
- SQL bind values
- Redis keys and values
- Kafka payloads and headers that may carry business data
- OSS object keys or signed URLs

Allowed data examples:

- class and method names after include/exclude filtering
- stable signature hashes
- duration and count metrics
- exception class names
- thread state and lock-owner summaries
- JDBC operation kind and sanitized datasource identity
- HTTP method and route template when safe
- Kafka topic hash or configured safe topic name if explicitly allowed

## Safety And Failure Handling

- The agent is disabled by default.
- Agent startup failure disables the agent and must not stop the JVM unless the
  JVM rejects the jar before `premain`.
- Probe startup failure disables only that probe.
- Runtime probe failure must never throw into business code.
- Hot-path code must avoid direct logging.
- Bounded queues, tracked-key caps, and rate limiters are mandatory.
- Overflow should be counted or emitted as a bounded dropped-event summary.
- The agent must hard-exclude JDK, logging, Byte Buddy, and its own packages.

## Phase Boundary

Phase 1 establishes the renamed agent and safe local runtime probes:

- module rename
- probe framework
- event schema
- method probe migration
- exception probe
- thread probe
- JVM probe
- deployment and docs

Phase 2 adds dependency probes:

- HTTP
- JDBC/MyBatis
- Redis
- Kafka
- OTel overlap controls
- dependency-focused Kibana troubleshooting guides

## Testing Strategy

All phases should include:

- config parsing tests
- probe enable/disable tests
- rate limiting and sampling tests
- sensitive-field leakage tests
- forked JVM `-javaagent` integration tests
- deployment render or startup-script tests

Phase 2 also needs coexistence tests with OTel-style trace context and
dependency probe tests proving payload data is not recorded.

## Documentation Strategy

Documentation should describe the new agent as a runtime diagnostics tool, not a
method profiler.

Update targets:

- `deploy/README.md`
- `docs/handbook/operations.md`
- deployment environment examples
- startup script comments
- Kibana query examples

## Rollout

1. Complete Phase 1 and remove the old method-profiler identity.
2. Run the agent only during diagnostic sessions.
3. Validate event volume and payload safety in local compose.
4. Add Phase 2 dependency probes one family at a time.
5. Keep all high-cardinality or high-noise dimensions behind explicit settings.
