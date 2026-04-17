# Community App Notice Boundary, Layering, And Local Collaboration Design

**Date:** 2026-04-17
**Status:** Approved for planning
**Owner:** Codex

---

## 1. Goal

Reset `backend/community-app/` onto a simpler and more enforceable backend architecture by addressing three connected problems:

- `B`: `notice` still depends directly on `message` DTOs and entities, so the owner boundary is not real
- `C`: some domains, especially `content`, now contain overlapping layers such as `api`, `app/use case`, thin action services, and legacy command services
- `D`: same-JVM collaboration is still described and sometimes modeled as if it were a remote module call, with pseudo-remote semantics like `SERVICE_UNAVAILABLE`, `module callback`, and `fail-closed`

This design covers all three problems, but the first implementation slice only delivers `B`.

The end state is not a package-scoped pseudo-modular monolith with mixed collaboration styles. The end state is a simpler single deployable where:

- owner domains define their own models
- synchronous in-process collaboration is local collaboration
- layering is easier to reason about and harder to bypass accidentally

---

## 2. Scope And Rollout Strategy

### 2.1 In Scope

- `backend/community-app/src/main/java/com/nowcoder/community/notice/**`
- `backend/community-app/src/main/java/com/nowcoder/community/message/**`
- `backend/community-app/src/main/java/com/nowcoder/community/content/**`
- synchronous collaboration helpers that currently describe same-process calls as remote/module callbacks
- architecture tests and architecture documentation that define these boundaries

### 2.2 First Implementation Slice

The first implementation slice is strictly limited to `B`:

- make `notice` own its own DTOs, entity, mapper contract, and service return types
- remove production dependencies from `notice` to `com.nowcoder.community.message.*`
- delete the temporary architecture-test allowlist that permits `notice -> message` type reuse
- update docs and tests so the new owner boundary is explicit and enforced

### 2.3 Explicitly Deferred To Later Slices

The first slice does **not** do the following:

- replace the underlying `message` table
- redesign the outbox/event topology for notice projection
- refactor all of `content` into a single new structure
- remove every pseudo-remote semantic in the codebase

Those belong to later slices for `C` and `D`.

---

## 3. Architectural Decisions

### 3.1 Community App Should Use A Simpler Single-Deployable Layering Model

`community-app` should be treated as a traditional layered monolith, not as a pseudo-distributed module system.

Default domain structure:

- `controller`: HTTP boundary only
- `service`: owner use cases and domain orchestration
- `mapper`: persistence access
- `entity`: owner persistence model
- `dto`: owner request/response model
- `api`: narrow synchronous collaboration interfaces for foreign callers
- `contracts.event`: asynchronous collaboration model

This means a domain does **not** need `app`, `use case`, `action service`, `command service`, and forwarding wrappers unless each layer has a distinct and durable responsibility.

### 3.2 Same-JVM Collaboration Is Local Collaboration

For any collaboration that happens inside `community-app`, the semantic model is:

- local method call
- local validation failure
- local not-found / forbidden / conflict behavior

It is **not**:

- remote callback
- downstream unavailability
- pseudo-service degraded state
- fail-closed because another package in the same Spring application is "unavailable"

Only real cross-process boundaries may use availability/degradation semantics, such as:

- `community-gateway -> community-app`
- `community-app -> community-im`
- external infra such as Redis, MySQL, Elasticsearch, Kafka

### 3.3 Each Owner Domain Must Own Its Types

An owner domain may reuse a shared library from `common` or `infra`, but must not reuse another business domain's owner types as its own primary model.

For `notice`, this means:

- `notice.controller` cannot expose `message.dto.*`
- `notice.service` cannot return `message.entity.Message`
- `notice.mapper` cannot accept or return `message.entity.Message`
- `notice` tests should validate `notice` types, not `message` types

Storage reuse is allowed temporarily, but type ownership is not optional.

---

## 4. Problem Analysis

### 4.1 B: Notice Is Not A Real Owner Domain Yet

Current state:

- `NoticeController` exposes `message.dto.LetterItemResponse`, `message.dto.MarkReadRequest`, and `message.dto.NoticeTopicSummaryResponse`
- `NoticeService` returns `List<Message>` and `List<LetterItemResponse>`
- `NoticeMapper` accepts and returns `message.entity.Message`
- `notice_mapper.xml` hard-codes notice semantics on top of the `message` table
- `ArchitectureRulesSupport.TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN` explicitly whitelists this leakage

This is not a migration detail anymore. It is the current architecture.

### 4.2 C: Content Has Overlapping Layers

Current state in `content` mixes several patterns at once:

- `PostController -> PostPublishingActionApi`
- `PostPublishingActionService -> CreatePostUseCase / UpdatePostUseCase / DeleteOwnPostUseCase`
- `PostModerationActionService -> TopPostUseCase / MarkPostWonderfulUseCase / AdminDeletePostUseCase`
- `PostCommandService` still exists as a separate write-path service, but no longer owns the main production path

This shape is harder to maintain because multiple classes exist to represent the same use-case boundary, and some are now effectively migration leftovers.

### 4.3 D: Local Collaboration Still Carries Pseudo-Remote Semantics

Representative examples:

- `social.service.ContentEntityResolver`
- `content.service.UserModerationGuard`
- `im.governance.UserModerationGuard`

These same-process calls are documented or modeled as if one package were remotely calling another package. That produces misleading behavior names, misleading tests, and misleading failure handling.

---

## 5. Target End State

### 5.1 Notice End State

`notice` becomes a normal owner domain:

- `notice.dto.NoticeItemResponse`
- `notice.dto.NoticeTopicSummaryResponse`
- `notice.dto.MarkNoticeReadRequest`
- `notice.entity.NoticeRecord`
- `notice.mapper.NoticeMapper`
- `notice.service.NoticeService`

Projection events from `content` and `social` still create notice records, but all projection output is mapped into `notice` owner types before persistence and before HTTP responses.

### 5.2 Content End State

`content` should eventually collapse back toward a simpler layered shape:

- controllers call owner services directly
- owner services orchestrate mapper calls and event publication
- only durable cross-domain synchronous collaboration APIs remain under `content.api`
- thin forwarding classes and duplicate write-path wrappers are removed

This does **not** require removing every helper class. It requires removing redundant layers whose only value is renaming or forwarding.

### 5.3 Local Collaboration End State

Inside `community-app`:

- local collaboration helpers may still exist, but their naming and failure model must be local
- incomplete local data should produce explicit local business errors, not pseudo-remote unavailability
- tests should verify local business semantics, not fake remote semantics

---

## 6. Phase Plan

### 6.1 Phase 1: Notice Boundary Reset

Deliverables:

- `notice` owns all primary types
- `notice` no longer imports `message` production types
- architecture allowlists for `notice -> message` are removed
- notice tests pass against the new owner model
- architecture docs describe `message` as removed from primary owner role inside `community-app`

### 6.2 Phase 2: Content Layer Simplification

Deliverables:

- identify the true owner write/read services in `content`
- inline or delete redundant forwarding layers
- remove dead or no-longer-owned command-service wrappers
- preserve narrow `content.api.*` contracts for foreign callers

### 6.3 Phase 3: Local Collaboration Semantic Cleanup

Deliverables:

- rename or replace pseudo-remote helper classes
- remove `SERVICE_UNAVAILABLE` and similar semantics from same-JVM collaboration paths
- rewrite tests to assert local business behavior
- document that availability semantics are only valid at real process boundaries

---

## 7. Detailed Design For Phase 1

### 7.1 New Notice Types

Create notice-owned types:

- `notice.entity.NoticeRecord`
  Fields mirror the current persisted columns used by notice:
  - `id`
  - `senderUserId`
  - `recipientUserId`
  - `topic`
  - `content`
  - `status`
  - `createTime`

- `notice.dto.NoticeItemResponse`
  HTTP response for listing individual notices

- `notice.dto.NoticeTopicSummaryResponse`
  HTTP response for the notice summary endpoint

- `notice.dto.MarkNoticeReadRequest`
  Request payload for mark-read

The first phase may keep integer status values and existing topic strings:

- `comment`
- `like`
- `follow`
- `moderation`

This avoids mixing type-owner migration with behavior redesign.

### 7.2 Notice Mapper Contract

`NoticeMapper` keeps its current query responsibility, but changes signatures to notice-owned types.

Allowed in phase 1:

- SQL still reads and writes the existing `message` table
- legacy sender compatibility remains in mapper SQL
- current topic filtering remains in mapper SQL

Not allowed in phase 1:

- `parameterType="com.nowcoder.community.message.entity.Message"`
- `resultType="com.nowcoder.community.message.entity.Message"`
- returning `List<Message>` to notice service callers

### 7.3 Notice Service Contract

`NoticeService` becomes the sole owner service for notice read/write use cases:

- create notice projection rows
- list notice items
- count unread notices
- mark notices as read
- summarize notice topics

If `NoticeItemAssembler` is still only field-copying after the type migration, it should be inlined into `NoticeService` or converted into a notice-local helper. It must not survive as a wrapper around foreign types.

### 7.4 Notice Controller Contract

`NoticeController` should expose only `notice.dto.*` types.

Because the user explicitly allows API changes, endpoint payloads may change names if needed. However, the recommended phase-1 path is:

- keep the same endpoints
- keep the same basic JSON shape where practical
- change only type ownership and Java-side naming first

This keeps the rollout focused on architecture instead of mixing in frontend migration.

### 7.5 Message Package Treatment

After phase 1, the `message` package should no longer have an owner role in production code under `community-app`.

Expected result:

- no production imports from `notice` to `message`
- architecture tests no longer whitelist `notice -> message`
- remaining `message` package content is either deleted or left only as short-lived dead-end legacy code pending later cleanup

Because current repository search shows production `message` usage is effectively limited to `notice`, this cleanup is expected to be much simpler than a shared-core extraction.

---

## 8. How Phase 1 Enables Phases 2 And 3

Phase 1 is the template for the later work:

- it proves that owner domains can own their models even when they temporarily reuse old storage
- it removes one of the clearest examples of cross-domain type leakage
- it gives a repeatable pattern for later domain cleanup: move owner types local first, then simplify orchestration, then delete migration leftovers

For `C`, this supports collapsing `content` back to clearer owner services without preserving legacy wrapper classes just because they already exist.

For `D`, this reinforces that same-process collaboration is local code composition, not package-to-package remote service negotiation.

---

## 9. Error Handling Rules

### 9.1 Phase 1

No behavior redesign is required for notice projection failures in phase 1. Existing event projection behavior may remain:

- listener path logs projection failures after commit
- outbox path serializes notice projection commands before commit

The phase-1 requirement is only that projection writes and read models use `notice` owner types.

### 9.2 Future Phases

For same-JVM collaboration in `community-app`, local collaboration helpers must use local business error semantics:

- invalid argument
- forbidden
- not found
- conflict
- invariant violation

They must not pretend another domain package inside the same Spring application is a remote dependency that became unavailable.

---

## 10. Validation And Governance

### 10.1 Architecture Tests

Phase 1 must update architecture tests to reflect the real target state:

- remove `TEMPORARY_SHARED_MESSAGE_TYPES_BY_ORIGIN`
- remove assertions that expect `notice` to reuse `message` types
- keep `notice` as a first-class governed domain

### 10.2 Regression Tests

Phase 1 must preserve:

- notice sentinel sender id `0`
- legacy sender id `1` compatibility for known notice topics
- exclusion of real user-to-user conversations such as `"1_2"` from notice reads

These are already represented in `NoticeServiceTest` and should remain covered after the owner-type migration.

### 10.3 Documentation

Update architecture documentation so it no longer describes `message` as notice's current owner-side model.

The docs should say:

- `notice` owns notice read/write semantics
- `message` is no longer the primary type owner for notice inside `community-app`
- future cleanup may remove the legacy `message` package entirely

---

## 11. Risks And Mitigations

### 11.1 Risk: Mixing Type Migration With Storage Migration

If phase 1 tries to also replace the `message` table, the slice becomes too large.

Mitigation:

- keep the existing table in phase 1
- move only the type owner boundary

### 11.2 Risk: Content Refactor Starts Before Notice Boundary Is Stable

If phase 2 starts before phase 1 finishes, the codebase will continue changing its internal rules while the first rule change is still incomplete.

Mitigation:

- finish `B` completely first
- remove the `notice -> message` allowlist before beginning `C`

### 11.3 Risk: Pseudo-Remote Semantics Continue To Spread

Even after phase 1, new code could still introduce local-remote confusion.

Mitigation:

- document the rule explicitly
- enforce it with follow-up architecture tests during phase 3

---

## 12. Recommended Conclusion

The correct way to resolve `B+C+D` is to simplify the architecture rather than add another abstraction layer.

Recommended rollout:

1. reset `notice` into a real owner domain
2. remove cross-domain type leakage and the matching architecture-test allowlists
3. then simplify `content` and other mixed-shape domains back toward a clearer layered structure
4. then clean same-JVM collaboration semantics so they behave like local code, not fake remote services

Phase 1 should therefore be intentionally narrow and complete:

- own the notice types locally
- keep the old table temporarily
- delete the `notice -> message` dependency from production code
- lock the new boundary with tests and docs

That creates a stable base for the deeper `C` and `D` refactors without turning the first implementation slice into an uncontrolled rewrite.
