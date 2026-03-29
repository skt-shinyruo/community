# Community App Architecture Governance Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `backend/community-app` so its package boundaries become enforceable, cross-domain collaboration goes only through owner-domain `api.query` / `api.action` / `api.model`, and the current facade/controller/mapper leaks are removed without regressing existing behavior.

**Architecture:** Start by locking the current architecture debt behind ArchUnit guardrails so the tree stops drifting. Then introduce explicit owner-domain collaboration APIs beginning with `user`, because it is the current high-fanout dependency source. After the `user` boundary is stable, split `content` into explicit query/action collaboration surfaces, remove `PostFacadeService`, clean controller adapter leaks, and finally unify duplicated projection logic behind owner-domain application services before tightening the guardrails to their final form.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, MyBatis, Micrometer, Maven, JUnit 5, ArchUnit

---

## File Structure Map

### Guardrails and architecture tests

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
  Role: lock package-boundary rules so no new foreign `entity` / `mapper` / `service` dependencies or facade classes are added while the migration is in progress.

### `user` owner-domain collaboration API

- `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserIdentityQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserModerationQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserSummaryQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRegistrationActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserProfileActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserIdentityView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserSummaryView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserModerationView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserAuthenticationResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserRegistrationResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserIdentityQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserModerationQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserSummaryQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserCredentialActionApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserRegistrationActionApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserProfileActionApiImpl.java`
  Role: expose the first explicit owner-domain collaboration surface because `user` is currently the repository’s implicit public kernel.

### Existing callers to migrate away from foreign `user` internals

- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminGrowthService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageUserQueryService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageGovernanceService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`
  Role: replace direct `user.service` / `user.mapper` / `user.entity` consumption with owner-domain APIs and delete transitional wrappers.

### `content` owner-domain collaboration API and facade removal

- `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/PostQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/PostScanQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/ContentEntityQueryApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/PostActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostSummaryView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostDetailView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/UserRecentCommentView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostScanView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/ResolvedContentEntityView.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostScanQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/ContentEntityQueryApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostActionApiImpl.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostFacadeService.java`
  Role: replace the current mixed facade and service leakage with explicit read/write collaboration surfaces and remove the oversized content boundary class.

### Existing callers to migrate away from `PostFacadeService` or foreign content services

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/PostSearchService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`
  Role: move all content collaboration behind owner-domain APIs and stop depending on content internals or the giant facade.

### Adapter cleanup and shared assembly

- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/controller/NoticeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageItemAssembler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/assembler/PostSummaryAssembler.java`
  Role: remove controller-level mapper/entity use and eliminate repeated HTTP DTO assembly code from multiple endpoints.

### Projection deduplication

- `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/service/PostProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeProjectionService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeOutboxHandler.java`
  Role: remove duplicated business logic between transaction listeners and outbox handlers by delegating both paths to a single owner-domain projection service.

### Documentation

- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
  Role: update SSOT documentation so the written architecture matches the enforced collaboration model.

---

### Task 1: Add Domain Guardrails With A Migration Baseline

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `backend/community-app/pom.xml`

- [ ] **Step 1: Write failing architectural tests for the target rules**

  Cover:
  - non-owner domains must not depend on foreign `mapper`
  - non-owner domains must not depend on foreign `entity`
  - controller packages must not depend on foreign `service` / `entity` / `mapper`
  - new production classes must not end with `FacadeService`

- [ ] **Step 2: Run the architecture tests and capture the current violation list**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test`

  Expected:
  - RED, with known offenders such as `GrowthController`, `AdminGrowthService`, `UserController`, `AdminUserController`, `MessageUserQueryService`, and `PostFacadeService`

- [ ] **Step 3: Add a temporary migration whitelist so the baseline goes GREEN without hiding new regressions**

  Implement:
  - centralize the allowed legacy callers inside the architecture tests
  - include exact fully qualified class names only
  - do not whitelist whole packages

- [ ] **Step 4: Re-run the architecture tests and verify the migration baseline**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test`

  Expected:
  - GREEN, with the current legacy set frozen in place

- [ ] **Step 5: Checkpoint the diff for the guardrail task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Introduce `user.api` And Migrate `auth` / `growth`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserIdentityQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserModerationQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/query/UserSummaryQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRegistrationActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserIdentityView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserSummaryView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserModerationView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserAuthenticationResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserRegistrationResult.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserIdentityQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserModerationQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserSummaryQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserCredentialActionApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserRegistrationActionApiImpl.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJob.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/AdminGrowthService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/UnifiedGrantService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/PasswordResetServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/GrowthControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/AdminGrowthServiceTest.java`

- [ ] **Step 1: Write or update failing tests for owner-domain `user.api` usage**

  Cover:
  - auth login still authenticates and issues the same transport result shape
  - password reset and registration flows still use owner-domain validation
  - growth summary no longer depends on `user.entity.User`
  - admin growth queries and adjustments no longer use `UserMapper` directly

- [ ] **Step 2: Run the focused auth/growth test set and confirm RED**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=AuthServiceLoginTest,PasswordResetServiceTest,RegistrationServiceTest,RegistrationVerificationServiceTest,GrowthControllerTest,AdminGrowthServiceTest test`

- [ ] **Step 3: Implement `user.api.query` / `user.api.action` as the new owner-domain collaboration surface**

  Implement:
  - query APIs for id/username/email lookup, summaries, score-bearing identity reads, and moderation status reads
  - action APIs for authenticate, registration-related mutations, and user-profile mutations
  - narrow `api.model` types instead of leaking `user.entity.User`

- [ ] **Step 4: Migrate `auth` and `growth` callers to the new APIs**

  Implement:
  - replace `AuthService` direct `User` coupling with `UserAuthenticationResult` and owner-domain query views
  - replace `GrowthController` direct entity reads with `UserIdentityView`
  - replace `AdminGrowthService` direct `UserMapper` reads with `UserIdentityQueryApi`

- [ ] **Step 5: Re-run the focused auth/growth tests and the architecture baseline**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=AuthServiceLoginTest,PasswordResetServiceTest,RegistrationServiceTest,RegistrationVerificationServiceTest,GrowthControllerTest,AdminGrowthServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test`

- [ ] **Step 6: Checkpoint the diff for the `user.api` phase-one task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Migrate `message` / `content` User Consumers And Delete Transitional Wrappers

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageUserQueryService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageGovernanceService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/controller/ImGovernanceController.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageUserQueryService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/controller/MessageControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/PrivateMessageGovernanceServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/PrivateMessageServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/UserModerationGuardTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/UserModerationGuardTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/im/controller/ImGovernanceControllerTest.java`

- [ ] **Step 1: Write failing tests that describe the post-migration collaboration shape**

  Cover:
  - message send resolves target users through `user.api.query`
  - moderation checks use `UserModerationQueryApi`
  - IM governance stays behaviorally identical after the migration
  - no production caller still requires `MessageUserQueryService`

- [ ] **Step 2: Run the focused message/content/IM tests and confirm RED**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=MessageControllerTest,PrivateMessageGovernanceServiceTest,PrivateMessageServiceTest,com.nowcoder.community.message.service.UserModerationGuardTest,com.nowcoder.community.content.service.UserModerationGuardTest,ImGovernanceControllerTest test`

- [ ] **Step 3: Replace `MessageUserQueryService` semantics with owner-domain APIs**

  Implement:
  - move any still-useful username resolve cache behavior behind `user.api.query`
  - stop constructing message-local user lookup semantics on top of `user.entity.User`

- [ ] **Step 4: Delete the transitional wrapper and migrate all callers**

  Implement:
  - remove `MessageUserQueryService`
  - point `MessageController`, `PrivateMessageGovernanceService`, and moderation guards to `user.api.query`
  - confirm no production import remains for foreign `user.entity` or foreign `user.service`

- [ ] **Step 5: Re-run the focused test set and architecture baseline**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=MessageControllerTest,PrivateMessageGovernanceServiceTest,PrivateMessageServiceTest,com.nowcoder.community.message.service.UserModerationGuardTest,com.nowcoder.community.content.service.UserModerationGuardTest,ImGovernanceControllerTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test`

- [ ] **Step 6: Checkpoint the diff for the message/content user-collaboration task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Introduce `content.api` And Remove `PostFacadeService`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/PostQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/PostScanQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/query/ContentEntityQueryApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/action/PostActionApi.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostSummaryView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostDetailView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/UserRecentCommentView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/PostScanView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/model/ResolvedContentEntityView.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostScanQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/ContentEntityQueryApiImpl.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/api/internal/PostActionApiImpl.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/UserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/BookmarkController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/service/PostSearchService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostFacadeService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/controller/PostControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostFacadeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/service/SearchReindexExecutionServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/ContentEntityResolverTest.java`

- [ ] **Step 1: Write failing tests for the post-facade end state**

  Cover:
  - post list/detail/create/update/delete behavior is unchanged after removing the facade
  - user recent-posts and recent-comments endpoints still return the same semantics
  - bookmark listing still preserves last-activity and tag decoration
  - search reindex and social content-entity resolution no longer depend on foreign content services

- [ ] **Step 2: Run the focused content/search/social test set and confirm RED**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=PostControllerTest,UserControllerLoggingTest,PostFacadeServiceTest,SearchReindexExecutionServiceTest,ContentEntityResolverTest test`

- [ ] **Step 3: Introduce `content.api.query` / `content.api.action` and migrate all current consumers**

  Implement:
  - read APIs for post list/detail summaries, post scans, recent comments, and content-entity resolution
  - action APIs for post/comment mutations that currently sit behind the facade
  - owner-domain collaboration models instead of leaking HTTP response DTOs

- [ ] **Step 4: Delete `PostFacadeService` and move its remaining assembly logic to owner-domain implementations**

  Implement:
  - no controller or foreign domain should still import `PostFacadeService`
  - search and social consumers must call `content.api.query`
  - `UserController` must consume `content.api.query` rather than a foreign facade

- [ ] **Step 5: Replace or remove `PostFacadeServiceTest` with tests for the new owner-domain APIs**

  Implement:
  - delete facade-only tests once the class is removed
  - add focused tests for `PostQueryApiImpl` / `PostActionApiImpl` as needed

- [ ] **Step 6: Re-run the focused tests and the architecture baseline**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=PostControllerTest,UserControllerLoggingTest,SearchReindexExecutionServiceTest,ContentEntityResolverTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test`

- [ ] **Step 7: Checkpoint the diff for the content-boundary task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 5: Clean Controller Adapters And Centralize Repeated Assembly

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/controller/MessageController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/controller/NoticeController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/message/service/MessageItemAssembler.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/assembler/PostSummaryAssembler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/UserControllerLoggingTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/controller/MessageControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/controller/NoticeControllerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/NoticeServiceTest.java`

- [ ] **Step 1: Write failing tests that assert controllers no longer own domain assembly**

  Cover:
  - admin user search and role update still behave the same after moving mapper access behind owner-domain services
  - message and notice endpoints still return the same serialized fields after sharing one assembler
  - no controller still contains private `Message -> LetterItemResponse` or duplicate post-summary mapping logic

- [ ] **Step 2: Run the focused adapter tests and confirm RED**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=UserControllerLoggingTest,MessageControllerTest,NoticeServiceTest test`

- [ ] **Step 3: Move direct mapper/entity access out of controllers**

  Implement:
  - `AdminUserController` must call owner-domain APIs or services rather than `UserMapper`
  - `GrowthController` should already be clean from Task 2; confirm it no longer imports foreign entities
  - controllers should only bind transport input, read auth context, and wrap the returned DTO/view

- [ ] **Step 4: Centralize repeated response assembly**

  Implement:
  - create `MessageItemAssembler` used by both `NoticeService` and message endpoints
  - create `PostSummaryAssembler` or equivalent owner-domain assembly path so bookmark and post list logic stop drifting

- [ ] **Step 5: Re-run the focused tests and architecture baseline**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=UserControllerLoggingTest,MessageControllerTest,NoticeServiceTest,DomainBoundaryArchTest,ControllerBoundaryArchTest test`

- [ ] **Step 6: Checkpoint the diff for the adapter-cleanup task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 6: Unify Projection Logic Between Transaction Listeners And Outbox Handlers

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/PointsProjectionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/search/service/PostProjectionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeProjectionService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/event/PointsOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/event/PostOutboxHandler.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeProjectionListener.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/event/NoticeOutboxHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/event/PointsOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/event/PostOutboxHandlerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/event/NoticeProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/event/NoticeOutboxHandlerTest.java`

- [ ] **Step 1: Write failing tests that describe shared projection semantics**

  Cover:
  - transaction-listener and outbox-handler paths produce the same observable side effects
  - the owner-domain projection service owns business decisions, not the transport adapter class
  - retry/error handling remains adapter-specific

- [ ] **Step 2: Run the focused event/projection tests and confirm RED**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=PointsProjectionListenerTest,PointsOutboxHandlerTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest,PostProjectionListenerTest,PostOutboxHandlerTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest test`

- [ ] **Step 3: Introduce shared owner-domain projection services**

  Implement:
  - move points, task-progress, post-index, and notice projection decisions into dedicated services
  - leave listener/outbox classes as thin adapters that deserialize or subscribe and then delegate

- [ ] **Step 4: Re-run the focused event tests and verify GREEN**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=PointsProjectionListenerTest,PointsOutboxHandlerTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest,PostProjectionListenerTest,PostOutboxHandlerTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest test`

- [ ] **Step 5: Checkpoint the diff for the projection-deduplication task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 7: Tighten The Guardrails, Update SSOT Docs, And Run Full Verification

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Remove the temporary migration whitelist and switch the rules to the final architecture**

  Final rules:
  - cross-domain dependencies are allowed only to `api.query`, `api.action`, and `api.model`
  - non-owner domains may not depend on foreign `service`, `entity`, or `mapper`
  - controllers may not depend on any foreign domain implementation type
  - no `FacadeService` classes remain in production code

- [ ] **Step 2: Run the architecture tests until the final rule set is GREEN**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest test`

- [ ] **Step 3: Update written SSOT documentation to match the enforced code reality**

  Rewrite:
  - `docs/ARCHITECTURE.md` section 2.3 so it no longer says “聚焦 service 或 domain-owned dto”
  - `docs/SYSTEM_DESIGN.md` internal collaboration rules so they point to owner-domain APIs, not generic direct service calls

- [ ] **Step 4: Run focused regression suites across the migrated areas**

  Run:
  - `cd backend && mvn -q -pl community-app -Dtest=AuthServiceLoginTest,PasswordResetServiceTest,RegistrationServiceTest,RegistrationVerificationServiceTest,GrowthControllerTest,AdminGrowthServiceTest,MessageControllerTest,PrivateMessageGovernanceServiceTest,PrivateMessageServiceTest,PostControllerTest,SearchReindexExecutionServiceTest,ContentEntityResolverTest,PointsProjectionListenerTest,PointsOutboxHandlerTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest,PostProjectionListenerTest,PostOutboxHandlerTest,NoticeProjectionListenerTest,NoticeOutboxHandlerTest test`

- [ ] **Step 5: Run the broader module verification**

  Run:
  - `cd backend && mvn -q -pl community-app test`

- [ ] **Step 6: Record the final architecture outcomes in the review summary**

  Confirm:
  - exact command list run
  - `PostFacadeService` removed
  - `MessageUserQueryService` removed
  - no production foreign `entity` / `mapper` imports remain
  - any residual risks, especially around oversized owner-domain APIs or package cycles

- [ ] **Step 7: Checkpoint the diff for the final architecture-governance task**

  Note: do not create a git commit unless the user explicitly asks for one.
