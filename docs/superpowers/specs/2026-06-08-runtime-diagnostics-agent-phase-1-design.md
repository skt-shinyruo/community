# Runtime Diagnostics Agent Phase 1 Design

## Context

Phase 1 turns the current `method-profiler-agent` into
`runtime-diagnostics-agent` and establishes a probe-based diagnostic foundation.
It includes local runtime probes that can be implemented without duplicating
large parts of OpenTelemetry dependency instrumentation.

Source blueprint:

```text
docs/superpowers/specs/2026-06-08-runtime-diagnostics-agent-blueprint-design.md
```

## Goals

- Rename the module, jar, package, deployment path, and settings to
  `runtime-diagnostics-agent`.
- Remove compatibility with `METHOD_PROFILER_*` settings.
- Introduce a generic probe framework.
- Migrate method latency profiling into a method probe.
- Add exception, thread, and JVM probes.
- Preserve existing trace correlation.
- Keep the agent disabled by default and bounded when enabled.

## Non-Goals

- Do not add HTTP, JDBC, Redis, or Kafka probes in Phase 1.
- Do not create synthetic trace ids.
- Do not collect arguments, return values, request bodies, payloads, or secrets.
- Do not add Community business dependencies.
- Do not add application-level DDD code changes.

## File And Package Changes

Replace:

```text
backend/method-profiler-agent
```

with:

```text
backend/runtime-diagnostics-agent
```

Replace package:

```text
com.nowcoder.observability.methodprofiler
```

with:

```text
com.nowcoder.observability.runtimediagnostics
```

Replace image jar path:

```text
/otel/method-profiler-agent.jar
```

with:

```text
/otel/runtime-diagnostics-agent.jar
```

Replace environment settings:

```text
METHOD_PROFILER_*
```

with:

```text
RUNTIME_DIAGNOSTICS_*
```

No old setting compatibility is required.

## Runtime Activation

The agent loads only when explicitly enabled.

Default Community settings:

```text
RUNTIME_DIAGNOSTICS_ENABLED=false
RUNTIME_DIAGNOSTICS_PROBES=method,exception,thread,jvm
RUNTIME_DIAGNOSTICS_INCLUDES=com.nowcoder.community.*
RUNTIME_DIAGNOSTICS_SAMPLE_RATE=1.0
RUNTIME_DIAGNOSTICS_MAX_EVENTS_PER_SECOND=20
RUNTIME_DIAGNOSTICS_SUMMARY_INTERVAL=60s
RUNTIME_DIAGNOSTICS_TOP_N=50
RUNTIME_DIAGNOSTICS_MAX_TRACKED_KEYS=10000
```

`backend/scripts/run-backend-service.sh` should append:

```text
-javaagent:/otel/runtime-diagnostics-agent.jar
```

only when `RUNTIME_DIAGNOSTICS_ENABLED=true`.

## Core Framework

Introduce a small probe framework:

```text
core/Probe.java
core/ProbeRegistry.java
core/DiagnosticRuntime.java
core/DiagnosticEvent.java
core/DiagnosticEventLogger.java
config/DiagnosticsConfig.java
config/DiagnosticsConfigLoader.java
trace/TraceContextReader.java
```

`Probe` should expose a simple lifecycle:

```text
name()
start(context)
stop()
```

`ProbeRegistry` starts enabled probes independently. If one probe fails, the
registry disables that probe and continues starting the rest.

`DiagnosticRuntime` owns shared bounded queues, aggregators, rate limiters, and
hot-path helpers. Instrumentation advice should call into this runtime instead
of doing logging or expensive formatting.

## Method Probe

The existing method latency behavior is migrated into:

```text
probes/method
```

Events:

```text
method_latency_summary
method_slow_call
```

Fields:

```text
diagnostic.probe=method
method.class
method.name
method.signature.hash
method.invocation.count
duration.avg.ms
duration.max.ms
duration.p95.ms
duration.ms
threshold.ms
```

The method probe keeps the current safety posture:

- hard excludes for JDK, logging, Byte Buddy, and agent packages
- constructors, native methods, abstract methods, synthetic methods, bridge
  methods, and type initializers excluded
- bounded tracked method keys
- slow-call rate limiting
- summary top-N ordering

## Exception Probe

The exception probe records exceptions observed on instrumented method exit.

Event:

```text
exception_observed
```

Fields:

```text
diagnostic.probe=exception
exception.type
method.class
method.name
method.signature.hash
```

The probe must not log raw exception messages or stack traces by default. It may
record a bounded top frame class/method only if it passes the same sanitization
rules. Full stack traces are out of scope for Phase 1.

Exception events are sampled and rate limited separately from method slow-call
events.

## Thread Probe

The thread probe is scheduled, not method-hot-path instrumentation.

Events:

```text
thread_snapshot
thread_deadlock_detected
lock_wait_snapshot
```

Fields:

```text
diagnostic.probe=thread
thread.count
thread.state.runnable
thread.state.blocked
thread.state.waiting
thread.state.timed_waiting
thread.deadlock.count
thread.lock.wait.count
```

Detailed thread names should be optional because they can be high-cardinality.
Default output should aggregate by state and deadlock count. Deadlock events may
include sanitized thread names and lock class names when available.

## JVM Probe

The JVM probe is scheduled and uses standard Java management APIs.

Events:

```text
jvm_runtime_summary
gc_summary
memory_pressure_snapshot
```

Fields:

```text
diagnostic.probe=jvm
jvm.uptime.ms
jvm.available.processors
jvm.memory.heap.used.bytes
jvm.memory.heap.max.bytes
jvm.memory.nonheap.used.bytes
jvm.thread.count
jvm.class.loaded.count
jvm.gc.collection.count
jvm.gc.collection.time.ms
```

The probe should avoid duplicate high-frequency metrics behavior. It emits a
diagnostic summary at the configured interval and pressure snapshots when memory
or GC thresholds are crossed.

## Trace Correlation

Trace lookup order:

1. Current OpenTelemetry span context by reflection.
2. SLF4J MDC `trace.id` and `span.id` by reflection.
3. No trace fields.

The agent must not generate trace ids. It only enriches active traces and logs.

## Event Output

All Phase 1 events use:

```text
event.category=runtime_diagnostics
diagnostic.agent.name=runtime-diagnostics-agent
diagnostic.probe=<probe>
```

Output target:

```text
SLF4J structured fields when available
stderr JSON-compatible fallback otherwise
```

## Failure Handling

- `premain` catches startup failures and disables the agent.
- Probe startup failures disable only the failed probe.
- Advice failures are swallowed and counted.
- Queue overflow increments a dropped-event counter.
- Summary reporting failures do not stop probe collection.

## Tests

Unit tests:

- config defaults and overrides
- old `METHOD_PROFILER_*` settings are ignored
- probe registry starts healthy probes after one fails
- method matching and hard excludes
- method aggregation and top-N output
- exception event sanitization
- thread snapshot aggregation
- JVM summary collection
- trace reader fallback order
- logger output shape
- rate limiting and queue overflow

Integration tests:

- forked JVM starts with `-javaagent`
- method summary events are emitted
- exception events are emitted without raw messages
- thread and JVM scheduled events are emitted
- target application exceptions still propagate unchanged

Deployment tests:

- backend image copies `/otel/runtime-diagnostics-agent.jar`
- startup script appends the new jar only when enabled
- compose/env examples expose `RUNTIME_DIAGNOSTICS_*`
- old `METHOD_PROFILER_*` names are removed from docs and deployment files

## Documentation

Update documentation to describe runtime diagnostics usage:

- enabling a short diagnostic run
- selecting probes
- choosing include/exclude patterns
- querying `event.category=runtime_diagnostics`
- interpreting method, exception, thread, and JVM events
- safety rules and forbidden payload data

## Rollout

1. Rename module and deployment identity.
2. Introduce core probe framework.
3. Migrate method probe.
4. Add exception probe.
5. Add thread and JVM probes.
6. Update deployment wiring and docs.
7. Run module tests and deployment render tests.
