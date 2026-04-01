# Community App Persistence Strategy Alignment Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Align `community-app` data access strategy by keeping direct mapper usage as the default owner-domain pattern while pushing `social` storage-selection details behind repository abstractions.

**Architecture:** Keep the project's cross-domain API boundary unchanged. Refactor only `social` write services so backend-specific compensation policy is expressed by repositories, then document and guard the rule that repositories are reserved for domains whose write semantics genuinely vary by storage backend.

**Tech Stack:** Spring Boot, MyBatis, JUnit 5, AssertJ, ArchUnit

---

### Task 1: Lock The Intended Behavior With Regression Tests

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/social/service/FollowServiceTest.java`

- [ ] Add failing tests proving that a compensating repository rolls back state if social event publication fails.
- [ ] Run the two targeted tests and confirm they fail for the expected reason before changing production code.

### Task 2: Move Storage Compensation Policy Behind Repository Abstractions

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/InMemoryLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/RedisLikeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/InMemoryFollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/RedisFollowRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowService.java`

- [ ] Introduce a repository-level capability for explicit compensation.
- [ ] Refactor `LikeService` to use repository semantics instead of reading `social.storage`.
- [ ] Refactor `FollowService` to use repository semantics instead of reading `social.storage`.
- [ ] Re-run the targeted social service tests and confirm green.

### Task 3: Encode The Rule In Docs And A Focused Guard

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`

- [ ] Add a focused test that prevents `LikeService` and `FollowService` from injecting `social.storage` directly again.
- [ ] Add an architecture doc section that explains when direct mapper access is the default and when a repository/port is justified.

### Task 4: Verify The Change Set

**Files:**
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/FollowServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`

- [ ] Run the focused regression and architecture tests.
- [ ] Review the diff to confirm the change stayed inside the agreed scope.
