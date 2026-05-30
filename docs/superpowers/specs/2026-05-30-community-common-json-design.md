# Community Common JSON Module Design

## Summary

Extract shared JSON infrastructure into a dedicated Maven module named
`community-common-json` under `backend/community-common/common-json`.

The module standardizes the project's Jackson configuration, JSON codec API,
exception handling, and reusable JSON parsing helpers. The migration keeps
existing JSON behavior stable, removes Jackson infrastructure from
`community-common-core`, and prevents domain models from depending on JSON tree
types.

This design does not attempt to hide every Jackson type. Jackson remains the
project's JSON engine, and DTO or contract annotations may continue to use
Jackson annotations where they define external JSON shape.

## Goals

- Create a pure Java `community-common-json` Maven module with no Spring
  dependency.
- Provide one standard JSON entry point for project code that serializes,
  deserializes, reads tree payloads, or maps tree payloads to typed objects.
- Centralize Jackson `ObjectMapper` construction so standalone code, tests, and
  Spring integrations share the same base behavior.
- Move generic JSON helpers, including event envelope JSON parsing, out of
  `community-common-core`.
- Keep `community-common-core` free of Jackson dependencies.
- Keep domain packages free of `ObjectMapper`, `JsonNode`, and other Jackson
  databind types.
- Preserve current HTTP, Kafka, Redis, WebSocket, and OSS JSON contracts.
- Add architecture guardrails that prevent mapper construction and JSON
  infrastructure from spreading back into unrelated modules.

## Non-Goals

- Do not replace Jackson with another JSON library.
- Do not create a full abstraction that makes every consumer unaware of Jackson.
- Do not remove Jackson annotations from DTOs, API contracts, or event contracts
  when those annotations describe JSON contract shape.
- Do not change global naming policy, such as switching to snake case.
- Do not globally omit null values for every response or payload.
- Do not change HTTP endpoints, Kafka topics, Redis key formats, database
  schemas, WebSocket frame contracts, or OSS client contracts.
- Do not split backend services or change deployable topology.

## Current State

JSON usage is spread across multiple modules:

- `community-common/common-core` owns the Jackson-free `EventEnvelope` contract.
- `community-common/common-json` owns `EventEnvelopeJsonParser` and envelope
  null-omission behavior through a Jackson mix-in.
- `community-common/common-web` owns `CommonJacksonConfig`, which disables
  timestamp-style date serialization for Spring MVC.
- `community-common/common-webflux` and `common-web` inject Spring
  `ObjectMapper` into security exception handlers.
- `community-common/common-idempotency` injects `ObjectMapper` for cached
  response serialization and replay.
- `community-app` uses `ObjectMapper` in infrastructure event handlers,
  Redis-backed repositories, application projection code, and some web filters.
- `community-im` modules use Jackson for WebSocket frame parsing, outbox
  payloads, Kafka handlers, and shared IM contract annotations.
- `community-oss-client` creates its own static `JsonMapper` to parse Result
  envelopes returned by the OSS service.
- Tests create many local `new ObjectMapper()` instances with inconsistent
  module registration and feature settings.

The project does not currently use Gson or FastJSON as a parallel JSON stack.

## Target Module

Add the module:

```text
backend/community-common/common-json
```

with artifact:

```text
com.nowcoder.community:community-common-json
```

Register it in `backend/community-common/pom.xml`.

The module remains pure Java. It may depend on Jackson core/databind modules,
but it must not depend on Spring Framework, Spring Boot, Servlet, WebFlux,
Kafka, Redis, MyBatis, or any business module.

Expected package root:

```text
com.nowcoder.community.common.json
```

## Public API Shape

The module exposes a project JSON facade for ordinary application code:

```java
public interface JsonCodec {
    String toJson(Object value);
    <T> T fromJson(String json, Class<T> type);
    JsonNode readTree(String json);
    <T> T treeToValue(JsonNode node, Class<T> type);
    JsonNode valueToTree(Object value);
}
```

The module also exposes a Jackson-backed implementation and mapper factory:

```text
JsonCodec
JacksonJsonCodec
JsonMappers
JsonCodecException
EventEnvelopeJsonParser
```

`JsonCodecException` wraps Jackson processing failures so most project code no
longer catches `JsonProcessingException` directly.

`JsonMappers` is the single construction point for standalone mappers used by
non-Spring clients and tests. Spring modules may adapt the same mapper settings
through their existing auto-configuration surfaces.

## Mapper Behavior

The first migration phase preserves behavior. The shared mapper baseline is:

```java
JsonMapper.builder()
    .findAndAddModules()
    .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
    .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
    .build();
```

Do not enable global `JsonInclude.Include.NON_NULL` in this migration. The
existing non-null behavior is specific to `EventEnvelope`, and changing all
responses or payloads would alter external contracts.

If `EventEnvelope` still needs null omission when serialized, implement that
behavior from the JSON module through a dedicated writer, a mix-in, or another
localized mapper configuration. Do not keep Jackson annotations in
`community-common-core`.

## Spring Integration

`community-common-json` does not own Spring Boot auto-configuration in the
first phase.

`community-common/common-web` and `community-common/common-webflux` may continue
to own Spring-specific integration, but their Jackson customizers must delegate
to or mirror the settings defined by `community-common-json`. This keeps pure
libraries from pulling Spring while still making Spring runtime JSON behavior
consistent with standalone clients and tests.

Spring bridge modules are responsible for exposing a `JsonCodec` bean backed by
the application `ObjectMapper` when a Spring application needs dependency
injection. The pure JSON module provides the implementation; the Spring modules
provide only bean wiring.

Spring HTTP message conversion can continue to use Spring's `ObjectMapper`.
Business and infrastructure code that explicitly serializes JSON should prefer
`JsonCodec`.

## DTO And Contract Annotations

Jackson annotations on DTOs and contract payloads are allowed when they define
the public JSON shape of those types. Examples include:

- `@JsonProperty`
- `@JsonIgnoreProperties`
- `@JsonAnySetter`
- `@JsonIgnore`
- existing project-level composed contract annotations

These annotations are contract metadata, not shared JSON infrastructure. The
migration must not create wrapper annotations unless repeated annotation
patterns justify them.

## DDD And Layering Boundaries

`community-common-json` is a technical infrastructure library, not a domain
model dependency.

Domain packages must not depend on Jackson databind types such as `ObjectMapper`
or `JsonNode`, and they should not depend on the `JsonCodec` facade. JSON
serialization belongs in application orchestration, infrastructure adapters, or
shared technical libraries.

The existing `notice.domain.model.NoticeProjection` currently stores a
`JsonNode` payload. This migration should remove that domain dependency by
keeping JSON tree conversion in the notice application or infrastructure layer.

Application services may use `JsonCodec` for use-case orchestration when JSON
serialization is part of the use case, such as notice content assembly or
idempotency response replay. Infrastructure adapters may use `JsonCodec` for
Redis values, outbox payloads, Kafka payloads, WebSocket frames, and HTTP client
envelope parsing.

## Migration Scope

In scope:

- Create `community-common-json`.
- Move event envelope JSON parsing from `common-core` to `common-json`.
- Remove Jackson dependency from `common-core`.
- Change generic JSON serialization call sites from raw `ObjectMapper` usage to
  `JsonCodec` where the code owns serialization.
- Keep Spring framework integration in `common-web` and `common-webflux`, wired
  to the shared mapper behavior.
- Replace standalone mapper creation in `community-oss-client` with the shared
  mapper or codec.
- Remove Jackson tree types from domain models touched by the migration.
- Update tests that verify JSON behavior to use the shared mapper factory or
  codec.
- Add architecture guardrails for JSON boundaries.

Out of scope:

- Mechanical removal of every Jackson import in tests.
- Rewriting DTOs solely to remove Jackson annotations.
- Reworking API contracts or event payload field names.
- Converting all application code to a library-neutral JSON abstraction.
- Introducing a second Spring-specific JSON module unless the implementation
  proves the pure module cannot stay clean.

## Guardrails

Add architecture or build guardrails with these rules:

- `community-common-core` must not declare or use `jackson-databind`.
- Domain packages must not depend on `com.fasterxml.jackson.databind..`.
- Production code outside `community-common-json` and approved Spring bridge
  configuration must not construct or configure its own mapper with
  `new ObjectMapper()`, `JsonMapper.builder()`, `SerializationFeature`, or
  `DeserializationFeature`.
- DTO and contract packages may depend on `com.fasterxml.jackson.annotation..`.

Guardrails should be precise enough to allow legitimate framework integration
and contract metadata while blocking scattered JSON infrastructure.

## Testing

Add focused tests for `community-common-json`:

- standard mapper registers Java time support
- dates are not serialized as timestamps
- unknown properties do not break typed deserialization
- `JsonCodec` wraps serialization and deserialization failures in
  `JsonCodecException`
- event envelope JSON parsing validates required fields and preserves payload
  tree access

Run focused tests for affected consumers:

- common web and webflux security exception handlers
- common idempotency serialization and replay tests
- app outbox enqueue and handler tests
- notice projection tests
- auth Redis repository tests
- IM outbox, Kafka handler, WebSocket frame, and push tests
- OSS client response parsing tests

Run architecture tests after guardrail changes:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Run module tests for the new and affected modules before implementation is
considered complete.

## Acceptance Criteria

- `community-common-json` exists and is registered under `community-common`.
- Shared mapper settings have one canonical source.
- `community-common-core` no longer depends on Jackson databind.
- Generic JSON parsing and serialization code uses `JsonCodec` or
  `JsonMappers` instead of scattered mapper construction.
- Domain code does not depend on Jackson databind types.
- DTO and contract Jackson annotations remain allowed.
- Existing JSON contracts remain behaviorally compatible.
- Guardrails prevent new production mapper construction outside approved JSON
  infrastructure.
- Focused module tests and relevant architecture tests pass.

## Risks

- Tightening guardrails may expose existing violations outside the initial
  migration path.
- Replacing injected `ObjectMapper` with `JsonCodec` can affect tests that mock
  Jackson failures directly; tests should move to codec-level failure behavior
  where possible.
- Removing `JsonNode` from domain models may require small application-layer
  reshaping, especially in notice projection.
- `FAIL_ON_UNKNOWN_PROPERTIES` behavior must be checked against current Spring
  runtime behavior and OSS client expectations to avoid accidental strictness
  changes.
- Global null omission must stay out of the shared mapper baseline to avoid
  response contract drift.
