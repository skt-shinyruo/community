# Growth Reward Progress Idempotency Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix growth task reward amount, like event idempotency, like removal routing, level completion counting, and period type validation.

**Architecture:** Keep all orchestration inside `growth.application` and all technical event/SQL details inside `growth.infrastructure`. Growth still calls wallet only through `WalletRewardActionApi`, and wallet remains the final balance ledger owner.

**Tech Stack:** Spring Boot, Java, MyBatis XML mappers, JUnit 5, AssertJ, Mockito.

## Global Constraints

- Do not introduce a growth ledger or new public controller.
- Do not change claimed-like rollback semantics.
- Preserve strict DDD package layering under `backend/community-app`.
- Use failing tests before production code changes.

---

### Task 1: Wallet Reward Amount

- Add a failing `TaskProgressApplicationServiceTest` proving wallet reward amount equals `reward_balance_delta`, not growth plus balance.
- Change automatic reward amount computation to use balance delta only.
- Verify the targeted test passes.

### Task 2: Like Source Event Id Consistency

- Add a failing `TaskProgressEventBackboneKafkaListenerTest` for missing `relationKey` fallback.
- Change backbone fallback to `like-created:<actorUserId>:<entityType>:<entityId>`.
- Verify the targeted test passes.

### Task 3: Like Removed Outbox Routing

- Add failing enqueuer and dispatch tests for `LIKE_REMOVED`.
- Dispatch removed payloads to the existing like-removed topic.
- Verify enqueuer and dispatch tests pass.

### Task 4: Level Completion Counting

- Add a failing `UserLevelApplicationServiceTest` showing incomplete check-in rows do not count.
- Add repository and mapper support for completed-only progress counts.
- Verify the targeted test passes.

### Task 5: Period Type Validation

- Add a failing `TaskProgressDomainServiceTest` for unsupported period type.
- Make unsupported nonblank period types throw `IllegalArgumentException`.
- Verify the targeted test passes.

### Task 6: Focused Regression Suite

- Run focused growth regression tests.
- Inspect the final diff for scoped changes only.
