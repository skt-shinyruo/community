# Generic JVM Method Profiler Agent Design

## Context

The repository already has runtime observability through
`community-common-observability`, Logback JSON stdout, EDOT Collector,
Elasticsearch, Kibana, and OTel Java Agent tracing. That stack covers HTTP
request latency, MyBatis slow SQL, outbound HTTP, Redis, Kafka, OSS, scheduled
jobs, JVM/runtime pressure, and trace correlation.

It does not currently provide JVM-wide Java method latency statistics. Existing
code has no global method AOP, `@Timed`, `@Observed`, or method profiler hook.

The requested capability is a generic Java agent that can be used beyond this
Community application while still integrating cleanly with the current local
observability stack.

## Goals

- Provide a generic JVM method latency profiler agent, not a Community-specific
  runtime component.
- Support broad Java method instrumentation through a `-javaagent` mechanism.
- Keep the agent disabled by default and safe to load only during diagnostic
  sessions.
- Emit bounded method latency summaries and slow-call events that can flow
  through the existing JSON stdout and Elastic/Kibana path.
- Preserve existing trace correlation when a method executes inside an existing
  trace context.
- Avoid business-domain dependencies and keep Community integration limited to
  deployment/runtime wiring.

## Non-Goals

- Do not replace OTel distributed tracing.
- Do not make JVM-wide method profiling a default production behavior.
- Do not log method arguments, return values, request bodies, SQL bind values,
  cookies, JWTs, Redis keys, Kafka payloads, or object keys.
- Do not instrument JDK internals, logging frameworks, Byte Buddy internals, or
  the profiler agent itself.
- Do not require Spring or Community classes for the generic agent to run.

## Module Shape

Add a standalone Maven module:

```text
backend/method-profiler-agent
```

The package name should be generic:

```text
com.nowcoder.observability.methodprofiler
```

The module builds a Java agent jar with a `Premain-Class` manifest entry. The
agent should shade its implementation dependencies where needed so it can run in
non-Community JVM applications without relying on application classpath content.

Community services consume the agent only through deployment/runtime wiring.
Business modules must not import agent classes.

## Runtime Activation

The agent is loaded only when explicitly enabled.

Generic runtime environment:

```text
METHOD_PROFILER_ENABLED=false
METHOD_PROFILER_INCLUDES=*
METHOD_PROFILER_EXCLUDES=
METHOD_PROFILER_SLOW_THRESHOLD_MS=100
METHOD_PROFILER_SUMMARY_INTERVAL=60s
METHOD_PROFILER_TOP_N=50
METHOD_PROFILER_SAMPLE_RATE=1.0
METHOD_PROFILER_MAX_EVENTS_PER_SECOND=20
```

Community deployment should keep a conservative default include:

```text
METHOD_PROFILER_ENABLED=false
METHOD_PROFILER_INCLUDES=com.nowcoder.community.*
```

`backend/scripts/run-backend-service.sh` should append the profiler agent
`-javaagent` only when `METHOD_PROFILER_ENABLED=true`. This can coexist with the
existing OTel Java Agent because the JVM supports multiple `-javaagent`
arguments.

## Instrumentation Strategy

Use Byte Buddy agent instrumentation for method enter/exit timing.

The first implementation should instrument ordinary Java methods matched by the
configured include/exclude rules. Constructors, native methods, abstract
methods, synthetic bridge methods, class initializers, and agent/logging
packages are out of scope for the first implementation.

The instrumentation advice should:

1. Capture `System.nanoTime()` on method entry.
2. Compute elapsed time on normal or exceptional exit.
3. Submit a sanitized method key and duration to an in-memory aggregator.
4. Avoid allocation-heavy work on the hot path.
5. Never throw into the instrumented application method.

## Matching Rules

The agent is generic, but safety exclusions are mandatory.

Hard excludes:

```text
java.*
javax.*
jakarta.*
sun.*
jdk.*
org.slf4j.*
ch.qos.logback.*
net.bytebuddy.*
com.nowcoder.observability.methodprofiler.*
```

User `METHOD_PROFILER_EXCLUDES` adds more exclusions but cannot remove hard
exclusions.

`METHOD_PROFILER_INCLUDES=*` means "eligible application/library classes after
hard exclusions", not "every class in the JVM".

Method identity fields:

```text
method.class
method.name
method.signature.hash
```

The hash should be stable for class name, method name, parameter type names, and
return type name. Raw signatures can be high-cardinality and should not be
logged by default.

## Aggregation Model

The agent should aggregate method latency in memory and emit periodic summaries.

Required summary metrics per method:

```text
count
duration.avg.ms
duration.max.ms
duration.p95.ms
```

The summary must emit only the top N methods ranked by `duration.max.ms`
descending. Ties are ordered by `method.signature.hash` ascending. The default
top N is 50.

The first implementation uses a bounded bucketed histogram for percentile
estimation. Exact percentile accuracy is less important than bounded memory and
predictable overhead.

The aggregator must cap the number of tracked method keys. When the cap is
reached, it should either drop new keys or bucket them into an overflow counter.
It must not grow without bound.

## Log Events

The agent emits JSON-compatible structured log fields through the application
logging pipeline when available. If SLF4J/Logback is not available, it may fall
back to stderr with the same JSON body shape.

Summary event:

```text
event.category=method
event.action=method_latency_summary
event.outcome=success
method.class=<sanitized class>
method.name=<sanitized method>
method.signature.hash=<stable hash>
method.invocation.count=<count>
duration.avg.ms=<avg>
duration.max.ms=<max>
duration.p95.ms=<p95>
```

Slow-call event:

```text
event.category=method
event.action=method_slow_call
event.outcome=threshold
method.class=<sanitized class>
method.name=<sanitized method>
method.signature.hash=<stable hash>
duration.ms=<duration>
threshold.ms=<threshold>
```

Slow-call events must be rate limited by
`METHOD_PROFILER_MAX_EVENTS_PER_SECOND`. Summary events are the primary output;
single slow-call events are diagnostic context.

## Trace Correlation

The agent should try to preserve existing trace context without creating new
trace roots.

Preferred lookup order:

1. Current OTel span context if OpenTelemetry API is available.
2. SLF4J MDC fields `trace.id` and `span.id` if MDC is available.
3. No trace fields.

If no trace exists, the profiler must not generate a synthetic trace ID. Method
profiling should enrich active traces, not create artificial distributed traces.

## Community Integration

Community service images should copy the built profiler agent jar into a stable
path such as:

```text
/otel/method-profiler-agent.jar
```

`backend/scripts/run-backend-service.sh` should add:

```text
-javaagent:/otel/method-profiler-agent.jar
```

only when `METHOD_PROFILER_ENABLED=true`.

Compose files and env examples should expose generic `METHOD_PROFILER_*`
settings. Community defaults should remain conservative:

```text
METHOD_PROFILER_ENABLED=false
METHOD_PROFILER_INCLUDES=com.nowcoder.community.*
```

Kibana queries can use:

```text
event.category : method
event.action : method_latency_summary
trace.id : "<trace id>"
```

## Safety Requirements

- The profiler must be disabled by default.
- Instrumentation failures must not prevent service startup unless the JVM
  itself rejects the agent jar.
- Runtime profiling failures must not fail business requests, scheduled jobs,
  Kafka listeners, or WebSocket handlers.
- Hot-path instrumentation must avoid logging directly.
- Logs must not include method arguments, return values, object identities, or
  raw exception messages.
- The agent must guard against recursive profiling of its own logging and
  aggregation code.
- The agent must provide sampling, rate limiting, and tracked-method caps.

## Testing Strategy

Unit tests:

- Config parsing from agent args, system properties, and environment variables.
- Include/exclude matching and hard exclusion enforcement.
- Stable method signature hash generation.
- Aggregator count, average, max, and percentile behavior.
- Slow-call threshold behavior and rate limiting.
- No argument or return value leakage in log fields.

Agent integration tests:

- Start a small forked JVM with `-javaagent`.
- Invoke a sample class with fast, slow, and throwing methods.
- Assert summary and slow-call events are emitted.
- Assert excluded packages are not instrumented.
- Assert exceptions from target methods still propagate unchanged.

Community integration tests:

- Render compose config and verify `METHOD_PROFILER_*` env values are present.
- Verify `run-backend-service.sh` appends the profiler `-javaagent` only when
  `METHOD_PROFILER_ENABLED=true`.
- Verify OTel and profiler agents can be configured together in the final Java
  command.

## Rollout

1. Add the generic agent module with tests.
2. Add Docker image and startup-script wiring without enabling it by default.
3. Add compose/env-example documentation.
4. Add operations documentation for Kibana queries and safe diagnostic usage.
5. Optionally add saved Kibana searches for method summaries after the event
   shape is stable.
