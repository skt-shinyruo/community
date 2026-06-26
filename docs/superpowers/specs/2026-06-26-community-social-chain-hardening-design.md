# Community Social Chain Hardening Design

## Background

`social` currently owns likes, follows, and blocks inside `backend/community-app`, but the downstream behavior is inconsistent across default deployment modes.

The default runtime uses:

- `social.storage=db`
- `social.events.publisher=outbox-kafka`
- Kafka backbone topic `social.events`

Under that default, several downstream behaviors depend on local `SocialContractEvent` listeners instead of the Kafka backbone, which creates correctness gaps between local mode and production-like mode.

In addition, the like lifecycle is not modeled strongly enough to support:

- self-like rejection at the owner boundary
- reliable downstream revoke/compensation on unlike
- reliable downstream revoke/compensation on content-driven bulk cleanup
- safe growth-task rollback rules
- safe notification revocation rules
- safe concurrent like-count maintenance

This design hardens the end-to-end social interaction chain while preserving strict DDD tactical layering.

## Goals

- Make the default `outbox-kafka` deployment mode behaviorally correct.
- Make cross-domain consumers rely on `social.events` as the canonical asynchronous source.
- Reject self-like at the `social` owner boundary.
- Make unlike and cleanup produce the same downstream observable semantics.
- Revoke like notifications on unlike and cleanup.
- Roll back like-driven growth progress only when the progress is not yet claimed.
- Keep claimed growth progress append-only.
- Keep reward and count projections reversible on unlike and cleanup.
- Eliminate unsafe absolute-reset behavior in like-count maintenance.
- Explicitly constrain `social` to DB-backed storage.

## Non-Goals

- Redesign follow notifications or add follow-revocation semantics.
- Introduce a brand-new social event family beyond the existing like/follow/block contracts.
- Support `social.storage=redis` for production-like use.
- Redesign wallet reward semantics outside the existing like delta model.
- Rebuild notice as a separate notification service.

## Confirmed Product Decisions

### Self-Like

- Self-like is forbidden.
- The `social` owner domain rejects `entityType=USER && actorUserId==entityId`.
- No downstream module should need to special-case accepted self-like writes anymore.

### Cleanup Semantics

- Deleting a post or comment must behave as a sequence of standard like removals.
- `cleanupEntityLikes` must not silently bulk-delete likes without emitting downstream semantics.
- Cleanup must produce the same externally visible behavior as manual unlike.

### Growth Rollback

- Like-driven task progress is reversible only when the task progress is not yet claimed.
- If the corresponding task progress is already claimed, unlike and cleanup do not roll back the task.
- Reward and count projections still roll back normally.

### Notice Rollback

- Like notices must be revoked on unlike and cleanup.
- Revocation may be implemented as a status/lifecycle transition rather than hard delete.
- Revoked notices must no longer count as active unread/visible like notices.

### Storage Boundary

- `social.storage=redis` is formally unsupported for this chain.
- Application startup must fail fast when social is configured outside the supported DB mode.

## Chosen Architecture

### Canonical Async Backbone

For cross-domain asynchronous collaboration, `social.events` becomes the only canonical source of social interaction events.

Required consequence:

- IM policy downstream projection must consume `social.events` rather than local `SocialContractEvent`.
- Content post-score downstream projection must consume `social.events` rather than local `SocialContractEvent`.
- Notice, growth, and user reward continue to consume `social.events`.

Local `SocialContractEvent` may still exist for same-process, same-domain best-effort behavior, but it must not be required for correctness in the default deployment mode.

### Stable Like Identity

The like contract needs two identifiers with separate meanings:

- `eventId`: unique per emitted event, used for message-level idempotency.
- `relationKey`: stable identity of a like relation, shared by `LikeCreated` and `LikeRemoved` for the same actor/entity pair.

Recommended `relationKey` format:

```text
like:<actorUserId>:<entityType>:<entityId>
```

`relationKey` is the key used for:

- notice revocation
- reward reversal
- growth progress rollback lookup
- cleanup parity with manual unlike

### Owner-Domain Like Semantics

The `social` owner remains responsible for:

- validation
- entity ownership resolution
- block checks
- relation mutation
- like-count mutation
- standard event publication

The owner domain must emit the same event contract for:

- manual unlike
- cleanup caused by post deletion
- cleanup caused by comment deletion

This design intentionally avoids a separate cleanup-only event family.

## Package and Layering Shape

All new behavior must stay inside existing DDD boundaries:

- `social.controller` continues to bind HTTP only.
- `social.application` owns orchestration for like, unlike, and cleanup emission.
- `social.domain` owns self-like prohibition and stable relation identity semantics.
- `social.infrastructure.persistence` owns DB scan/delete/count implementations.
- `social.infrastructure.event` owns outbox and Kafka publication.
- foreign domains continue to enter `social` only through `api.query` and `api.action`.

No downstream module may call `social` repositories or persistence directly.

## Social Owner Changes

### 1. Reject Self-Like

Add explicit domain validation:

- if `entityType == USER`
- and `actorUserId.equals(entityId)`
- reject the command with a business error

This closes the current inconsistency where downstream modules ignore self-like but social accepts it.

### 2. Enrich Like Domain Event and Contract

Add the following fields to the like event shape:

- `relationKey`
- `occurredAt`

Contract compatibility rules:

- keep existing fields such as `actorUserId`, `entityType`, `entityId`, `entityUserId`, `postId`
- add new fields without removing existing ones
- consumers may migrate incrementally

### 3. Replace Silent Bulk Cleanup

Current bulk cleanup behavior must be replaced.

Instead of:

- count likes
- bulk delete rows
- reset owner counts
- emit no social event

the owner flow must become:

1. scan likes for the target entity in pages
2. for each existing like relation:
   - remove the relation
   - decrement the owner like count atomically
   - emit standard `LikeRemoved`
3. continue until the entity has no likes left

Behavioral requirement:

- if no relation exists, no event is emitted
- cleanup must be idempotent
- cleanup may be chunked, but each removed relation must produce one standard `LikeRemoved`

### 4. Remove Unsafe Absolute Count Reset from the Normal Cleanup Path

The current DB cleanup path reads the current owner count and then overwrites it with an absolute reset value, which is unsafe under concurrent likes on other entities.

The hardened path must use only atomic deltas:

- `+1` on `LikeCreated`
- `-1` on `LikeRemoved`

Absolute resets may remain only in dedicated administrative repair/rebuild tooling, not in the normal request-driven social chain.

### 5. Fail Fast on Unsupported Storage

Startup validation must enforce:

- `social.storage=db`

If not satisfied, application startup must fail with a clear message that the strict social/IM chain requires DB-backed social storage.

## Downstream Changes

### Notice

#### Required Behavior

- `LikeCreated` creates an active like notice.
- `LikeRemoved` revokes the matching active like notice.
- cleanup-triggered removals revoke notices the same way.
- follow notice behavior remains append-only.

#### Persistence Requirement

The notice read model needs explicit fields that allow reliable revoke without parsing `contentJson`.

Recommended additions:

- `source_event_type`
- `source_relation_key`
- `lifecycle_status` or `revoked_at`

Required semantics:

- active like notices are counted by notice list/count/unread views
- revoked like notices are excluded from active list/count/unread views
- revocation is idempotent

Hard delete is not required. Status-based revocation is preferred because it preserves audit history.

### Growth

#### Required Behavior

- `LikeCreated` keeps the current progress-add behavior.
- `LikeRemoved` adds a reverse path.
- cleanup-triggered removals must behave the same as manual unlike.

#### Rollback Rule

Rollback is based on the original recorded social relation identity, not on the unlike timestamp.

Reason:

- the unlike may happen on a different business day than the original like
- the original task progress must be reversed in the same progress bucket that consumed the original like

#### Event Log Strategy

The growth event log must support reverse lookup by relation identity.

Required capability:

- find the previously recorded like-progress contribution created from a given `relationKey`

Recommended approach:

- persist `relationKey` or equivalent stable source identity in the growth event log
- on `LikeRemoved`, look up previously recorded, unclaimed like-driven progress contributions

Rollback rules:

- if the corresponding progress row is `CLAIMED`, do nothing
- if the corresponding progress row is `CLAIMABLE` or `IN_PROGRESS`, decrement `currentValue` by 1
- if progress drops below target, transition `CLAIMABLE -> IN_PROGRESS`
- if progress is no longer reached, clear `reachedAt`
- remove or mark reverted the matching event-log record so the relation can be re-added later

### User Reward

Reward logic stays structurally the same:

- `LikeCreated` adds reward delta
- `LikeRemoved` subtracts reward delta

Required refinement:

- source identity must be unified around the stable like relation identity
- cleanup-triggered removals must reverse reward the same way as manual unlike

### IM Policy

The IM block-policy projection must not rely on local social events for correctness.

Required change:

- add a Kafka consumer for `social.events`
- consume `BlockRelationChanged`
- transform that event into the existing IM outbox/event dispatch flow

This preserves current IM projection semantics while aligning the trigger path with the default backbone deployment mode.

### Content Post Score

Post score projection must also move to the backbone.

Required change:

- add a Kafka consumer for `social.events`
- process `LikeCreated` and `LikeRemoved` where `entityType == POST`
- enqueue `postId` into `PostScoreQueue`

The local listener should not remain the only correctness path.

## Data Model Changes

### Social Contracts

Extend `LikePayload` with:

- `relationKey`
- `occurredAt`

`occurredAt` represents the action timestamp for the emitted event.

### Notice Read Model

Extend the notice persistence model for like projections with revocation metadata.

Minimum required fields:

- `sourceRelationKey`
- revocation state

### Growth Event Log

Extend growth task event logging so like contributions can be reversed by stable relation identity.

At minimum, the persisted log must let the system answer:

- which task progress rows consumed the like identified by `relationKey`
- whether that contribution is still eligible for reversal

### Historical Migration Policy

Historical rows that predate the new stable relation metadata are handled with an explicit best-effort policy.

Policy:

- new semantics are guaranteed for newly created likes after rollout
- historical likes created before rollout are not required to gain full retroactive revoke/rollback coverage
- notice and growth reverse handling for pre-rollout rows may be partial if the old row cannot be matched reliably
- no mandatory full backfill is required in this project scope

## Event Delivery and Idempotency

### General Rule

Every downstream consumer must treat incoming social events as at-least-once delivered.

### Notice

- create path must be idempotent by `eventId`
- revoke path must be idempotent by `relationKey`

### Growth

- create path remains idempotent by source event identity
- reverse path must be idempotent by relation identity and current progress state

### Reward

- wallet source ids remain idempotent
- created and removed source ids must not collide with each other

### IM / Content Score

- repeated block-change or post-score enqueue events must be harmless

## Rollout Strategy

### Phase 1: Backbone Correctness

- add Kafka consumer path for IM block-policy projection
- add Kafka consumer path for content post-score projection
- keep existing local listeners temporarily only as compatibility fallback

### Phase 2: Owner Like Semantics

- reject self-like
- add `relationKey` and `occurredAt` to like events
- enforce DB-backed social storage at startup

### Phase 3: Cleanup Hardening

- replace silent bulk cleanup with paged standard unlike emission
- remove absolute-reset count maintenance from the normal cleanup path

### Phase 4: Downstream Reverse Semantics

- add notice revocation
- add growth unclaimed-progress rollback
- unify reward reverse source identity

### Phase 5: Cleanup of Compatibility Paths

- remove or demote local-event correctness paths once backbone paths are verified

## Testing Strategy

### Social Owner Tests

- self-like is rejected
- normal like emits `LikeCreated` with `relationKey`
- normal unlike emits `LikeRemoved` with the same `relationKey`
- cleanup emits one `LikeRemoved` per previously existing relation
- cleanup is idempotent
- concurrent normal like activity is not lost during cleanup
- startup validation fails when `social.storage!=db`

### Notice Tests

- `LikeCreated` creates an active notice
- `LikeRemoved` revokes the matching active notice
- repeated `LikeRemoved` is idempotent
- revoked notice is excluded from active unread/count/list views

### Growth Tests

- `LikeCreated` increments progress
- `LikeRemoved` rolls back unclaimed progress
- `LikeRemoved` does not roll back claimed progress
- repeated `LikeRemoved` is idempotent
- re-like after unlike can increment again

### Reward Tests

- `LikeCreated` adds reward
- `LikeRemoved` subtracts reward
- cleanup-triggered removed behaves the same as manual unlike

### IM / Content Backbone Tests

- `BlockRelationChanged` from `social.events` reaches IM dispatch
- post `LikeCreated/LikeRemoved` from `social.events` enqueues score refresh

### Architecture / Guardrail Tests

- if package boundaries or event adapters move, update ArchUnit coverage accordingly

## Risks and Mitigations

### Risk: Cleanup Event Storm

Large content deletions may emit many `LikeRemoved` events.

Mitigation:

- page cleanup work
- ensure consumers are idempotent
- monitor topic throughput and lag

### Risk: Historical Rows Without Relation Metadata

Old notices or growth logs may not be fully reversible if they lack stable relation identity.

Mitigation:

- guarantee correctness for new events after rollout
- add targeted backfill only if required

### Risk: Dual Trigger Paths During Migration

Temporary coexistence of local and backbone listeners can cause duplicate handling.

Mitigation:

- retain strict idempotency in all consumers
- remove legacy correctness paths after validation
