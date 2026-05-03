# Community Complete Tracing Design

## Goal

Support complete end-to-end tracing for the project when the observability overlay is enabled, while keeping normal local startup lightweight.

The target startup behavior is:

- Plain local/container startup keeps `OTEL_ENABLED=false` by default.
- `./deploy/deployment.sh up --topology single --observability` and `./deploy/deployment.sh up --topology cluster --observability` enable OpenTelemetry for backend services by default.
- HTTP, Gateway/WebFlux, Servlet, WebSocket, Kafka producer/consumer, outbox, scheduled jobs, XXL jobs, after-commit callbacks, local async callbacks, logs, and traces share one coherent trace context model.

## Current State

The repository already has a partial observability foundation:

- `TraceIdCodec` resolves W3C `traceparent` first, then legacy `X-Trace-Id`, then generates a new 32-character lowercase hex trace id.
- Servlet and WebFlux filters resolve inbound trace headers and write response headers.
- JSON logback configs emit `trace.id`, `trace_id`, and `span_id` fields.
- Backend container images include the OpenTelemetry Java agent.
- Runtime scripts enable the agent only when `OTEL_ENABLED=true`.
- EDOT collector and Elastic/Kibana local observability overlay already exist.

The gap is that tracing is not complete by default under the observability overlay and several non-HTTP boundaries do not yet have repository-owned propagation guarantees.

## Non-Goals

- Do not make normal non-observability startup heavier.
- Do not introduce tracing dependencies into domain models or application services.
- Do not replace business idempotency keys, message request ids, or domain event ids with trace ids.
- Do not hand-instrument every business method with OpenTelemetry spans in this phase.
- Do not require Elastic or the collector to be healthy for business services to process traffic.

## Architecture

Use OpenTelemetry Java agent as the primary tracing mechanism, and add project-level trace bridge code only where automatic instrumentation is insufficient or where legacy compatibility requires it.

Tracing code belongs at technical boundaries:

- `community-common/common-core` owns pure trace constants, normalization, header parsing, and thread/MDC context helpers.
- `community-common/common-web` owns Servlet inbound trace bridge and Servlet HTTP response trace headers.
- `community-common/common-webflux` owns WebFlux inbound trace bridge and WebFlux HTTP response trace headers.
- `community-common/common-spring` or another common infrastructure module owns trace-aware wrappers for local async boundaries.
- Kafka producer/consumer propagation is implemented in infrastructure adapters or shared Kafka support, not in domain/application code.
- Outbox trace persistence and restoration is implemented in the outbox infrastructure.
- Jobs, schedulers, event listeners, enqueuers, and handlers restore or create trace context before calling same-domain application services.

This keeps the mandatory DDD layering intact: inbound adapters and infrastructure may deal with tracing, while application/domain logic remains tracing-agnostic.

## Trace Context Rules

Inbound context resolution uses these rules everywhere:

1. A legal W3C `traceparent` is authoritative.
2. If `traceparent` is missing or illegal, a legal `X-Trace-Id` is normalized and used.
3. If both are missing or illegal, generate a new 32-character lowercase hex trace id.
4. Response headers always include `X-Trace-Id` and `traceparent` derived from the resolved trace.
5. Invalid inbound trace values are not copied into standard trace fields or logs.

`trace.id` / `trace_id` / `span_id` are observability fields. Business `requestId`, message ids, event ids, and idempotency keys remain business fields and keep their current semantics.

## HTTP And WebFlux Flow

Servlet and WebFlux filters continue to bridge `traceparent` and `X-Trace-Id`.

When OTel is enabled:

- The Java agent creates HTTP server/client spans for supported frameworks.
- WebClient, RestTemplate, gateway routing, JDBC, Redis, and Kafka client spans are expected to come from agent instrumentation where supported.
- Project bridge code preserves legacy `X-Trace-Id` compatibility and guarantees response header write-back.

Project code must avoid creating competing root traces when a legal `traceparent` is already present.

## WebSocket And IM Flow

WebSocket/session tracing follows HTTP bootstrap context:

- Session bootstrap inherits the inbound HTTP trace.
- WebSocket handshake resolves `traceparent` / `X-Trace-Id`.
- Session-level logs include the resolved trace id.
- Message-level processing uses the session trace unless a message-specific trace context is explicitly available.

The IM asynchronous path must propagate trace through Kafka headers:

```text
gateway / browser
  -> im-realtime WebSocket
  -> Kafka command
  -> im-core consumer
  -> im-core outbox / Kafka event
  -> im-realtime consumer
  -> WebSocket push
```

Payload `requestId` continues to support acknowledgement, idempotency, and client correlation. It does not replace trace context.

## Kafka Flow

Kafka propagation must be explicit enough that repository tests can prove it:

- Producer adapters write `traceparent` and `X-Trace-Id` record headers from current context.
- Consumer listeners restore `TraceContext` and MDC from record headers before handling.
- Consumer listeners clear restored context in `finally`.
- If a consumed record has no legal trace headers, the consumer creates a new trace for that handling attempt.
- Existing payload contracts do not need trace fields added when Kafka headers are available.

OTel Kafka instrumentation may create producer/consumer spans, but repository-owned header propagation remains required for log correlation, compatibility, and tests.

## Outbox Flow

Outbox must preserve trace across durable delay:

- Enqueue captures the current resolved trace context.
- The outbox table/model stores the trace id and enough parent context to emit or rebuild `traceparent`.
- Worker execution restores trace context before invoking an `OutboxHandler`.
- Worker execution clears context in `finally`.
- Old outbox rows with no trace context remain processable; the worker generates a new trace and logs that the context was recovered from missing data.

Outbox handlers remain infrastructure adapters. They restore trace at the boundary and then call application services or Kafka adapters according to the existing DDD rules.

## Local Async, After-Commit, And Jobs

Local async boundaries need trace wrappers because ThreadLocal/MDC do not naturally cross delayed execution:

- `AfterCommitExecutor` captures the current trace when a callback is registered and restores it when the callback runs.
- Local `Runnable`, `Supplier`, and `CompletableFuture` callback sites that run after the original request thread must use trace-aware wrappers.
- Scheduled jobs and XXL jobs create a job-run trace when there is no upstream context.
- Job logs and any downstream Kafka/outbox/HTTP calls inherit that job-run trace.

Tracing wrappers must never swallow business exceptions. They only set and clear context around the delegated action.

## Logs, Metrics, And Querying

Structured logs remain the primary universal troubleshooting surface:

- Logs include `service.name`, `service.version`, `trace.id`, `trace_id`, `span_id`, `community.category`, `community.action`, and `community.outcome` where available.
- When OTel is enabled, Elastic `traces-*` contains span data and `logs-community-default` contains structured logs.
- Kibana saved objects should include a starter view for "logs and spans by trace".

Runbook guidance must be explicit:

- Use `trace.id` / `trace_id` for technical request and async chain correlation.
- Use business `requestId` or event ids for idempotency, replay, and message acknowledgement questions.
- Do not treat a business `requestId` as a tracing parent.

## Deployment Behavior

Observability overlay startup changes from "collector available, OTel optional" to "collector available, backend tracing enabled by default":

- `deployment.sh` or compose environment handling sets `OTEL_ENABLED=true` when `--observability` is used and the caller did not explicitly override it.
- Backend services still keep `OTEL_ENABLED=false` outside the observability overlay.
- `OTEL_EXPORTER_OTLP_ENDPOINT` continues to point to the EDOT collector.
- Missing or unhealthy collector degrades observability export but must not prevent application traffic.

The implementation must document how to override tracing off even when the observability overlay is used.

## Error Handling

Tracing must not break business flows:

- Illegal inbound trace headers are ignored and replaced.
- Trace injection/extraction failures are logged as structured warnings when useful, but business processing continues when possible.
- Kafka producer tracing header injection must not prevent sending a message.
- Kafka consumer and outbox context restoration must always clear context in `finally`.
- OTel exporter failures must not fail HTTP requests, Kafka handlers, outbox workers, or jobs.
- No domain or application code throws tracing-specific exceptions.

## Testing Strategy

Tests must cover both trace bridge behavior and end-to-end propagation.

Unit tests:

- `TraceIdCodec` parsing, normalization, and `traceparent` construction.
- Kafka trace header injection and extraction.
- Trace-aware runnable/supplier wrappers restore and clear context.
- Outbox event trace serialization and missing-trace fallback.

Spring and integration tests:

- Servlet filter resolves headers, writes response headers, and populates MDC.
- WebFlux filter resolves headers, writes response headers, and forwards headers.
- WebClient/RestTemplate compatibility keeps `X-Trace-Id` bridge behavior.
- Kafka producer writes headers and consumer restores/clears context.
- Outbox worker restores trace context before invoking handlers.
- Scheduled/XXL handlers create a trace when none exists.

End-to-end local acceptance:

- Start `single` topology with `--observability`; verify services have `OTEL_ENABLED=true`.
- Run one REST API through gateway and verify logs share one trace.
- Run one IM private message flow and verify the trace crosses WebSocket, Kafka command, im-core handling, Kafka event, and WebSocket push.
- Run one outbox-backed projection flow and verify worker logs inherit the enqueue trace.
- Run one scheduled or XXL job flow and verify a job-run trace appears in logs and downstream operations.
- Query Kibana/Elastic by trace and confirm logs and spans are available when OTel export is healthy.

## Acceptance Criteria

- Plain startup remains lightweight and does not enable OTel by default.
- Observability overlay startup enables OTel for all backend services by default.
- HTTP, WebFlux gateway, WebSocket, Kafka, outbox, after-commit/local async, scheduled jobs, and XXL jobs have explicit trace propagation or trace creation rules.
- Logs can correlate the full project chain by trace id.
- OTel spans are exported to Elastic traces when the collector is healthy.
- Legacy `X-Trace-Id` clients remain compatible.
- DDD layering is preserved; tracing is contained in common/infrastructure/adapters.
- Tests and runbook docs prove the behavior and explain how to inspect it.
