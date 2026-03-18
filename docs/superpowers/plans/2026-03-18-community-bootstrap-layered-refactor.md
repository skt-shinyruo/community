# Community Bootstrap Layered Refactor Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor `backend/community-bootstrap` from contract-first modular-monolith patterns to business-domain packages with classic Spring Boot layering and direct service-to-service collaboration.

**Architecture:** The refactor should first establish `common` as the new home for cross-cutting code, then replace in-process internal API wrappers with direct domain service wiring, and only after that collapse `application` and legacy package naming. Keep the repository buildable after each task; do not mix package moves, semantic rewrites, and verification into one unbounded batch.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, MyBatis, Micrometer, Maven, JUnit 5

---

### Task 1: Introduce `common` And Move Cross-Cutting Foundations

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/api/Result.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/api/ErrorCode.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/api/CommonErrorCode.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/api/SimpleErrorCode.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/exception/BusinessException.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/domain/EntityTypes.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/validation/ValidationLimits.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/trace/TraceHeaders.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/trace/TraceIdCodec.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/event/EventEnvelope.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/event/EventEnvelopeParser.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/event/EventTopicConventions.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/event/UnknownEventAction.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/infra/web/ResultTest.java`

- [ ] **Step 1: Create the destination package structure under `common`**

  Create:
  - `common/web`
  - `common/exception`
  - `common/constants`
  - `common/trace`
  - `common/event`

- [ ] **Step 2: Move or copy the cross-cutting contract types into `common`**

  Move:
  - `Result` into `common.web`
  - `ErrorCode`, `CommonErrorCode`, `SimpleErrorCode`, `BusinessException` into `common.exception`
  - `EntityTypes`, `ValidationLimits` into `common.constants`
  - `TraceHeaders`, `TraceIdCodec` into `common.trace`
  - event envelope utilities into `common.event`

- [ ] **Step 3: Update direct import sites before deleting old package usage**

  Prioritize:
  - web infrastructure
  - controllers
  - services using `BusinessException`, `EntityTypes`, or validation limits
  - tests that directly assert `Result` or exception semantics

- [ ] **Step 4: Run the focused foundation tests and compile checks**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=ResultTest,GlobalExceptionHandlerTest test`

- [ ] **Step 5: Leave a temporary compatibility shim only if the tree does not yet compile without it**

  Rule:
  - prefer completing all import migrations in the same task
  - only keep transitional wrappers if required to preserve incremental buildability

- [ ] **Step 6: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Rebuild The HTTP Error And Trace Stack On Top Of `common`

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/GlobalExceptionHandler.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/SecurityExceptionHandler.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/web/AuthOriginGuardFilter.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/ResultTraceIdAdvice.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/web/TraceIdFilter.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/infra/web/GlobalExceptionHandlerTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/auth/api/AuthControllerUnitTest.java`

- [ ] **Step 1: Update infrastructure imports to the new `common` packages**

  Cover:
  - exception mapping
  - `Result` response writing
  - trace header and trace-id generation usage

- [ ] **Step 2: Preserve both HTTP error paths explicitly**

  Keep consistent:
  - controller exception flow via `GlobalExceptionHandler`
  - security entry point and access-denied flow via `SecurityExceptionHandler`
  - filter-written rejection flow in `AuthOriginGuardFilter`

- [ ] **Step 3: Reconfirm trace propagation for every response path**

  Verify:
  - `ResultTraceIdAdvice` still decorates controller responses
  - direct writers still set trace headers and trace id where required
  - no path regresses to empty-body or header-less error responses

- [ ] **Step 4: Run focused web/security tests**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=GlobalExceptionHandlerTest,AuthControllerUnitTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Replace The `auth -> user` Internal API Chain With Direct Services

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/service/DbRefreshTokenStore.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/service/UserAuthAccess.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/application/UserAuthApiImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/UserAuthApi.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalAuthenticateResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalSessionProfileResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalRegisterResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalActivationResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalUserByEmailResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserInternalRefreshTokenRecordResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/InternalUserService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionService.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/auth/service/UserAuthAccessTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`

- [ ] **Step 1: Write or update failing tests around direct auth-to-user collaboration**

  Cover:
  - authentication
  - registration
  - activation
  - refresh-token persistence or lookup paths

- [ ] **Step 2: Replace `UserAuthAccess` call sites with direct `user.service` dependencies**

  Implement:
  - inject focused user services into auth services
  - return ordinary objects or primitives instead of internal `Result<T>` payloads

- [ ] **Step 3: Remove the `UserAuthApi` adapter layer**

  Delete or absorb:
  - `UserAuthApi`
  - `UserAuthApiImpl`
  - internal DTOs that only existed for this in-process boundary

- [ ] **Step 4: Re-run targeted auth tests and verify GREEN**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=UserAuthAccessTest,RefreshTokenServiceTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Replace The `user -> social` And Search/Ops Internal Orchestration Layers

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/service/UserSocialProfileService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/application/SocialReadApplicationService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/application/dto/SocialUserProfileStats.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/like/SocialLikeQueryService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/application/SearchOpsApiImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/api/ops/SearchOpsApi.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/ops/api/OpsController.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/social/application/SocialReadApplicationServiceTest.java`
- Create: `backend/community-bootstrap/src/test/java/com/nowcoder/community/ops/api/OpsControllerTest.java`

- [ ] **Step 1: Replace `SocialReadApplicationService` with direct social service composition**

  Implement:
  - let `UserSocialProfileService` and `SocialLikeQueryService` depend on focused social services
  - remove the application-only aggregation wrapper

- [ ] **Step 2: Replace `SearchOpsApi` with direct search service wiring in ops**

  Implement:
  - inject a search admin or search service into `OpsController`
  - keep metrics and response semantics explicit without `SearchOpsApi`

- [ ] **Step 3: Remove obsolete application DTOs and interfaces**

  Delete or absorb:
  - `SocialUserProfileStats`
  - `SearchOpsApi`
  - `SearchOpsApiImpl`
  - any mapping code that only existed to wrap same-process calls

- [ ] **Step 4: Run the focused tests for user-social and ops-search paths**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=SocialReadApplicationServiceTest,OpsControllerTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 5: Replace The `content/social/message <-> user/content/social` Internal Wrappers

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/UserModerationAccess.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/UserModerationGuard.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/service/UserModerationAccess.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/service/UserModerationGuard.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/service/UserLookupService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/service/ContentEntityResolver.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/like/LikeService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/service/CommentService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/im/api/ImGovernanceController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/social/application/BlockQueryApplicationService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/EntityResolveApiImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/application/UserModerationApiImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/application/UserReadApiImpl.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/internal/api/EntityResolveApi.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/internal/api/UserModerationApi.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/internal/dto/EntityResolveResponse.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/contracts/internal/dto/UserModerationStatus.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/UserReadApi.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/internal/dto/UserSummary.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/message/service/UserLookupServiceResolveCacheTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/message/service/PrivateMessageServiceBlockCheckTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- Create: `backend/community-bootstrap/src/test/java/com/nowcoder/community/social/service/ContentEntityResolverTest.java`

- [ ] **Step 1: Replace moderation lookups with direct user-service collaboration**

  Implement:
  - let content and message guards depend on focused user moderation services
  - remove `UserModerationAccess` wrappers once callers are migrated

- [ ] **Step 2: Replace user lookup wrappers with direct user read services**

  Implement:
  - let message services and controllers depend on user-facing read services
  - preserve any required caching locally in `UserLookupService` only if it still adds value after direct wiring

- [ ] **Step 3: Replace content entity resolution wrappers with direct content services**

  Implement:
  - let social services call focused content services directly
  - remove `EntityResolveApi` and `ContentEntityResolver` adapter behavior once callers no longer need the indirection

- [ ] **Step 4: Replace social application wrappers used by content/message/IM**

  Implement:
  - switch `CommentService`, `PrivateMessageService`, and `ImGovernanceController` to direct social service usage
  - remove `BlockQueryApplicationService` after callers move

- [ ] **Step 5: Delete obsolete internal API interfaces, impls, and boundary DTOs**

  Delete or absorb:
  - `contracts.internal.api.*`
  - `contracts.internal.dto.*`
  - `user.api.internal.*`
  - matching `*ApiImpl` classes that only served these boundaries

- [ ] **Step 6: Re-run the targeted cross-domain tests and verify GREEN**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=UserLookupServiceResolveCacheTest,PrivateMessageServiceBlockCheckTest,CommentServiceTest,ContentEntityResolverTest test`

- [ ] **Step 7: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 6: Collapse Remaining `application` Packages And Normalize Naming

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostApplicationService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/PostScanApplicationService.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/application/dto/PostScanResult.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/api/PostController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/search/api/SearchController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/auth/api/AuthController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/UserController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/AdminUserController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/FilesController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/api/LeaderboardController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/api/MessageController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/api/NoticeController.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/dao/CommentMapper.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/content/dao/DiscussPostMapper.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/message/dao/MessageMapper.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/user/dao/UserMapper.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/application/PostApplicationServiceTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/content/application/PostScanApplicationServiceTest.java`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/social/application/BlockQueryApplicationServiceTest.java`

- [ ] **Step 1: Move business orchestration from `application` into conventional services**

  Prioritize:
  - content post and post-scan flows
  - any remaining search, social, or user application helpers

- [ ] **Step 2: Rename package ownership by responsibility**

  Implement:
  - HTTP endpoint classes from `*.api` -> `*.controller`
  - DTO classes from `*.api.dto` -> `*.dto`
  - persistence classes from `*.dao` -> `*.mapper` or `*.repository`
  - keep `*.api.security`, `*.api.event`, and error-code classes mapped by responsibility rather than forced into controller packages

- [ ] **Step 3: Update component scans, imports, and MyBatis resource references**

  Verify:
  - Spring still discovers moved controllers and services
  - mapper XML bindings still match renamed interfaces
  - package refactors do not break tests or IDE/package-private assumptions

- [ ] **Step 4: Re-run targeted package-normalization tests**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=PostApplicationServiceTest,PostScanApplicationServiceTest,BlockQueryApplicationServiceTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 7: Remove `ModuleCallSupport`, Update Docs, And Verify The Module

**Files:**
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/modulecall/ModuleCallSupport.java`
- Modify: `backend/community-bootstrap/src/main/java/com/nowcoder/community/infra/modulecall/ModuleCallOptions.java`
- Modify: `backend/community-bootstrap/src/test/java/com/nowcoder/community/infra/modulecall/ModuleCallSupportTest.java`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`
- Test: `backend/community-bootstrap/src/test/java/com/nowcoder/community/bootstrap/arch/BackendFlatteningArchTest.java`

- [ ] **Step 1: Remove remaining production usages of `ModuleCallSupport` and `ModuleCallOptions`**

  Rule:
  - no ordinary in-process business path may still depend on this abstraction
  - if metrics remain necessary, record them directly in the owning service or controller

- [ ] **Step 2: Delete the obsolete module-call support code and replace or remove its tests**

  Implement:
  - remove `ModuleCallSupport`
  - remove `ModuleCallOptions`
  - drop tests that only defend the old internal-call abstraction

- [ ] **Step 3: Update architecture and system design docs to the new default style**

  Rewrite:
  - direct service collaboration inside the monolith
  - `common` as the cross-cutting package
  - classic layered naming inside business domains

- [ ] **Step 4: Run focused and broad module verification**

  Run:
  - `mvn -pl backend/community-bootstrap -Dtest=GlobalExceptionHandlerTest,AuthControllerUnitTest,RefreshTokenServiceTest,CommentServiceTest,MessageControllerTest test`
  - `mvn -pl backend/community-bootstrap test`

- [ ] **Step 5: Re-check the architectural end state against the approved spec**

  Verify:
  - no `contracts.internal.*`
  - no `application` layer
  - no single-process internal `Result<T>` transport
  - old `api/dao/api-internal` naming has been normalized by responsibility

- [ ] **Step 6: Prepare the final review summary for the `community-bootstrap` track**

  Include:
  - exact tests run
  - any residual cycle-risk areas
  - any follow-up cleanup left for the IM track
