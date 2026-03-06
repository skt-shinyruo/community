# Flatten Backend Monolith Design

**Date:** 2026-03-06

**Goal:** Collapse the backend from a modular monolith with microservice-era seams into a single backend module with package-level boundaries, local in-process collaboration, local event dispatch, and module-owned security rules.

**Status:** Approved for implementation in the current session.

---

## Context

The backend currently runs as one Spring Boot process, but much of the code still models the system as if it were a fleet of services:

- domain Maven modules depend on each other as if they were service libraries
- in-process calls still go through `*RpcService`, `*InternalClient`, `Result`, timeout classification, and synthetic downstream-availability errors
- asynchronous projections still use Kafka, outbox, consumed-event tables, and replay-oriented infrastructure even though producers and consumers live in the same process
- HTTP authorization is centralized in `community-bootstrap`, so API ownership and security ownership diverge
- some controllers, especially content APIs, still orchestrate application use cases directly instead of delegating to a stable application layer

This creates needless complexity, duplicated abstractions, and misleading failure semantics.

---

## Decision Summary

### 1. Collapse backend build structure to one deployable backend module

Keep `backend/community-bootstrap` as the only Maven module and deployable artifact.

Move sources from:

- `backend/auth-service`
- `backend/user-service`
- `backend/content-service`
- `backend/social-service`
- `backend/message-service`
- `backend/search-service`
- `backend/analytics-service`
- `backend/ops-service`
- `backend/platform/*`

into `backend/community-bootstrap/src/main/java` and `backend/community-bootstrap/src/test/java`, preserving package names such as:

- `com.nowcoder.community.auth`
- `com.nowcoder.community.content`
- `com.nowcoder.community.search`

This removes module-level seams while preserving package-level domain organization.

### 2. Replace internal RPC semantics with local application services

Remove internal adapters that exist only to simulate remote calls:

- `SocialBlockClient`
- `RpcLikeQueryService`
- `ContentServiceClient`
- `*RpcServiceImpl` classes used only for in-process collaboration

Replace them with direct local collaborators in `application` packages:

- `social.application.BlockQueryApplicationService`
- `social.application.LikeQueryApplicationService`
- `content.application.PostScanApplicationService`

Callers use local method signatures and local exceptions instead of `Result`, unwrap helpers, fail-open branches, timeout classification, or synthetic “service unavailable” behavior.

### 3. Replace Kafka/outbox/replay flow with local domain events

For projections and side effects that remain asynchronous in concept but local in deployment:

- publish domain events in the same process
- handle them via `@TransactionalEventListener(phase = AFTER_COMMIT)`
- remove Kafka producers, consumers, outbox relay, consumed-event stores, DLQ/replay code, and Redis-based reindex lock coordination that only exist for multi-process deployment

Local event listeners remain decoupled from write paths, but the implementation reflects single-process reality.

### 4. Push authorization ownership back into domains

Introduce a small extension point in bootstrap, for example `ApiSecurityRules`, and let each domain contribute its own authorization rules:

- `auth.api.security.AuthSecurityRules`
- `user.api.security.UserSecurityRules`
- `content.api.security.ContentSecurityRules`
- `social.api.security.SocialSecurityRules`
- `search.api.security.SearchSecurityRules`
- `analytics.api.security.AnalyticsSecurityRules`
- `ops.api.security.OpsSecurityRules`

`CommunitySecurityConfig` becomes an assembler rather than the global truth source for path ownership.

### 5. Normalize layering inside the flattened module

Each domain package adopts a consistent structure:

- `api`: controllers, HTTP DTOs, security rules
- `application`: use-case orchestration, transaction boundaries, local event publication
- `domain`: entities, domain rules, domain services, domain events
- `infra`: mappers, repositories, Redis/ES/file integrations

`PostController` becomes a thin HTTP adapter. Content query assembly and command orchestration move into `application`.

---

## Target Architecture

### Build

- parent: `backend/pom.xml`
- child module: only `backend/community-bootstrap`
- all previous module POMs removed
- all dependencies consolidated into `backend/community-bootstrap/pom.xml`

### Runtime

- one Spring Boot application
- one datasource
- one Redis integration where needed for cache/data structures, not distributed coordination for intra-process workflows
- one Elasticsearch integration
- no Kafka requirement for local projections

### Package Boundaries

Package boundaries remain the main organizational tool. ArchUnit checks are updated to enforce package-layer rules inside one module rather than module-to-module API dependency rules.

---

## Major Refactor Areas

### A. Build Collapse

- move source trees into `community-bootstrap`
- merge resources, mappers, and tests
- rewrite `backend/pom.xml`
- rewrite `backend/community-bootstrap/pom.xml`
- delete old module POMs and stale module wiring

### B. Local Collaboration

- replace `Result<T>` internal collaboration with plain return values
- remove `InternalClientSupport` usage from local business flows
- delete internal transport DTOs that only existed for RPC boundaries

### C. Local Eventing

- introduce local domain event publisher abstraction backed by Spring events
- move search indexing updates, notification projection, and analytics updates to local event listeners
- remove outbox and Kafka glue where it is only serving in-process listeners

### D. Content Application Layer

- add explicit content application services for post queries, post commands, and comment operations
- move text sanitation, tag aggregation, bookmark/like composition, and subscription-aware query composition out of `PostController`

### E. Modular Security Registration

- each domain owns its path authorization rules
- bootstrap gathers rule contributors and applies them in order

### F. Reindex Simplification

- replace Redis lock/renewal job coordination with single-process in-memory coordination
- keep existing ops API shape where practical

---

## Risks and Mitigations

### Risk: large file moves break resources or scans

Mitigation:

- move domain by domain
- run targeted tests after each domain move
- verify MyBatis mapper locations and resource loading after each merge

### Risk: event refactor breaks projections

Mitigation:

- add tests that assert listeners run after commit and update search/notifications
- remove Kafka wiring only after local listeners are green

### Risk: security regression during ownership transfer

Mitigation:

- add integration tests for representative public, authenticated, moderator, and admin endpoints
- keep contributor ordering explicit in bootstrap

### Risk: over-scoped controller refactor

Mitigation:

- thin `PostController` first
- use it as the pattern for later controllers, but avoid rewriting all controllers in one pass unless tests justify it

---

## Non-Goals

- changing external API paths
- changing frontend contracts unless a server-side cleanup forces a harmless response-shape fix
- redesigning domain behavior unrelated to the monolith flattening
- splitting the app again for future microservices in this change

---

## Verification Strategy

- baseline backend test run before edits
- targeted unit/integration tests for each refactor slice
- full backend test run after major milestones
- architecture/documentation consistency review at the end

---

## Expected Outcome

After the refactor:

- the backend is built and packaged as one backend module
- local collaboration uses local application services, not faux RPC seams
- local side effects use local events, not Kafka/outbox/replay infrastructure
- security rule ownership is colocated with each domain
- controllers are thinner and layering is more explicit
- the code reflects actual deployment reality: one backend process, one codebase, one application
