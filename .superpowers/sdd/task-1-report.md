# Task 1 Report: Event Backbone Metadata

## Scope

Task 1 added backbone metadata to the content and social outbox contract envelopes, propagated the metadata through publisher and dispatch paths, extended post payload assembly with `updateTime`, and updated the Task 1 plan checkboxes.

## TDD Evidence

### RED

Added failing Task 1 coverage first in:

- `OutboxContentEventPublisherTest`
- `ContentEventDispatchApplicationServiceTest`
- `OutboxSocialDomainEventPublisherTest`
- `SocialEventDispatchApplicationServiceTest`

Exact command run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest
```

Observed failure:

- test compilation failed because `ContentContractEvent` and `SocialContractEvent` did not expose `aggregateId`, `aggregateType`, `occurredAt`, or `version`
- the old three-argument record constructors no longer matched the new test expectations

### GREEN

After the minimal production changes, reran the same command:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 41, Failures: 0, Errors: 0, Skipped: 0`

## Implementation

- Expanded `ContentContractEvent` and `SocialContractEvent` to carry:
  - `eventId`
  - `aggregateId`
  - `aggregateType`
  - `type`
  - `occurredAt`
  - `version`
  - `payload`
- Kept a three-argument compatibility constructor in both records so unchanged downstream consumers/tests can still compile without widening Task 1 scope.
- Added `updateTime` to `PostPayload`.
- Updated `ContentPostPayloadAssembler` to map `DiscussPost.updateTime` into the contract payload.
- Updated `OutboxContentEventPublisher` to stamp backbone metadata for post, comment, and moderation envelopes.
- Updated `LocalContentEventPublisher` to stamp the same backbone metadata for local-mode events.
- Updated `BlockApplicationService`, `BlockRelationChangedDomainEvent`, and `BlockPayload` so block events carry source-authored `occurredAt` and version.
- Updated `OutboxSocialDomainEventPublisher` to stamp backbone metadata for like, follow, and block envelopes.
- Updated `LocalSocialDomainEventPublisher` to stamp the same backbone metadata for local-mode events.
- Updated both dispatch application services to parse and validate `aggregateId`, `aggregateType`, `occurredAt`, and positive `version` before dispatching.
- Updated Task 1 tests to assert metadata serialization and missing-metadata rejection.
- Updated Task 1 checkboxes and synchronized the Task 1 file map with the block-event files required by the implementation.

## Files Changed

- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/ContentContractEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/SocialContractEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/PostPayload.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentPostPayloadAssembler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/OutboxContentEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
- `docs/superpowers/plans/2026-07-06-community-content-platform-high-concurrency-implementation-plan.md`

## Self-Review

- The metadata validation now guarantees both dispatch services reject incomplete outbox envelopes early.
- Publisher metadata is deterministic and owner-authored for `occurredAt`; publishers reject missing source timestamps instead of fabricating publish time.
- The compatibility constructors keep unchanged downstream tests and adapters source-compatible, while Task 1 publishers use full envelopes.
- The focused Maven commands stayed green after the changes; final verification evidence is recorded in the later review-fix sections.

## Concerns

- Non-block event `version` remains the plan's positive timestamp-derived fallback until Task 2/Task 5 introduce source metadata on projection commands.

## Review fix

### RED

Added review-driven coverage for:

- `LocalContentEventPublisherTest`
- `LocalSocialDomainEventPublisherTest`
- `OutboxSocialDomainEventPublisherTest`

Focused command run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest,LocalSocialDomainEventPublisherTest,PostHotFeedProjectionLocalListenerTest,LocalContentEventPublisherTest
```

Observed failure:

- `BlockRelationChangedDomainEvent` had no constructor carrying source `occurredAt`
- `BlockPayload` had no `occurredAt` accessor
- local publishers still depended on removed compatibility constructors for envelope population

### GREEN

Reran the same focused command after the fixes.

Result:

- `BUILD SUCCESS`
- `Tests run: 48, Failures: 0, Errors: 0, Skipped: 0`

### Files changed

- `backend/community-app/src/main/java/com/nowcoder/community/content/contracts/event/ContentContractEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/SocialContractEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisher.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/LocalContentEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/LocalSocialDomainEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/OutboxSocialDomainEventPublisherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionLocalListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/PostHotFeedProjectionKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/event/ContentEventKafkaSenderAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/infrastructure/event/SocialEventKafkaSenderAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/PostOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/search/infrastructure/event/SearchPostProjectionKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/PostTaskProgressKafkaOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/CommentTaskProgressOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/LikeTaskProgressKafkaOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/infrastructure/event/TaskProgressEventBackboneKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/infrastructure/event/ImPolicyBackboneKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/infrastructure/event/NoticeProjectionKafkaListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`
- `docs/superpowers/plans/2026-07-06-community-content-platform-high-concurrency-implementation-plan.md`

## Review fix 2

### RED

Added review-driven coverage for malformed outbox metadata in:

- `ContentEventDispatchApplicationServiceTest`
- `SocialEventDispatchApplicationServiceTest`

Focused command run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest
```

Observed failure:

- malformed `aggregateId` escaped as raw `IllegalArgumentException`
- malformed `occurredAt` escaped as raw `DateTimeParseException`

### GREEN

Reran the same focused command after wrapping invalid metadata fields in controlled `IllegalStateException`s.

Result:

- `BUILD SUCCESS`
- `Tests run: 26, Failures: 0, Errors: 0, Skipped: 0`

Task 1 focused suite rerun after this fix:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest,LocalSocialDomainEventPublisherTest,PostHotFeedProjectionLocalListenerTest,LocalContentEventPublisherTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`

### Files changed

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`

## Review adjudication

The re-review raised two Important findings after the malformed metadata fix:

- Task scope did not list the block-domain files that were required to preserve source-authored block `occurredAt` and `version`.
- Non-block event `version` is timestamp-derived rather than an owner revision.

Resolution:

- Updated the Task 1 plan file map and git-add block to include:
  - `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/social/contracts/event/BlockPayload.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/social/domain/event/BlockRelationChangedDomainEvent.java`
- Added a Task 1 note clarifying that the current plan requires a positive backbone `version`; it uses existing owner-authored version where available (block relations) and the plan's timestamp-derived fallback for post/comment/like/follow events until Task 2/Task 5 source metadata is introduced for replay-safe projection consumers.

Evidence checked:

- Codebase search found existing owner revision support for block relations only (`BlockRepository.nextBlockProjectionVersion`, `BlockPayload.version`).
- Plan consistency section already states `ProjectPostHotFeedCommand` gains `sourceVersion` in Task 2 and search/notice commands gain source metadata in Task 5 before replay-safe consumption logic uses them.

Verification after plan/report synchronization:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest,LocalSocialDomainEventPublisherTest,PostHotFeedProjectionLocalListenerTest,LocalContentEventPublisherTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 52, Failures: 0, Errors: 0, Skipped: 0`

## Review fix 3

### RED

Added review-driven coverage for missing source timestamps in:

- `OutboxContentEventPublisherTest`
- `LocalContentEventPublisherTest`
- `OutboxSocialDomainEventPublisherTest`
- `LocalSocialDomainEventPublisherTest`

Focused command run:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,LocalContentEventPublisherTest,OutboxSocialDomainEventPublisherTest,LocalSocialDomainEventPublisherTest
```

Observed failure:

- all four new missing-source-timestamp tests failed because publishers fabricated `occurredAt` via `Instant.now()` instead of rejecting missing owner-authored event time

### GREEN

Reran the same focused command after replacing the fallback with required source timestamp validation.

Result:

- `BUILD SUCCESS`
- `Tests run: 28, Failures: 0, Errors: 0, Skipped: 0`

Additional check:

- codebase search for `Instant.now` in the four content/social publishers returned no matches

## Review fix 4

Restored three-argument compatibility constructors on `ContentContractEvent` and `SocialContractEvent` so unchanged downstream code can keep constructing legacy-shaped events while Task 1 production publishers emit the full metadata envelope. Removed constructor-only churn from non-Task-1 tests.

Verification command:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxContentEventPublisherTest,ContentEventDispatchApplicationServiceTest,OutboxSocialDomainEventPublisherTest,SocialEventDispatchApplicationServiceTest,LocalSocialDomainEventPublisherTest,LocalContentEventPublisherTest,ContentEventKafkaSenderAdapterTest,PostHotFeedProjectionKafkaListenerTest,PostHotFeedProjectionLocalListenerTest,CommentTaskProgressOutboxEnqueuerTest,LikeTaskProgressKafkaOutboxEnqueuerTest,PostTaskProgressKafkaOutboxEnqueuerTest,TaskProgressEventBackboneKafkaListenerTest,ImPolicyBackboneKafkaListenerTest,NoticeProjectionKafkaListenerTest,PostOutboxEnqueuerTest,SearchPostProjectionKafkaListenerTest,SocialEventKafkaSenderAdapterTest,CommentRewardOutboxEnqueuerTest,UserRewardKafkaListenerTest
```

Result:

- `BUILD SUCCESS`
- `Tests run: 109, Failures: 0, Errors: 0, Skipped: 0`
