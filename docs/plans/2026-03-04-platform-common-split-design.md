# Platform Common Split Design (Capability-based Infra Starters)

**Date:** 2026-03-04  
**Status:** Approved (user chose direction C + option A: remove `platform/common`)

## Context

The backend is a modular monolith. Domain modules (`*-service`) are compiled and assembled into one deployable app (`community-bootstrap` / `community-app`). The `backend/platform` subtree already distinguishes:

- `contracts-*`: stable cross-module contracts (Result/error codes/headers/event envelope)
- `infra-*`: cross-cutting infrastructure delivered as starters (example: `infra-security-starter`, `infra-outbox`)

However, `backend/platform/common` has become a catch-all module:

1. It is effectively a Spring Boot starter (it registers multiple `@AutoConfiguration`s via `AutoConfiguration.imports`) while being named "common".
2. It has a very wide dependency surface (web + webflux + security + redis + jdbc + kafka + micrometer), so "just add one small thing" tends to inflate the module forever.
3. Domain-specific logic has already leaked into platform (example: conversationId parsing tied to message domain), accelerating the "junk drawer" effect.

## Goals

1. Remove `backend/platform/common` entirely.
2. Replace it with **capability-based** `infra-*` modules (each with a clear boundary and minimal dependency surface).
3. Keep runtime behavior stable (no API contract changes; no business rule rewrites).
4. Make "where should this code live?" obvious and enforceable.
5. Enable incremental migration with small, safe commits (build stays green after each wave).

## Non-goals (first wave)

1. No renaming of external API paths (`/api/**`, `/files/**`).
2. No big-bang rewrite of domain layering or inter-domain dependency graph.
3. No mass renaming of configuration keys (existing keys remain; refactors focus on code location and module boundaries).

## Target Structure

### 1) Package namespaces (strong convention)

- `com.nowcoder.community.contracts.*`: stable protocol/contract types only.
- `com.nowcoder.community.infra.*`: cross-cutting infrastructure (starter or library), **no domain semantics**.
- `com.nowcoder.community.<domain>.*`: domain implementation (`auth`, `user`, `content`, `social`, `message`, `search`, `analytics`, `ops`).

Policy: Any class whose javadoc/name mentions a specific domain concept (post/comment/conversation/like/...) must live in the corresponding domain module, not in `infra`.

### 2) Capability modules (replace `platform/common`)

Split `platform/common` into the following modules:

1. `backend/platform/infra-trace`
   - Purpose: traceId runtime context and MDC helpers.
   - Contains: `TraceId`, `TraceContext`.

2. `backend/platform/infra-web-starter`
   - Purpose: servlet/reactive web cross-cutting (filters, advice, exception mapping, Jackson defaults, IP resolution, audit logging).
   - Contains:
     - `TraceIdFilter` (+ reactive `TraceIdWebFilter` if kept)
     - `AuditLogFilter`
     - `GlobalExceptionHandler`, `SecurityExceptionHandler` (+ reactive variants)
     - `CommonJacksonConfig`
     - `TrustedProxyProperties`, `ClientIpResolver`
   - Provides auto-config via `AutoConfiguration.imports` with servlet/reactive branches.

3. `backend/platform/infra-internal-client`
   - Purpose: internal HTTP client conventions (headers/unwrap/error mapping/metrics).
   - Contains: `InternalClientSupport` (and related helpers if needed).

4. `backend/platform/infra-kafka-starter`
   - Purpose: Kafka cross-cutting defaults (DLQ publisher, consumer trace bridging utilities).
   - Contains:
     - `KafkaTraceSupport`
     - `KafkaDlqPublisher`, `KafkaDlqRecord`
     - `KafkaDlqPublisherAutoConfiguration` (as a default bean provider)

5. `backend/platform/infra-idempotency-starter`
   - Purpose: HTTP idempotency (store + guard) as an opt-in capability.
   - Contains: `IdempotencyGuard`, `IdempotencyStore`, `JdbcIdempotencyStore`, `RedisIdempotencyStore`, `IdempotencyProperties`, auto-config.

6. `backend/platform/infra-scheduler-starter`
   - Purpose: scheduler/job helpers (single-flight locks, etc.).
   - Contains: `SingleFlightTaskGuard` (+ auto-config when Redis is present).

7. `backend/platform/infra-tx`
   - Purpose: transaction boundary helpers.
   - Contains: `AfterCommitExecutor`.

8. `backend/platform/infra-startup-validation-starter`
   - Purpose: production startup fail-closed validation framework.
   - Contains: auto-config + runner integration.
   - Key change: split "framework mechanism" from "service-specific policy":
     - infra provides the runner and a simple SPI (e.g. `StartupValidator` list)
     - each domain module contributes its own validators when necessary (e.g. auth-only SMTP rules)

### 3) Explicit “must move out of platform” items

To prevent domain leakage:

- `ConversationIdParser` (message domain concept) moves to `backend/message-service`.
- `OwnerGuard` currently exposes domain-specific methods (conversation membership checks) and moves to `backend/message-service` (or becomes a generic infra guard without domain semantics; first wave keeps it domain-owned).
- `HtmlEntityCodec` is currently only used by content; moves to `backend/content-service`.
- `ValidationLimits` is used by multiple API DTOs; it is a contract-level constant set and moves to `backend/platform/contracts-core`.

## Dependency Rules

Enforce a 3-layer dependency direction:

1. `contracts-*` (lowest)
   - Must not depend on Spring/Micrometer/Kafka/Redis/JDBC/domain modules.

2. `infra-*` (middle)
   - May depend on `contracts-*` and on frameworks (Spring/Micrometer/etc.).
   - Must not depend on any domain module.

3. `*-service` and `community-bootstrap` (top)
   - Domain modules may depend on `contracts-*` and selected `infra-*`.
   - `community-bootstrap` is the composition root and should explicitly declare infra starters used by the runtime.

## AutoConfiguration Design

Each `infra-*-starter` that provides Spring defaults follows these rules:

1. Use Spring Boot 3 `AutoConfiguration.imports` (one file per starter).
2. Split servlet/reactive auto-config using:
   - `@ConditionalOnWebApplication(type = SERVLET/REACTIVE)`
   - `@ConditionalOnClass` for the relevant types
3. Provide only defaults via `@ConditionalOnMissingBean` so `community-bootstrap` can override safely.
4. Avoid hiding critical runtime behavior behind transitive dependencies. Prefer explicit dependencies in `community-bootstrap` for “global must-have” cross-cutting capabilities.

## Migration Strategy (high level)

Wave 0 (safety):
1. Ensure baseline build is green and repeatable (`cd backend && mvn test`).

Wave 1 (introduce new infra modules):
1. Create the new `infra-*` modules with minimal POMs and no code changes.
2. Move code from `platform/common` into the appropriate module, preserving behavior.
3. Keep old imports compiling by updating callers immediately (no "compat packages" in first wave).

Wave 2 (domain leakage cleanup):
1. Move message/content domain-specific helpers into the corresponding domain modules.
2. Move `ValidationLimits` into `contracts-core`.

Wave 3 (delete `platform/common`):
1. Remove `platform/common` module from the platform reactor and from the top-level backend reactor.
2. Update all `*-service` and `community-bootstrap` POMs to depend on the new infra modules.
3. Verify build + tests, then delete remaining unused classes/resources.

## Risks and Mitigations

1. **Accidental behavior changes in auto-config**
   - Mitigation: move code with minimal edits; keep beans `@ConditionalOnMissingBean`; run tests after each wave.

2. **Bean duplication or missing beans**
   - Mitigation: ensure each starter owns its `AutoConfiguration.imports`; keep conditional loading logic equivalent to current common.

3. **Cross-module import churn**
   - Mitigation: migrate by capability (one infra module at a time), keep commits small.

4. **Startup validation coupling to app name**
   - Mitigation: introduce an SPI list (`StartupValidator` contributors) and keep existing rules by migrating them into `auth-service` validators without changing semantics.

## Acceptance Criteria

1. `backend/platform/common` module is removed.
2. All previous `platform/common` capabilities still exist, but are owned by capability-based `infra-*` modules.
3. No domain-specific helpers remain under `backend/platform` (except `contracts-*` and `infra-*`).
4. Backend builds and tests pass (`cd backend && mvn test`).
5. New code placement rules are documented and followed (contracts vs infra vs domain vs bootstrap).

