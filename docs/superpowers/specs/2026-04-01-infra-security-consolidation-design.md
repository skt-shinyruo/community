# Infrastructure And Security Consolidation Design

## Context

The backend currently has four different places that implement overlapping security and transport infrastructure:

- `community-app` owns private `infra.security.*` JWT configuration and Servlet security auto-configuration.
- `community-common-web` provides shared Servlet web infrastructure, but does not own shared JWT/security rules.
- `im-core` duplicates Servlet trace, exception, and security-response classes instead of reusing the shared stack.
- `gateway` and `im-realtime` each keep their own WebFlux-side JWT decoder and trace/security handling.

This creates three concrete problems:

1. The source of truth for JWT rules is fragmented.
2. HTTP error response shape and trace propagation can drift across services.
3. Shared infrastructure is copied into service modules instead of being consumed from common modules.

This design intentionally targets infrastructure only. It does not change business payloads, IM message protocol, or gateway topology.

## Goal

Build a single shared infrastructure/security base for all backend services so that:

- JWT secret and issuer rules come from one shared implementation.
- Servlet and WebFlux services emit the same HTTP security/error envelope semantics.
- trace header parsing and write-back are consistent across services.
- service-local code only keeps route authorization policy and service-specific endpoint allowlists.

## Non-Goals

This phase does not:

- change business API payload schemas;
- change WebSocket message protocol payloads;
- redesign service boundaries or gateway routing;
- refactor domain/application/service layering;
- merge Servlet and WebFlux into one technical module.

## Design Principles

1. One rule source, many consumers.
2. Shared modules own cross-cutting behavior; service modules own only local policy.
3. Servlet and WebFlux are unified semantically, not forced into the same implementation type.
4. Migration must be incremental and testable after each batch.

## Target Module Layout

### `community-common-security`

Create a new common module dedicated to transport-agnostic security primitives and shared response semantics.

Responsibilities:

- own `security.jwt.*` configuration properties;
- validate HMAC secret and issuer consistently;
- construct shared `JwtDecoder` and `JwtEncoder` building blocks;
- provide a shared helper for parsing `Jwt.sub` into integer user id;
- provide a shared security error response writer that emits `Result.error(...)` with trace headers;
- provide shared support code used by both Servlet and WebFlux layers.

This module must not contain route authorization policy, `SecurityFilterChain`, or stack-specific filter/advice implementations.

### `community-common-web`

Keep this as the shared Servlet transport module.

Responsibilities after consolidation:

- `TraceIdFilter`;
- `AuditLogFilter`;
- `ResultTraceIdAdvice`;
- `GlobalExceptionHandler`;
- Servlet-side authentication/authorization failure adapters.

These classes should stop owning their own JWT/security semantics and instead depend on `community-common-security` for shared rule evaluation and response generation.

### `community-common-webflux`

Create a new common module for WebFlux transport infrastructure.

Responsibilities:

- WebFlux trace propagation and response header write-back;
- WebFlux authentication/authorization failure handling;
- WebFlux exception-to-`Result` rendering for HTTP endpoints;
- shared components reusable by `gateway` and `im-realtime`.

This module should not own service-specific route allowlists or WebSocket business protocol logic.

### Service Modules

After migration:

- `community-app` keeps only service-local authorization policy and app-specific security rules.
- `im-core` depends on `community-common-security` and `community-common-web`, deleting duplicated Servlet infrastructure.
- `im-realtime` depends on `community-common-security` and `community-common-webflux`, deleting local JWT/security duplication.
- `gateway` depends on `community-common-security` and `community-common-webflux`, keeping only route policy and proxy-specific concerns.

## Unified Runtime Rules

### JWT

`security.jwt.*` becomes the only supported source of truth.

Required shared semantics:

- all services use `security.jwt.hmac-secret`;
- all services use `security.jwt.issuer`;
- `community-app` encodes with the same issuer that every other service validates;
- services do not carry independent placeholder lists, validation text, or issuer defaults that diverge from the shared implementation.

The shared module should expose reusable factories/builders rather than forcing every service to manually construct `NimbusJwtDecoder` or secret keys.

### HTTP Error Envelope

All HTTP services, Servlet and WebFlux, should produce the same envelope semantics based on `Result`.

Required behavior:

- authentication failure: `401` with `Result.error(CommonErrorCode.UNAUTHORIZED)`;
- authorization failure: `403` with `Result.error(CommonErrorCode.FORBIDDEN)`;
- request decode/validation failures map to the same `Result` semantics across stacks;
- generic unhandled errors map to a single shared rule;
- every HTTP error response includes unified trace header write-back and `traceId` backfill.

This does not require every exception class to be identical between stacks, but it does require the observable semantics to be identical.

### Trace Propagation

All HTTP entry/exit paths must obey the same rule:

- parse `X-Trace-Id` and `traceparent` on ingress;
- derive one normalized trace id;
- expose that trace id to response bodies and response headers;
- preserve the same behavior for normal responses, exception responses, and security direct-write responses.

WebSocket message payloads remain protocol-specific, but handshake-adjacent and service-to-service HTTP calls should use the same shared trace support.

## Detailed Component Changes

### Move out of `community-app`

The following responsibilities should stop living only under `community-app` private packages:

- JWT properties;
- HMAC secret parsing/validation;
- shared subject-to-user-id parsing helper;
- general-purpose security error response rendering.

`community-app` should continue to own:

- authority conversion specific to its JWT claims model if still app-specific;
- actuator security chain and app route authorization assembly;
- any app-only origin or metrics protection policy.

### Normalize `im-core`

`im-core` currently duplicates shared Servlet web infrastructure instead of consuming it.

The migration target is:

- depend on shared common Servlet infrastructure;
- delete local duplicates of trace filter, security exception handler, global exception handler, and response advice;
- retain only local `SecurityFilterChain` path policy and IM-specific access rules.

### Normalize `gateway` and `im-realtime`

These services should stop owning their own ad-hoc JWT decoder setup and WebFlux-side security response behavior.

They should instead:

- consume shared JWT config/rules from `community-common-security`;
- consume shared WebFlux infrastructure from `community-common-webflux`;
- keep only route-level authorization policy and local path configuration.

## Migration Plan

The implementation order is part of the design because doing it out of order increases breakage risk.

### Batch 1: Build `community-common-security`

1. Create the module and wire it into the parent Maven structure.
2. Move shared JWT properties and validation there.
3. Add shared factories/support for `JwtDecoder`, `JwtEncoder`, trace-aware security error writing, and `Jwt.sub` parsing.
4. Switch `community-app` to consume the new shared code first.

Why first:

- `community-app` is the token producer;
- the shared rule source must exist before consumers migrate.

### Batch 2: Build `community-common-webflux`

1. Create the module.
2. Add WebFlux trace and security error infrastructure.
3. Migrate `gateway`.
4. Migrate `im-realtime`.

Why second:

- the two WebFlux services currently drift together;
- their stack-specific concerns can be unified without touching Servlet migration yet.

### Batch 3: Migrate `im-core`

1. Replace local duplicated Servlet classes with shared dependencies.
2. Delete copied classes after tests prove parity.
3. Keep only IM-specific security chain and route policy in-module.

Why third:

- `im-core` is structurally a consumer of existing shared Servlet infra;
- after the shared security base is stable, this becomes a clean deletion/refactor pass.

### Batch 4: Tests and docs

1. Add shared module tests for JWT, trace, and security response semantics.
2. Update service tests that currently assert old local bean wiring or stack-specific response details.
3. Update backend docs so configuration and shared behavior have one documented source.

## Data Flow

### JWT lifecycle

1. `community-app` issues JWTs through shared encoder support in `community-common-security`.
2. `gateway`, `im-core`, and `im-realtime` validate JWTs using shared decoder support from the same module.
3. Subject parsing into user id uses the same shared helper everywhere.

### HTTP request lifecycle

1. Shared stack adapter parses trace headers on ingress.
2. Service-local security chain applies path policy.
3. On success, shared response advice/backfill writes trace headers and `traceId`.
4. On security or exception failure, shared response rendering emits the same `Result` semantics and trace headers.

## Error Handling

Infrastructure migration must be fail-closed where secrets or issuer configuration are invalid.

Required rules:

- missing or too-short JWT secret is a startup failure;
- invalid/unsupported issuer configuration is a startup failure;
- no service should silently fall back to a weaker default;
- duplicated local fallback logic should be removed once shared rules exist.

At runtime:

- security failures use the unified `Result` envelope;
- trace header generation still works even when request input is missing or malformed;
- services may keep their own business exception mappings, but the cross-cutting security/transport mappings must be centralized.

## Testing Strategy

Testing must prove semantic convergence, not just compilation.

### Shared module tests

- JWT secret validation tests;
- issuer validation tests;
- decoder/encoder compatibility tests;
- trace header normalization tests;
- security response rendering tests for 401/403 with `traceId`.

### Consumer service tests

- `community-app`: token issue + protected endpoint integration checks;
- `gateway`: unauthenticated/authenticated endpoint behavior and trace header checks;
- `im-realtime`: WebFlux HTTP security behavior and shared JWT validation checks;
- `im-core`: confirm shared Servlet infrastructure replaces local duplicates without behavioral regression.

### Regression intent

The key regression we want to prevent is semantic drift:

- one service accepting a token another rejects;
- one service omitting `traceId` in error responses;
- one service returning a different error envelope for auth failures.

## Risks And Mitigations

### Risk: migration changes externally visible behavior

This phase explicitly allows controlled external behavior change, but the new behavior must become uniform, documented, and test-covered.

Mitigation:

- define shared semantics first;
- update tests to assert those semantics directly;
- update docs in the same phase.

### Risk: over-centralization leaks service policy into shared modules

Mitigation:

- shared modules own rules and adapters, not route allowlists;
- service modules continue to own `SecurityFilterChain` path policy.

### Risk: WebFlux and Servlet become awkwardly coupled

Mitigation:

- separate transport modules;
- unify semantics via `community-common-security`, not by pretending the stacks are the same implementation.

## Expected Outcome

After this phase:

- JWT and HTTP security behavior across backend services comes from one shared rule set;
- `community-app` private security infrastructure becomes thinner;
- `im-core` deletes duplicated Servlet transport/security classes;
- `gateway` and `im-realtime` stop carrying independent JWT/security implementations;
- future services can adopt the shared base instead of copy-pasting infrastructure again.
