# Community App Growth Task-Progress Application-Layer Convergence Design

**Date:** 2026-04-24
**Status:** Proposed for planning
**Owner:** Codex

---

## 1. Goal

Complete the remaining deferred application-layer convergence in `backend/community-app/` by collapsing the `growth` task-progress write entry into a single owner application entry while keeping foreign-domain collaboration stable.

The specific problem is the current owner-domain write path:

`foreign domain -> GrowthTaskProgressActionApi -> TaskProgressTriggerService -> TaskProgressProjectionService -> TaskProgressService`

This path works functionally, but it still keeps multiple owner entry layers alive at once:

- `TaskProgressTriggerService` acts like an entry adapter
- `TaskProgressProjectionService` acts like a second owner orchestration layer
- `TaskProgressService` is the real transactional worker

That shape conflicts with the long-term application-layer rule already established for `community-app`:

- same-domain owner entry should be a small `*ApplicationService`
- foreign domains should keep calling owner-domain `api.*`
- owner implementation details should stay behind the owner application entry

---

## 2. Scope And Non-Goals

### 2.1 In Scope

- `growth` task-progress write entry only
- convergence of `TaskProgressTriggerService` + `TaskProgressProjectionService`
- keeping `GrowthTaskProgressActionApi` stable for foreign callers
- test and documentation updates needed to reflect the new owner entry shape

### 2.2 Out Of Scope

- `ops` controller behavior
- reward/account legacy surface cleanup
- `UnifiedGrantService`
- `RewardAccountService`
- `LegacyRewardAccountQueryApi`
- wallet/growth accounting redesign

### 2.3 Non-Goals

- do not change the external behavior of task-progress awarding
- do not rename foreign-domain contracts used by `content` or `social`
- do not introduce a heavier CQRS or handler hierarchy
- do not combine unrelated reward/account migrations into this slice

---

## 3. Current State

### 3.1 Foreign Callers

Current foreign callers already depend on the owner-domain contract, which is the correct boundary:

- `CreatePostUseCase` calls `GrowthTaskProgressActionApi.triggerPostPublished(...)`
- `CommentService` calls `GrowthTaskProgressActionApi.triggerCommentCreated(...)`
- `LikeService` calls `GrowthTaskProgressActionApi.triggerLikeCreated(...)`

This part should remain stable.

### 3.2 Owner Implementation Drift

Inside `growth`, the owner implementation is still split across two entry-like classes:

- `TaskProgressTriggerService`
  - creates `ContentContractEvent` / `SocialContractEvent`
  - synthesizes event ids such as `post-published:<postId>`
- `TaskProgressProjectionService`
  - translates events into `TaskProgressProjectionCommand`
  - computes `bizDate` through `GrowthBusinessTimeService`
  - filters unsupported inputs such as self-like cases
- `TaskProgressService`
  - executes the transactional task-progress update
  - enforces idempotency via `user_task_event_log`
  - advances progress rows and auto-grants rewards

The resulting owner path is more layered than needed and preserves legacy naming (`TriggerService`, `ProjectionService`) as if they were independent owner entry surfaces.

---

## 4. Design Options

### 4.1 Recommended: Single Owner Application Entry

Keep `GrowthTaskProgressActionApi` unchanged, and replace the two owner entry layers with:

`foreign domain -> GrowthTaskProgressActionApi -> TaskProgressApplicationService -> TaskProgressService`

Benefits:

- keeps foreign-domain call sites stable
- removes legacy multi-entry naming
- matches the approved application-layer style
- keeps `TaskProgressService` focused on transaction logic only

### 4.2 Minimal Rename Only

Inline `TaskProgressProjectionService` into `TaskProgressTriggerService`, but keep `TaskProgressTriggerService` as the owner implementation.

This is smaller, but still leaves a legacy entry name and does not fully align with the long-term style.

### 4.3 Wider Growth Rollout

Converge task-progress plus reward/account legacy surfaces in one pass.

This would be more complete in theory, but it mixes two separate migration problems and makes verification much riskier.

**Decision:** choose **4.1**.

---

## 5. Target Architecture

### 5.1 Stable Foreign Boundary

Keep the existing owner-domain contract:

`GrowthTaskProgressActionApi`

Its public methods stay:

- `triggerPostPublished(UUID postId, UUID userId, Instant createTime)`
- `triggerCommentCreated(CommentPayload payload)`
- `triggerLikeCreated(String sourceEventId, LikePayload payload)`

Foreign domains continue to call this contract without knowing any owner implementation details.

### 5.2 New Owner Entry

Create:

- `TaskProgressApplicationService implements GrowthTaskProgressActionApi`

It becomes the single owner write entry for task-progress orchestration.

Responsibilities:

- translate post/comment/like inputs into the owner task-progress command shape
- normalize missing or invalid inputs to no-op
- compute `bizDate` using `GrowthBusinessTimeService`
- preserve current self-like filtering behavior
- call `TaskProgressService.processEvent(...)`

### 5.3 Internal Transaction Worker

Keep:

- `TaskProgressService`

Responsibilities remain unchanged:

- template lookup
- event-log idempotency
- progress row initialization
- progress update
- reward auto-grant

`TaskProgressService` stays an internal owner implementation, not a foreign collaboration surface.

### 5.4 Removed Owner Entry Layers

Retire:

- `TaskProgressTriggerService`
- `TaskProgressProjectionService`

Their responsibilities move into `TaskProgressApplicationService`.

This is not a behavior change. It is an owner entry convergence.

---

## 6. Behavioral Rules To Preserve

The new implementation must keep all current task-progress semantics:

- post publish uses synthetic source event id `post-published:<postId>`
- comment created uses synthetic source event id `comment-created:<commentId>`
- like created uses the upstream `sourceEventId`
- `bizDate` continues to come from `GrowthBusinessTimeService.dateOf(...)`
- self-like does not award task progress
- missing ids or timestamps remain safe no-op inputs
- `TaskProgressService.processEvent(...)` remains the only transactional mutation path

---

## 7. Testing Strategy

### 7.1 Owner Entry Tests

Replace `TaskProgressTriggerServiceTest` with `TaskProgressApplicationServiceTest`.

These tests should verify:

- post publish maps to `POST_PUBLISHED` with correct synthetic source id and `bizDate`
- comment create maps to `COMMENT_CREATED` with correct synthetic source id and `bizDate`
- like create maps to `LIKE_CREATED` for the entity owner
- self-like remains no-op
- invalid or incomplete inputs remain no-op

### 7.2 Transaction Core Tests

Keep:

- `TaskProgressServiceTest`
- `TaskProgressServiceUnitTest`

These continue to protect:

- idempotent event handling
- period key semantics
- reward grant behavior
- duplicate-row recovery

### 7.3 Foreign Caller Regression

Keep the existing foreign caller tests green:

- `CreatePostUseCaseTest`
- `CommentServiceTest`
- `LikeServiceTest`

They should continue to assert that foreign domains use `GrowthTaskProgressActionApi`, not owner internals.

### 7.4 Retirement Check

Add or extend a retirement test so the following classes no longer remain on the classpath:

- `com.nowcoder.community.growth.service.TaskProgressTriggerService`
- `com.nowcoder.community.growth.service.TaskProgressProjectionService`

---

## 8. Documentation Changes

Update growth-focused business logic documentation so task-progress entry ownership reflects the new shape:

- old wording: `TaskProgressProjectionService` translates upstream events
- new wording: `TaskProgressApplicationService` is the owner task-progress application entry, and `TaskProgressService` is the internal transactional executor

The broader application-layer documents should also record that the deferred long-term rollout now narrows from “growth/ops” to the remaining growth reward/account legacy surface only.

---

## 9. Migration Sequence

1. add `TaskProgressApplicationService`
2. move trigger/projection translation logic into it
3. update growth owner-entry tests first
4. rewire production code to the new owner entry
5. retire `TaskProgressTriggerService` and `TaskProgressProjectionService`
6. update docs and retirement tests
7. run focused suite
8. run full `community-app` test suite

---

## 10. Risks And Mitigations

### 10.1 Risk: Silent Semantic Drift

Because the existing trigger/projection layers mostly no-op on invalid input, a careless refactor could accidentally change no-op behavior into exceptions.

Mitigation:

- lock no-op cases in the new `TaskProgressApplicationServiceTest`

### 10.2 Risk: Foreign Caller Churn

If this migration changes `GrowthTaskProgressActionApi`, the refactor spreads into `content` and `social`.

Mitigation:

- keep `GrowthTaskProgressActionApi` unchanged in this slice

### 10.3 Risk: Scope Expansion

It is tempting to merge reward/account legacy cleanup into the same rollout.

Mitigation:

- explicitly leave `UnifiedGrantService`, `RewardAccountService`, and `LegacyRewardAccountQueryApi` out of scope

---

## 11. Recommended End State

After this slice, the task-progress path should be:

`CreatePostUseCase / CommentService / LikeService`
`-> GrowthTaskProgressActionApi`
`-> TaskProgressApplicationService`
`-> TaskProgressService`

This gives `growth` task-progress one clear owner application entry without disturbing its foreign collaboration boundary or transactional core.
