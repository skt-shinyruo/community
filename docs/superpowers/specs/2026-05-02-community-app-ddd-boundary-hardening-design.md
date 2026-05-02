# Community App DDD Boundary Hardening Design

**Date:** 2026-05-02
**Status:** Draft for review
**Owner:** Codex

---

## 1. Goal

`backend/community-app` 已经有严格 DDD Tactical Layering 的目标包形状和一批 ArchUnit 规则，但最新代码检查显示仍存在“包名合规、职责漂移”的问题。

本设计的目标是把这些问题拆成可安全落地的治理批次：

- 先补齐可执行 guardrail，阻止新的边界污染继续进入；
- 再修复当前最明确、行为风险最低的边界泄漏；
- 最后按领域逐步重塑 facade、跨域 side effect 和贫血模型。

本设计不是一次全量重构方案。第一批只处理架构边界与可测试行为保持，后续领域建模另行按领域计划执行。

---

## 2. Existing Rules And Constraints

本设计必须遵守仓库根目录 `AGENTS.md` 中的严格 DDD Tactical Layering：

```text
Controller / Listener / Job
  -> ApplicationService
      -> Domain model / DomainService / Repository interface / Domain event
      -> foreign owner-domain api.query / api.action / api.model when cross-domain synchronous collaboration is required
      -> contracts.event when cross-domain asynchronous collaboration is required
          -> Infrastructure implementation
```

相关文档必须保持一致：

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/superpowers/specs/2026-04-27-community-app-strict-ddd-tactical-layering-design.md`
- `docs/superpowers/specs/2026-05-01-community-app-ddd-boundary-cleanup-design.md`

相关架构测试位置：

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`

---

## 3. Current Problems

### 3.1 Application Still Leaks Spring Web Upload Types

`user.application.UserAvatarApplicationService` and `user.application.port.AvatarStoragePort` accept `MultipartFile`.

This violates application transport neutrality:

- `MultipartFile` is HTTP multipart transport input, not use-case input;
- application tests need Spring Web types to exercise a user use case;
- the storage port becomes tied to Spring MVC instead of an application-level upload abstraction.

The current implementation path also leaves `UserFileApplicationService` parsing raw request URI strings. URL pattern extraction belongs in the controller/web adapter; the application service should receive a file key and validate business constraints.

### 3.2 Event And Outbox Adapters Do Too Much

`search.infrastructure.event.PostOutboxHandler` directly depends on `content.api.query.PostScanQueryApi`, pulls current content state, decides whether to delete or sync the search projection, then invokes `SearchApplicationService`.

This violates inbound adapter responsibilities:

- the handler is not only adapting outbox input;
- foreign synchronous collaboration is happening before the same-domain application boundary;
- projection decision logic is split into infrastructure.

`content.infrastructure.event.PostDomainEventBridge` and `PostPayloadAssembler` similarly perform business payload construction in infrastructure. `PostPayloadAssembler` loads content repositories, decodes text, and assembles `contracts.event.PostPayload`. The technical bridge is deciding what the published business fact looks like.

### 3.3 ApplicationService Has Become A Facade Layer In Some Domains

Several domains now have one public `*ApplicationService` that delegates to other same-domain `*ApplicationService` classes:

- `auth.application.AuthApplicationService`
- `market.application.MarketApplicationService`
- `wallet.application.WalletApplicationService`

This recreates a facade/application split under the same suffix. It weakens the rule that `*ApplicationService` is the use-case entry and makes transaction/idempotency ownership harder to locate.

### 3.4 Cross-Domain Side Effects Are Too Synchronous

Some write paths synchronously call foreign action APIs for effects that are naturally projections:

- content post publishing triggers user points and growth tasks;
- social like changes trigger user points and growth tasks.

Synchronous `api.action` is valid for strong consistency collaboration, but points, growth tasks, notifications, and search projections should normally consume `contracts.event` with idempotency. Otherwise content/social write availability is coupled to user/growth/wallet processing.

### 3.5 Domain Models Remain Mostly Data Containers

The market order flow is the clearest example:

- `MarketOrder` is a mutable data object with getters and setters;
- `MarketOrderApplicationService` owns most order creation, delivery, shipment, cancellation, stock, and status transition logic;
- `MarketOrderDomainService` is mostly validation;
- `MarketOrderRepository` exposes many `markXxx` and `changeStatus` methods.

This is not a package dependency violation, but it means the DDD tactical layering has not yet fully moved business behavior into domain models and domain services.

---

## 4. Scope

### 4.1 First Batch In Scope

The first implementation batch should cover only boundary hardening with low behavioral risk:

- add ArchUnit guardrails for the identified blind spots;
- remove Spring Web upload types from user application service and application port;
- move `/files/**` key extraction out of application and into controller/web adapter;
- make search outbox handler call only same-domain application service;
- move content post contract payload assembly out of infrastructure event adapter into application-owned code;
- update focused tests and handbook architecture docs.

### 4.2 First Batch Non-Goals

The first batch must not:

- redesign `MarketOrder` or wallet aggregates;
- replace synchronous points/growth calls with outbox consumers;
- remove every application-to-application call in all domains;
- change public HTTP paths, response fields, status behavior, or error codes;
- change database schema;
- split `community-app` into multiple Maven modules.

### 4.3 Later Batches

Later batches should handle:

- collapsing application facade layering in auth, market, and wallet;
- moving points/growth side effects to `contracts.event` consumers where strong consistency is not required;
- reshaping `MarketOrder`, `WalletAccount`, and related repositories into stronger domain objects and intent-oriented persistence contracts.

---

## 5. Target Design

### 5.1 Guardrails

ArchUnit should make the intended boundary executable before broad code changes.

New or tightened rules:

- classes under `..application..` must not depend on:
  - `org.springframework.web..`
  - `org.springframework.web.multipart..`
  - `jakarta.servlet..`
  - `org.springframework.http..`
  - `org.springframework.core.io..`
- inbound adapters include `*Controller`, `*Listener`, `*Handler`, `*Bridge`, `*Enqueuer`, and `*Job` where they live under controller, event, job, or outbox-related infrastructure packages;
- inbound adapters must not depend on foreign `api.query`, `api.action`, `api.model`;
- inbound adapters must not depend directly on same-domain domain repositories or domain models except narrow event type inputs for Spring event listener bridges;
- public same-domain controller/listener/job/handler entry points call same-domain `*ApplicationService` only;
- `application.port` packages are either retired across all domains or explicitly limited to technical ports with no Spring/Web/HTTP types.

The first batch should prefer failing tests first. If a rule would produce many unrelated failures, the implementation should narrow it to the affected packages first and document follow-up tightening.

### 5.2 User Avatar Upload Boundary

Controller remains responsible for HTTP multipart binding.

Target flow:

```text
UserController
  -> extracts MultipartFile
  -> converts to application-neutral upload source
  -> UserAvatarApplicationService
      -> AvatarStoragePort
          -> UserAvatarStorageAdapter
              -> AvatarService / AvatarStorageProvider
```

Application input should use Java/project-owned types. A minimal option is:

```text
AvatarUploadContent
  InputStream content
  String contentType
  long size
  boolean empty
```

`AvatarStoragePort.upload(...)` should accept this application type, not `MultipartFile`.

Infrastructure providers may still use `MultipartFile` internally only if the adapter wraps the application input into a provider-specific representation. Prefer changing provider APIs to the same neutral type so the transport boundary stays clean end to end.

`UserFileApplicationService` should expose:

```text
loadAvatarByKeyOrNull(String fileKey)
```

`FilesController` extracts the key from `/files/**`, decodes it, and passes it to application. Application validates the key format and loads the avatar.

Compatibility requirements:

- `/api/users/{id}/avatar/upload-token` behavior unchanged;
- avatar upload endpoint path and validation messages unchanged unless a current message is transport-specific;
- `/files/**` status, content type, content length, cache header, and `X-Content-Type-Options` behavior unchanged.

### 5.3 Search Outbox Handler Boundary

`PostOutboxHandler` should only adapt outbox payload into a search application command.

Target flow:

```text
PostOutboxHandler
  -> SearchPostProjectionApplicationService.projectPostFromOutbox(command)
      -> content.api.query.PostScanQueryApi
      -> PostSearchPayloadMapper
      -> SearchApplicationService or PostSearchRepository
```

The application service owns:

- current content state lookup;
- deleted/missing projection decision;
- mapping `PostProjectionView` into search sync command;
- invoking search indexing/delete behavior.

The handler owns:

- topic name;
- payload deserialization;
- null/blank payload handling;
- command construction.

Compatibility requirements:

- outbox topic remains `projection.search.post`;
- invalid JSON still fails with a clear illegal state exception;
- missing post projection still deletes the search index entry;
- existing search projection behavior remains unchanged.

### 5.4 Content Event Payload Assembly Boundary

Business event payload assembly should move out of infrastructure event adapters.

Target flow for post events:

```text
PostPublishingApplicationService / PostModerationApplicationService / PostScoreUpdateApplicationService
  -> publishes or schedules domain/application event with enough business identity
  -> ContentPostEventPayloadAssembler in application
      -> domain repositories
      -> ContentTextCodec
      -> contracts.event.PostPayload
  -> ContentEventPublisher port
      -> infrastructure event/outbox publisher
```

Infrastructure should:

- bridge Spring events to technical event publication;
- write outbox/local event using already assembled contract payload;
- avoid loading repositories to determine business event fields.

If preserving `@TransactionalEventListener(BEFORE_COMMIT)` is necessary, the listener can call a same-domain application service that assembles and publishes the payload. The listener itself must not own repository reads and contract assembly.

Compatibility requirements:

- published content contract event types and payload field names unchanged;
- post published/updated/deleted event timing remains before commit unless deliberately changed in a later reliability design;
- search/notice consumers keep receiving equivalent payloads.

### 5.5 Application Facade Cleanup Direction

The first batch does not remove facade layering, but new guardrails should document the desired direction.

Allowed end state options:

1. Controllers may inject multiple same-domain `*ApplicationService` classes, each representing a real use case group.
2. A domain may keep one public application entry service, but subordinate collaborators must not also be public `*ApplicationService` entry points.

The project should avoid a pattern where:

```text
Controller -> FooApplicationService -> BarApplicationService -> Repository
```

because it recreates facade service layering under approved names.

### 5.6 Cross-Domain Side Effect Direction

Synchronous foreign `api.action` remains allowed only when the caller needs immediate consistency or an immediate return value from the foreign owner domain.

For effects such as points, growth tasks, notification projections, and search projections, the preferred direction is:

```text
owner application
  -> publish contracts.event
foreign application listener
  -> idempotent projection/action
```

The first batch should not perform this conversion, but new code should not add more synchronous points/growth-style side effects without a documented strong consistency reason.

### 5.7 Domain Model Enrichment Direction

The first deep domain candidate should be market order.

Later design should move rules such as order creation, delivery, shipment, confirmation, cancellation, and status transitions into domain model/domain service methods. Application should become orchestration:

```text
load aggregate and collaborators
call domain behavior
persist result
publish event or enqueue side effect
return application result
```

Repository interfaces should move away from broad `markXxx` and `changeStatus` surfaces toward either aggregate save/update or intent-oriented methods that map directly to domain behavior.

---

## 6. Testing Strategy

### 6.1 Architecture Tests

Run focused architecture tests:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='com.nowcoder.community.app.arch.*' test
```

Expected after first batch:

- application has no `MultipartFile` / Spring Web dependency;
- inbound handler/bridge/enqueuer rules catch foreign API usage before application;
- listener/handler rules include `PostOutboxHandler`;
- documentation and ArchUnit rules agree on `application.port` policy.

### 6.2 Focused Unit Tests

Add or update tests for:

- `UserAvatarApplicationService`
  - rejects non-self actor;
  - delegates neutral upload content to `AvatarStoragePort`;
  - does not require Spring `MultipartFile` in application tests.
- `FilesController` / `UserFileApplicationService`
  - controller extracts and decodes file key from `/files/**`;
  - application validates file key format;
  - not found, content type, content length, and cache headers stay compatible.
- `PostOutboxHandler`
  - deserializes payload and calls search projection application service;
  - does not call `PostScanQueryApi`.
- search projection application service
  - missing content projection deletes index;
  - present projection syncs index using existing mapping.
- content event payload assembly
  - published post payload fields remain equivalent for publish/update/delete.

### 6.3 Regression Tests

Run relevant focused backend tests plus community-app test suite when feasible:

```bash
mvn -q -f backend/pom.xml -pl community-app -Dtest='*UserAvatar*,*UserFile*,*PostOutbox*,*Search*' test
mvn -q -f backend/pom.xml -pl community-app test
```

If the full suite is too slow or environment-dependent, the implementation report must list which commands ran and which were skipped.

---

## 7. Compatibility Rules

First batch must preserve:

- public HTTP routes;
- JSON response fields;
- business error codes;
- upload token semantics;
- avatar file serving semantics;
- outbox topic names and payload shape;
- content contract event type names and payload field names;
- search projection behavior for deleted/missing posts.

Allowed internal changes:

- application command/result class names;
- application-neutral upload content type;
- controller helper methods;
- application service names for newly introduced projection/event orchestration;
- ArchUnit rule coverage.

---

## 8. Migration Plan Shape

The implementation plan should be written after this spec is approved. It should use this order:

1. Add failing ArchUnit rules for application Web dependency and inbound handler boundaries.
2. Refactor user avatar upload and file-key boundary.
3. Refactor search outbox handler into thin adapter plus application projection service.
4. Move content post payload assembly to application-owned code.
5. Update handbook architecture docs.
6. Run focused tests and architecture tests.

Later plans should separately address:

- application facade cleanup;
- event-driven points/growth projections;
- market order domain model enrichment.

---

## 9. Risks And Mitigations

Risk: ArchUnit rules become too broad and fail unrelated infrastructure code.

Mitigation: start with precise packages/classes and remove exceptions only after each focused migration completes.

Risk: replacing `MultipartFile` changes upload validation behavior.

Mitigation: preserve existing validation messages and add tests around empty file, content type, size, and ownership checks.

Risk: moving outbox projection logic changes deleted-post search behavior.

Mitigation: create tests for missing content projection and existing projection before moving logic.

Risk: content event assembly timing changes.

Mitigation: keep current transaction phase in first batch. Move ownership of assembly, not delivery timing.

Risk: facade cleanup and rich domain modeling balloon the first batch.

Mitigation: keep those as later batches with separate specs/plans.

---

## 10. Acceptance Criteria

First batch is complete when:

- `application` production code has no Spring Web upload/HTTP transport dependencies;
- user avatar upload application port uses application-neutral input;
- `UserFileApplicationService` no longer parses raw request URI;
- `PostOutboxHandler` depends only on same-domain search application service plus technical outbox/deserialization dependencies;
- content post event payload assembly is application-owned, not infrastructure-owned;
- ArchUnit tests enforce the new boundaries;
- focused user/search/content tests pass;
- architecture tests pass;
- `docs/handbook/architecture.md`, `docs/handbook/system-design.md`, and this spec remain aligned.

Later batches are complete only when:

- auth/market/wallet no longer use application facade chains;
- points/growth projection side effects have documented sync-vs-async decisions;
- market order behavior is substantially expressed by domain model/domain service instead of application transaction script.
