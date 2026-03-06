# Flatten Backend Monolith Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Collapse the backend into one backend Maven module, remove internal RPC semantics, replace local Kafka/outbox workflows with local events, thin content APIs, and push authorization ownership back into each domain package.

**Architecture:** Keep `backend/community-bootstrap` as the only backend module and preserve package-level domain boundaries inside it. Replace inter-module transport abstractions with local application services and local transactional domain events. Security ownership moves from one central path matrix to per-domain rule contributors assembled by bootstrap.

**Tech Stack:** Java 17, Spring Boot 3.2, Spring Security, Spring events, MyBatis, Redis, Elasticsearch, JUnit 5, ArchUnit, Maven

---

### Task 1: Freeze Baseline and Document Scope

**Files:**
- Create: `docs/plans/2026-03-06-flatten-backend-monolith-design.md`
- Create: `docs/plans/2026-03-06-flatten-backend-monolith-implementation-plan.md`

**Step 1: Verify backend baseline**

Run: `cd backend && mvn -q -pl :community-bootstrap -am test`
Expected: PASS

**Step 2: Save approved design and plan**

Write the approved design and this plan into `docs/plans/`.

**Step 3: Do not commit unless user requests**

Keep changes uncommitted in the worktree because the current session has not been asked to create commits.

### Task 2: Collapse Maven Modules into One Backend Module

**Files:**
- Modify: `backend/pom.xml`
- Modify: `backend/community-bootstrap/pom.xml`
- Delete: `backend/auth-service/pom.xml`
- Delete: `backend/user-service/pom.xml`
- Delete: `backend/content-service/pom.xml`
- Delete: `backend/social-service/pom.xml`
- Delete: `backend/message-service/pom.xml`
- Delete: `backend/search-service/pom.xml`
- Delete: `backend/analytics-service/pom.xml`
- Delete: `backend/ops-service/pom.xml`
- Delete: `backend/platform/pom.xml`
- Move: `backend/auth-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/user-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/content-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/social-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/message-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/search-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/analytics-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/ops-service/src/**` -> `backend/community-bootstrap/src/**`
- Move: `backend/platform/**/src/**` -> `backend/community-bootstrap/src/**`

**Step 1: Write a failing architecture/build test**

Create or update a test in `backend/community-bootstrap/src/test/java/com/nowcoder/community/bootstrap/arch/BackendFlatteningArchTest.java` that asserts:

- no production classes are loaded from old Maven-module-only source roots
- package layering rules are validated inside `community-bootstrap`

**Step 2: Run the test and watch it fail**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=BackendFlatteningArchTest test`
Expected: FAIL until the build structure is flattened

**Step 3: Flatten module layout**

- reduce `backend/pom.xml` to one child module
- merge dependencies into `backend/community-bootstrap/pom.xml`
- move sources/resources/tests into `community-bootstrap`
- delete obsolete module POMs

**Step 4: Re-run targeted architecture/build tests**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=BackendFlatteningArchTest test`
Expected: PASS

### Task 3: Replace Internal RPC with Local Application Services

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/SocialBlockClient.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/like/RpcLikeQueryService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/service/ContentServiceClient.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/api/rpc/SocialReadRpcService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/api/rpc/SocialBlockRpcService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/api/rpc/ContentScanRpcService.java`
- Delete or replace internal-only `*RpcServiceImpl` classes once callers are migrated
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/application/BlockQueryApplicationService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/application/LikeQueryApplicationService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostScanApplicationService.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/like/LikeQueryApplicationServiceTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/service/SocialBlockAccessTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/search/service/PostScanApplicationServiceTest.java`

**Step 1: Write failing tests for local collaboration**

Add tests asserting:

- content code reads likes/blocks through local application services without `Result`
- search reindex scans posts through a local application service without RPC wrappers
- no synthetic timeout/degraded/service-unavailable branch remains in local call paths

**Step 2: Run targeted tests and watch them fail**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=LikeQueryApplicationServiceTest,SocialBlockAccessTest,PostScanApplicationServiceTest test`
Expected: FAIL

**Step 3: Implement local services and migrate callers**

- introduce local application services
- inject them directly into content/search callers
- remove `InternalClientSupport` from local flows
- remove `Result<T>` from local collaboration signatures

**Step 4: Re-run targeted tests**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=LikeQueryApplicationServiceTest,SocialBlockAccessTest,PostScanApplicationServiceTest test`
Expected: PASS

### Task 4: Replace Kafka/Outbox Local Projections with Spring Transactional Events

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/domain/event/PostDomainEventPublisher.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/domain/event/PostPublishedDomainEvent.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/domain/event/PostUpdatedDomainEvent.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/domain/event/PostDeletedDomainEvent.java`
- Delete/replace: Kafka producer and outbox bridge classes under `content/event`, `content/kafka`, `search/kafka`, and matching message listeners
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/service/PostSearchService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/**`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/analytics/**`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/message/event/NotificationProjectionListenerTest.java`

**Step 1: Write failing tests for local event dispatch**

Create tests that assert:

- post publish/update/delete emits a local event
- search projection listener updates index after commit
- notification listener creates notifications after commit

**Step 2: Run tests and watch them fail**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=PostProjectionListenerTest,NotificationProjectionListenerTest test`
Expected: FAIL

**Step 3: Implement local event infrastructure**

- back domain event publisher with Spring events
- register listeners with `@TransactionalEventListener(phase = AFTER_COMMIT)`
- remove Kafka consumers/producers, outbox glue, consumed-event stores, and replay-only local code

**Step 4: Re-run tests**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=PostProjectionListenerTest,NotificationProjectionListenerTest test`
Expected: PASS

### Task 5: Simplify Search Reindex Coordination to Single-Process State

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/service/ReindexJobService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/rpc/SearchOpsRpcServiceImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/service/PostSearchService.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/search/service/ReindexJobServiceTest.java`

**Step 1: Write failing tests for single-process coordination**

Assert:

- only one reindex job can run at a time
- a second request gets the running job id
- no Redis lock/renewal behavior is required

**Step 2: Run test and watch it fail**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=ReindexJobServiceTest test`
Expected: FAIL

**Step 3: Implement in-memory single-flight reindex state**

- replace Redis lock keys and renewal scheduler with in-memory coordination
- preserve current error/reporting semantics where practical

**Step 4: Re-run the test**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=ReindexJobServiceTest test`
Expected: PASS

### Task 6: Thin `PostController` and Normalize Content Application Layer

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/api/PostController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/PostService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostQueryApplicationService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostCommandApplicationService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostRepresentationAssembler.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/api/PostControllerTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/application/PostQueryApplicationServiceTest.java`

**Step 1: Write failing tests for thin-controller behavior**

Assert:

- controller delegates to application services
- query composition and DTO assembly happen outside the controller
- create/update paths perform text handling and idempotent use-case orchestration through application services

**Step 2: Run targeted tests and watch them fail**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=PostControllerTest,PostQueryApplicationServiceTest test`
Expected: FAIL

**Step 3: Implement application services and simplify controller**

- move query aggregation and write orchestration into `application`
- keep `PostController` focused on HTTP adaptation
- reduce `PostService` toward domain/repository-facing responsibilities only

**Step 4: Re-run targeted tests**

Run:
- `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=PostControllerTest,PostQueryApplicationServiceTest test`
Expected: PASS

### Task 7: Push Security Rules into Domain-Owned Contributors

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/bootstrap/security/CommunitySecurityConfig.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/bootstrap/security/ApiSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/api/security/AuthSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/security/UserSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/api/security/ContentSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/api/security/SocialSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/api/security/SearchSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/analytics/api/security/AnalyticsSecurityRules.java`
- Create: `backend/community-bootstrap/src/main/java/com/nowcoder/community/ops/api/security/OpsSecurityRules.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/bootstrap/security/CommunitySecurityConfigTest.java`

**Step 1: Write failing security assembly tests**

Assert:

- bootstrap assembles contributor beans
- each domain rule contributor applies its own path rules
- representative public and restricted endpoints keep current behavior

**Step 2: Run tests and watch them fail**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=CommunitySecurityConfigTest test`
Expected: FAIL

**Step 3: Implement contributor model**

- define `ApiSecurityRules`
- register one contributor per domain
- simplify `CommunitySecurityConfig` to shared cross-cutting setup plus contributor assembly

**Step 4: Re-run tests**

Run: `cd backend && mvn -q -pl :community-bootstrap -am -Dtest=CommunitySecurityConfigTest test`
Expected: PASS

### Task 8: Rewrite Architecture Documentation and Final Verification

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Modify: `backend/README.md`

**Step 1: Update docs to match flattened backend**

Describe:

- one backend module
- package-level boundaries
- local application services
- local transactional events
- domain-owned security rules

**Step 2: Run full backend verification**

Run: `cd backend && mvn -q -pl :community-bootstrap -am test`
Expected: PASS

**Step 3: Run full reactor verification**

Run: `cd backend && mvn -q test`
Expected: PASS

**Step 4: Summarize remaining follow-up work**

Document any intentionally deferred cleanups, especially secondary controllers that still need layering normalization after `PostController` becomes the template.

---

Plan complete and saved to `docs/plans/2026-03-06-flatten-backend-monolith-implementation-plan.md`. Since you already asked me to execute this refactor in the current session, I’ll continue here rather than stopping for a separate execution handoff.
