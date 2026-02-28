# Modular Monolith (A-1) Design — Community Backend Simplification

**Date:** 2026-02-27  
**Status:** Approved (user confirmed A-1 + single schema + single datasource)

## Context / Problem

The repository is currently structured as a multi-service system (gateway/auth/user/content/social/message/search/analytics/ops).
For a small-to-medium product maintained long-term by a small team, this causes high operational and cognitive overhead:

- Many deployables, each with its own `application.yml`, port, service discovery, and RPC wiring.
- Cross-domain **runtime coupling** already exists (e.g. `content-service ↔ social-service`), reducing real autonomy while keeping distributed complexity.
- Debugging and local development require multiple services + Nacos/Dubbo/Gateway, which is expensive for day-to-day iteration.

At the same time, the user confirmed:

- The backend can be released **as a whole** (no independent deployments required).
- We should optimize for **long-term maintainability** rather than microservice purity.

## Goals

1. **Single deployable backend** (one Spring Boot application) serving all existing `/api/**` endpoints.
2. **Keep API paths stable** to minimize frontend changes.
3. **Remove distributed-only infrastructure** that no longer provides value for a single deployable:
   - Nacos (discovery/config import)
   - Dubbo RPC (remote calls between “services”)
   - Spring Cloud Gateway (routing between “services”)
4. **Single MySQL schema** (`community`) with **single datasource**.
5. Preserve core capabilities (initially): MySQL / Redis / Kafka / ES / Outbox / observability.

## Non-goals (for A-1)

- Re-architecting domains into a new DDD model.
- Removing Kafka/Outbox/Event-Envelope patterns (can be evaluated after monolith stabilizes).
- Multi-datasource / multi-schema support (explicitly rejected in favor of a single schema).

## Current Coupling Evidence (Symptoms)

### Build-time coupling

- `content/content-service/pom.xml:49` depends on `social-api`
- `social/social-service/pom.xml:54` depends on `content-api`

This is not inherently wrong, but it signals that the system’s boundaries are already interwoven.

### Runtime coupling (write-path, fail-closed)

- Content write-path (comment/reply) calls Social for block checks:
  - `content/.../SocialBlockClient` uses `@DubboReference SocialBlockRpcService`
  - `content/.../CommentService` enforces “blocked ⇒ forbid interaction”
- Social write-path (like/follow) calls Content for entity resolution:
  - `social/.../ContentEntityResolver` uses in-process `EntityResolveRpcService`
  - `social/.../LikeService` resolves entity metadata to avoid trusting client-injected fields

In a multi-deployable world, this creates cascade timeouts + release coupling.
In a monolith, these become normal in-process module calls.

## Proposed Architecture (A-1)

### 1) Single runtime app: `community-app`

Introduce a single Spring Boot application module:

- `app/community-app` (new Maven module)
- One `@SpringBootApplication` entrypoint under `com.nowcoder.community`
- Depends on existing domain modules as libraries
- Provides the single `application.yml` and a single port

All existing controllers remain as-is (they already use `/api/...`), so external routing does not change.

### 2) Remove Dubbo/Nacos/Gateway for internal communication

Replace inter-service RPC with in-process calls:

- Replace `@DubboReference` with constructor injection of the same interface type.
- Replace `@DubboService` with Spring `@Service` / `@Component`.
- Keep `*-api` modules temporarily as “module boundary interfaces” (interfaces/DTO/events).

This keeps the code changes minimal while eliminating network/service-discovery uncertainty.

### 3) Centralize configuration (single `application.yml`)

Today, each module ships its own `src/main/resources/application.yml`.
In a monolith, **multiple `application.yml` on the classpath will merge**, causing port/name/datasource config collisions.

Design decision:

- Only `app/community-app/src/main/resources/application.yml` remains as `application.yml`.
- Existing module configs are renamed and kept for reference (e.g. `module-application-content.yml`)
  or moved under a non-default location so Spring Boot does not auto-load them.

### 4) Security: one `SecurityFilterChain`

Each former service currently defines its own `*SecurityConfig` with a `SecurityFilterChain` (often same method name and `@Order`),
which will conflict in a single app.

Design decision:

- Remove/disable per-module `*SecurityConfig` classes.
- Create one consolidated security config in `community-app`:
  - Merge the previous `authorizeHttpRequests` rules.
  - Keep actuator security chain from `infra-security-starter` (already uses `securityMatcher("/actuator/**")` and `@Order(1)`).

### 5) Database: single schema `community`

Design decision:

- Keep a single schema `community` and one datasource.
- Migrate MySQL init scripts from multi-schema to `use community;` only.

#### Shared-table conflicts to resolve

Multiple schemas currently define identically named tables:

- `outbox_event` (identity/content/social)
- `http_idempotency` (content/message)

Design decision:

- Use **one shared** `outbox_event` table compatible with `platform/infra-outbox` mapper.
- Use **one shared** `http_idempotency` table.
  - Standardize idempotency `operation` naming to include domain prefixes:
    - `content:create_comment`, `message:send_message`, etc.

This avoids table duplication and makes the monolith’s shared infra truly shared.

## Migration Strategy (High-Level)

### Phase 0: Preparation

- Create an isolated git worktree for the refactor.
- Verify baseline `mvn test` passes.

### Phase 1: Add `community-app` module

- Add Maven module + single entrypoint.
- Make it build without changing existing services yet.

### Phase 2: Config + Security consolidation

- Introduce single app `application.yml`.
- Rename/remove other `application.yml` to prevent classpath merging.
- Consolidate all security rules into one filter chain.

### Phase 3: DB schema unification

- Update MySQL init scripts to single schema `community`.
- Resolve `outbox_event` and `http_idempotency` conflicts.
- Point datasource config to the unified schema.

### Phase 4: Remove internal “distributed wiring”

- Replace Dubbo references/services with in-process beans.
- Remove Nacos imports and Spring Cloud Gateway module from build.
- Remove Dubbo/Nacos/Gateway dependencies and configs once no longer used.

## Risks / Mitigations

- **Security config collisions** → mitigate by consolidating to a single `SecurityFilterChain`.
- **Classpath config merging (`application.yml`)** → mitigate by ensuring only one `application.yml` exists.
- **Table-name conflicts in single schema** → mitigate by defining shared tables once; prefix idempotency operations.
- **Unexpected bean collisions** (same bean names across modules) → mitigate iteratively; prefer constructor injection + explicit bean names where required.
- **Operational regressions** (rate limit/origin guard previously in gateway) → mitigate by relying on auth-service’s own rate-limit and platform servlet filters first; revisit edge hardening later.

## Acceptance Criteria

1. One Spring Boot process serves all `/api/**` endpoints.
2. No Nacos required to start the backend.
3. No Dubbo required for internal calls.
4. No Spring Cloud Gateway required for routing.
5. One MySQL schema `community`, one datasource, with all required tables.
6. `mvn test` passes in the new build topology.
