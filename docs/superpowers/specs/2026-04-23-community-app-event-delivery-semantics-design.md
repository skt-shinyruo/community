# Community App Event Delivery Semantics Alignment Design

**Date:** 2026-04-23
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

Remove the hidden semantics switch created by `events.outbox.enabled` inside `community-app`.

The current problem is not simply that the codebase has listeners and outbox handlers. The actual problem is that the same business outcome changes delivery semantics by environment:

- some paths become `AFTER_COMMIT` best-effort local listeners when the flag is `false`
- the same paths become `BEFORE_COMMIT` outbox enqueue plus async retry when the flag is `true`
- tests default to `false` while main runtime defaults to `true`

The target end state is:

- each business path has one stable delivery model
- the model is chosen by architectural boundary, not by environment toggle
- tests exercise the same delivery model as production

This design is intentionally narrow. It fixes delivery-semantics drift. It does not redesign the whole event taxonomy.

---

## 2. Problem Statement

`community-app` currently publishes local Spring application events for content and social changes. Several downstream consumers then split into two implementations behind `@ConditionalOnProperty(events.outbox.enabled=...)`.

Representative examples:

- points:
  - `PointsProjectionListener`
  - `PointsOutboxEnqueuer`
  - `PointsOutboxHandler`
- search:
  - `PostProjectionListener`
  - `PostOutboxEnqueuer`
  - `PostOutboxHandler`
- notice:
  - `NoticeProjectionListener`
  - `NoticeOutboxEnqueuer`
  - `NoticeOutboxHandler`
- task progress:
  - `TaskProgressProjectionListener`
  - `TaskProgressOutboxEnqueuer`
  - `TaskProgressOutboxHandler`

This creates four concrete problems:

1. Same business behavior has different consistency and failure semantics in different environments.
2. Tests default to a different path than production.
3. Operational debugging requires first identifying which semantic mode was active.
4. Outbox is being used for same-process business collaboration that does not justify queue, retry, dead-letter, and worker complexity.

---

## 3. Options Considered

### 3.1 Option A: Keep the Toggle but Align Defaults Everywhere

Set `events.outbox.enabled=true` everywhere, including tests, and keep both implementations.

Pros:

- minimal code change
- production and tests stop diverging by default

Cons:

- hidden semantic switch still exists
- same-process points and task-progress still pay outbox complexity
- accidental future config drift reintroduces the same bug class

Decision: rejected.

### 3.2 Option B: Move Everything to Outbox

Delete all local listeners and force points, notice, task-progress, and search through outbox.

Pros:

- single implementation style
- retryable delivery everywhere

Cons:

- same-process business collaboration becomes queue-driven without boundary justification
- business writes inside the monolith become eventually consistent by default
- operational complexity expands further

Decision: rejected.

### 3.3 Option C: Fix Semantics Per Boundary

Use outbox only where the target is a real external integration or externalized projection store. Keep same-process business collaboration local and explicit.

Pros:

- stable semantics per business path
- reduced operational surface area
- clearer mental model
- aligns delivery choice with architectural boundary

Cons:

- requires deleting existing dual-track implementations
- points and task-progress need explicit orchestration instead of generic event fan-out

Decision: accepted.

---

## 4. Architectural Decisions

### 4.1 Global Outbox Toggle Must Not Select Business Semantics

`events.outbox.enabled` must stop deciding whether points, notice, task-progress, and search use different delivery models.

After this change:

- no business path should have both local-listener and outbox implementations selected by that property
- runtime configuration may still control outbox infrastructure bootstrapping if needed, but not the business boundary choice
- in this implementation slice, `community-app` profiles that include search projection should continue to bootstrap outbox infrastructure because search remains outbox-backed

### 4.2 Search Is an Externalized Projection and Keeps Outbox

`search.storage=es` means post search projection writes to Elasticsearch, which is an externalized projection store and a legitimate outbox boundary.

Search therefore keeps:

- enqueue before commit
- async worker delivery
- retry/dead handling
- current-state reconstruction in handler

Search will have one path only.

### 4.3 Points and Task Progress Are Same-Process Business Collaboration

Points writes local wallet state through in-app services. Task progress writes local task state and may immediately grant rewards through local wallet services.

These are not generic best-effort projections. They are business effects inside the same application and same persistence boundary.

They should become explicit local application orchestration, not event-driven dual-track projection code.

### 4.4 Notice Is a Local Read-Side Concern

Current notice projection writes local notice records in the same application. It does not justify generic outbox machinery.

Notice should keep one local implementation. Because notice creation is not a hard precondition for the original command success, it can remain post-commit best-effort.

If the system later introduces real external notice delivery such as email, push, or webhook, those outbound integrations should get their own dedicated outbox boundary.

---

## 5. Target End State

### 5.1 Search

Search projection uses only:

- `PostOutboxEnqueuer`
- `PostOutboxHandler`

Delete:

- `PostProjectionListener`

Behavior stays as it is today in outbox-enabled production:

- relevant content events enqueue before commit
- handler reconstructs projection from current DB state
- worker retries failures

### 5.2 Points

Points no longer project from generic content/social event listeners.

Introduce a narrow local application collaborator, for example:

- `PointsAwardService`
- or `UserEngagementRewardOrchestrator`

Owning use cases call it directly when they complete the business action:

- post published
- comment created
- like created
- like removed

Delete:

- `PointsProjectionListener`
- `PointsOutboxEnqueuer`
- `PointsOutboxHandler`

The new local service keeps the current command mapping and idempotent reward request id format, but the orchestration becomes explicit instead of hidden behind event fan-out.

### 5.3 Task Progress

Task progress no longer projects from generic content/social/growth events through listener or outbox dual tracks.

Introduce an explicit local collaborator around `TaskProgressService.processEvent(...)`.

Owning use cases call it directly for:

- post published
- comment created
- like created
- check-in completed

Delete:

- `TaskProgressProjectionListener`
- `TaskProgressOutboxEnqueuer`
- `TaskProgressOutboxHandler`

This makes task progress and reward issuance local business orchestration with one transaction model.

### 5.4 Notice

Notice keeps a single local listener implementation:

- `NoticeProjectionListener`

Delete:

- `NoticeOutboxEnqueuer`
- `NoticeOutboxHandler`

Notice remains:

- local
- post-commit
- best-effort with warning logs

That matches its role as a local inbox/read-side concern.

---

## 6. Required Code Changes

### 6.1 Remove Conditional Bean Switching

Delete `@ConditionalOnProperty(events.outbox.enabled=...)` from the affected business delivery beans by collapsing each boundary to one implementation.

The codebase should no longer express:

- one listener path for `false`
- one outbox path for `true`

for the same business responsibility.

### 6.2 Replace Event-Driven Business Effects with Explicit Local Calls

Points and task progress should move from broad event subscription to explicit calls from the use cases that own the behavior.

This means updating the relevant application services in:

- content write flows
- social write flows
- growth check-in flow

The call sites should be close to the original successful business action, not hidden behind a catch-all projection listener.

### 6.3 Keep Outbox Infrastructure Only for Real Outbox Boundaries

Common outbox infrastructure remains because search still uses it.

That includes:

- `JdbcOutboxEventStore`
- `OutboxWorker`
- `OutboxWorkerScheduler`
- outbox table and retry model

But the infrastructure surface in `community-app` becomes smaller because only search remains on it in this slice.

### 6.4 Align Test Configuration with Production Semantics

Tests must stop relying on `events.outbox.enabled=false` as the default semantic mode.

Expected changes:

- production and test configs no longer intentionally select different business paths
- profiles that load search projection should default to outbox infrastructure enabled in both production and test
- search tests either run with outbox enabled or drain the outbox deterministically in test helpers
- points, task-progress, and notice tests validate their single retained implementation only

---

## 7. Rollout Plan

### 7.1 Phase 1: Eliminate Semantic Drift

Deliverables:

- search has only outbox path
- notice has only local listener path
- points has only explicit local orchestration path
- task progress has only explicit local orchestration path
- tests stop depending on opposite defaults

### 7.2 Phase 2: Clean Up Event Surface

After Phase 1 stabilizes, review remaining local contract events and remove subscriptions that are no longer needed for same-process business collaboration.

This phase is cleanup only. It is not required to achieve semantic alignment.

---

## 8. Error Handling Model

### 8.1 Search

Keep current outbox behavior:

- enqueue failure before commit fails the original transaction
- handler failure retries asynchronously
- repeated failure eventually moves event to `DEAD`

### 8.2 Points

Points becomes part of local business orchestration.

If reward application fails, the local use case should fail according to normal local business rules. The system should not silently degrade this path through generic best-effort event handling.

### 8.3 Task Progress

Task progress becomes part of local business orchestration.

If task progress or reward issuance fails, the owning local use case should see the failure directly. This is preferable to hidden divergence between environments.

### 8.4 Notice

Notice stays best-effort after commit. Failures remain warnings, because notice projection is not treated as a commit-critical business invariant in this slice.

---

## 9. Testing Strategy

Tests should be rewritten around stable semantics:

- search:
  - enqueue on relevant post events
  - worker handling updates ES/in-memory repository from current DB state
  - retry/dead behavior remains covered
- points:
  - post/comment/like use cases call local reward collaborator directly
  - idempotent request id format remains unchanged
- task progress:
  - post/comment/like/check-in use cases call local task-progress collaborator directly
  - reward issuance behavior remains covered through local orchestration tests
- notice:
  - local post-commit listener remains covered
  - no outbox notice tests remain

Integration tests should no longer assert one semantic path in test and a different path in production.

---

## 10. Risks And Mitigations

### 10.1 Risk: Points and Task Progress Touch Many Call Sites

Mitigation:

- keep the new collaborators narrow
- preserve existing command mapping logic where possible
- change only the owning use cases that currently lead to those events

### 10.2 Risk: Search Tests Become Harder Because They Must Respect Outbox

Mitigation:

- add deterministic test helpers to poll or drain outbox
- keep unit tests at handler/enqueuer level
- keep integration tests explicit about async boundary

### 10.3 Risk: Notice Reliability Becomes Visibly Lower Than Search

Mitigation:

- make the distinction explicit in docs and tests
- treat notice as local inbox projection, not integration delivery
- only introduce dedicated outbox again if a real outbound boundary appears

---

## 11. Non-Goals

This design does not:

- replace Spring application events everywhere
- redesign contract event naming
- split `community-app` into services
- remove outbox infrastructure entirely
- introduce Kafka or another broker

The only purpose of this slice is to stop one config flag from changing business delivery semantics inside the monolith.
