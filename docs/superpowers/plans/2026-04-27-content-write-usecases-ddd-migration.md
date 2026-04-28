# Content Write Use Cases DDD Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Finish the `content` write-side migration started by the strict DDD Tactical Layering plan by removing same-domain `content.app.*UseCase` entry points from post publishing, post moderation, and report moderation flows.

**Architecture:** `PostController` and `ModerationController` call `content.application.*ApplicationService` only. Business rules move to `content.domain.service`, persistence remains behind `content.domain.repository` interfaces implemented by `content.infrastructure.persistence`, and foreign-domain synchronous collaboration stays on owner-domain `api.action` contracts.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, ArchUnit, JUnit 5, Mockito, Maven.

---

## File Structure Map

### Existing Task 1/2 Foundation

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
  Already owns create/update/delete-by-author orchestration.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/CreatePostCommand.java`
  Existing create command. This plan adds update/admin command types only when they remove parameter noise.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostPublishingDomainService.java`
  Already owns author edit/delete rules. This plan adds admin moderation post rules.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
  Existing post repository port. This plan adds moderation write methods.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
  Existing MyBatis implementation. This plan adds mapper-backed moderation writes.

### Files To Create

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostModerationApplicationService.java`
  Owner application entry for top, wonderful, and admin delete.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java`
  Owner application entry for report moderation action handling.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/TakeModerationActionCommand.java`
  Command for report moderation actions.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostModerationDomainService.java`
  Domain rules for top/wonderful/admin delete.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/ModerationDecisionDomainService.java`
  Domain rules for action normalization, duration resolution, and supported action validation.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionApiAdapter.java`
  Temporary foreign API adapter implementing `PostModerationActionApi` and delegating to `content.application.PostModerationApplicationService`.

### Files To Modify

- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
  Import `content.application.PostModerationApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java`
  Import `content.application.ModerationApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
  Add post moderation write methods.
- `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
  Implement the new repository methods.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`
  Keep as the replacement coverage for author update/delete.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostModerationApplicationServiceTest.java`
  Move and rewrite from the legacy service test.
- `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java`
  Move and rewrite from `TakeModerationActionUseCaseTest`.

### Files To Delete After Replacement Tests Pass

- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/UpdatePostUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/DeleteOwnPostUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/AdminDeletePostUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/TopPostUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/MarkPostWonderfulUseCase.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCase.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/UpdatePostUseCaseTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCaseTest.java`

---

## Task 1: Retire Author Update/Delete UseCase Classes

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/UpdatePostUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/DeleteOwnPostUseCase.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/UpdatePostUseCaseTest.java`
- Verify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`

- [x] **Step 1: Verify production references are already gone**

Run:

```bash
cd /home/feng/code/project/community
rg -n "UpdatePostUseCase|DeleteOwnPostUseCase" backend/community-app/src/main/java
```

Expected before deletion:

```text
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/DeleteOwnPostUseCase.java:...
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/UpdatePostUseCase.java:...
```

No controller, service, application, or adapter class should reference either type.

- [x] **Step 2: Delete the legacy author write use cases and test**

Delete exactly these files:

```text
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/UpdatePostUseCase.java
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/DeleteOwnPostUseCase.java
backend/community-app/src/test/java/com/nowcoder/community/content/app/post/UpdatePostUseCaseTest.java
```

- [x] **Step 3: Run replacement coverage**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.PostPublishingApplicationServiceTest,DddLayeringArchTest test
```

Expected: PASS. The application test covers author update/delete through `PostPublishingApplicationService`, `PostPublishingDomainService`, `PostRepository`, and `PostTagRepository`.

---

## Task 2: Move Post Moderation Writes Into `content.application`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostModerationApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/PostModerationDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationActionApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/PostController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/repository/PostRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/infrastructure/persistence/MyBatisPostRepository.java`
- Move/Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostModerationApplicationServiceTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationApplicationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/AdminDeletePostUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/TopPostUseCase.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/post/MarkPostWonderfulUseCase.java`

- [x] **Step 1: Write the replacement application test**

Create `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostModerationApplicationServiceTest.java` with coverage for:

```java
service.top(actorUserId, postId);
service.wonderful(actorUserId, postId);
service.delete(actorUserId, postId);
```

Verify these calls in order:

```text
top: PostModerationDomainService.assertCanModeratePost -> PostRepository.markTop -> PostDomainEventPublisher.postUpdated -> PostBusinessEventLogger.postTop
wonderful: PostModerationDomainService.assertCanModeratePost -> PostRepository.markWonderful -> PostDomainEventPublisher.postUpdated -> PostWriteSideEffectScheduler.schedulePostScoreRefresh -> PostBusinessEventLogger.postWonderful
delete: PostModerationDomainService.assertCanAdminDeletePost -> PostRepository.markDeletedByAdmin -> PostDomainEventPublisher.postDeleted -> PostBusinessEventLogger.postDeleteByAdmin
```

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.PostModerationApplicationServiceTest test
```

Expected: compile failure until the new application/domain repository methods exist.

- [x] **Step 2: Add domain rules**

Create `PostModerationDomainService` with these methods:

```java
public void assertCanModeratePost(UUID actorUserId, PostSnapshot post)
public boolean shouldAdminDelete(UUID actorUserId, PostSnapshot post)
```

Rules:

```text
actorUserId/postId invalid -> BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法")
top/wonderful require existing active post -> deleted post throws POST_NOT_FOUND
admin delete invalid actor/post -> BusinessException(INVALID_ARGUMENT, "actorUserId/postId 非法")
admin delete on already deleted post -> shouldAdminDelete returns false
```

- [x] **Step 3: Extend `PostRepository`**

Add methods:

```java
void markTop(UUID postId);
void markWonderful(UUID postId);
void markDeletedByAdmin(UUID postId, UUID actorUserId, Date deletedTime);
```

Use existing `getRequiredSnapshot(UUID postId)` for loading active/deleted status.

- [x] **Step 4: Implement MyBatis repository methods**

In `MyBatisPostRepository`, implement the new repository methods by delegating to the existing mapper/service-equivalent persistence operations:

```text
markTop -> update post type to 1
markWonderful -> update post status to 1
markDeletedByAdmin -> update moderation delete metadata with status=2, reasonCode="admin_delete", operatorUserId=actorUserId, updateTime=deletedTime
```

Do not call `TopPostUseCase`, `MarkPostWonderfulUseCase`, `AdminDeletePostUseCase`, or raw `PostService` from the new application service.

- [x] **Step 5: Implement `content.application.PostModerationApplicationService`**

Constructor dependencies:

```java
PostModerationDomainService domainService
PostRepository postRepository
PostDomainEventPublisher domainEventPublisher
PostWriteSideEffectScheduler postWriteSideEffectScheduler
PostBusinessEventLogger postBusinessEventLogger
```

Methods:

```java
@Transactional
public void top(UUID actorUserId, UUID postId)

@Transactional
public void wonderful(UUID actorUserId, UUID postId)

@Transactional
public void delete(UUID actorUserId, UUID postId)
```

- [x] **Step 6: Keep foreign API compatibility**

Create `content.service.PostModerationActionApiAdapter implements PostModerationActionApi`:

```java
@Service
public class PostModerationActionApiAdapter implements PostModerationActionApi {
    private final com.nowcoder.community.content.application.PostModerationApplicationService delegate;

    public void top(UUID actorUserId, UUID postId) { delegate.top(actorUserId, postId); }
    public void wonderful(UUID actorUserId, UUID postId) { delegate.wonderful(actorUserId, postId); }
    public void delete(UUID actorUserId, UUID postId) { delegate.delete(actorUserId, postId); }
}
```

Same-domain controllers must inject `content.application.PostModerationApplicationService`; foreign domains continue depending on `PostModerationActionApi`.

- [x] **Step 7: Delete legacy classes after references are gone**

Run:

```bash
cd /home/feng/code/project/community
rg -n "TopPostUseCase|MarkPostWonderfulUseCase|AdminDeletePostUseCase" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected before deletion: only the class files and old test references remain.

Delete:

```text
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/AdminDeletePostUseCase.java
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/TopPostUseCase.java
backend/community-app/src/main/java/com/nowcoder/community/content/app/post/MarkPostWonderfulUseCase.java
backend/community-app/src/main/java/com/nowcoder/community/content/service/PostModerationApplicationService.java
backend/community-app/src/test/java/com/nowcoder/community/content/service/PostModerationApplicationServiceTest.java
```

- [x] **Step 8: Run focused post moderation tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.PostModerationApplicationServiceTest,PostControllerUnitTest,DddLayeringArchTest test
```

Expected: PASS.

---

## Task 3: Move Report Moderation Action Into `content.application`

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/application/command/TakeModerationActionCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/domain/service/ModerationDecisionDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/controller/ModerationController.java`
- Move/Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCase.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCaseTest.java`

- [x] **Step 1: Add command type**

Create:

```java
package com.nowcoder.community.content.application.command;

import java.util.UUID;

public record TakeModerationActionCommand(
        UUID actorId,
        UUID reportId,
        String action,
        String reason,
        Integer durationSeconds
) {
}
```

- [x] **Step 2: Add domain decision service**

Create `ModerationDecisionDomainService` with:

```java
public ModerationDecision decide(TakeModerationActionCommand command)
```

The returned `ModerationDecision` must contain:

```text
actorId
reportId
normalizedAction
normalizedReason
resolvedDurationSeconds
```

Rules copied exactly from `TakeModerationActionUseCase`:

```text
actorId null -> BusinessException(INVALID_ARGUMENT, "actorId 非法")
reportId null -> BusinessException(INVALID_ARGUMENT, "reportId 非法")
unsupported action -> BusinessException(INVALID_ARGUMENT, "action 非法")
blank reason -> BusinessException(INVALID_ARGUMENT, "reason 不能为空")
mute default duration -> 86400
ban default duration -> 604800
non mute/ban null duration -> 0
non mute/ban negative duration -> 0
```

- [x] **Step 3: Move application orchestration**

Create `content.application.ModerationApplicationService` with constructor dependencies copied from `TakeModerationActionUseCase` plus `ModerationDecisionDomainService`:

```java
ReportService reportService
ModerationAuditWriter moderationAuditWriter
ModerationTargetResolver moderationTargetResolver
ContentModerationApplier contentModerationApplier
ModerationNoticePublisher moderationNoticePublisher
UserModerationActionApi userModerationActionApi
ModerationDecisionDomainService decisionDomainService
```

Method:

```java
@Transactional
public UUID takeAction(TakeModerationActionCommand command)
```

Preserve the existing action behavior:

```text
reject -> mark report rejected, publish reporter notice
hide/delete -> apply content action, mark processed, publish target and reporter notices
warn -> mark processed, publish target and reporter notices
mute/ban -> call UserModerationActionApi, mark processed, publish target and reporter notices
non-pending report -> BusinessException(INVALID_ARGUMENT, "该举报已处理")
```

Implementation note: this step was tightened during execution to comply with `DddLayeringArchTest`. Instead of copying the old `content.app.moderation` and `content.service` collaborators into the new application service, the final implementation uses domain repositories plus application ports implemented by infrastructure adapters.

- [x] **Step 4: Move tests**

Move `TakeModerationActionUseCaseTest` to:

```text
backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java
```

Update it to instantiate `ModerationApplicationService` and pass `new TakeModerationActionCommand(...)` instead of direct parameters.

- [x] **Step 5: Rewire controller**

Change `ModerationController` to import:

```java
com.nowcoder.community.content.application.ModerationApplicationService
com.nowcoder.community.content.application.command.TakeModerationActionCommand
```

The controller still maps HTTP DTO fields, but the application call becomes:

```java
moderationApplicationService.takeAction(new TakeModerationActionCommand(actorId, reportId, request.getAction(), request.getReason(), request.getDurationSeconds()));
```

- [x] **Step 6: Delete legacy use case and old service wrapper**

Run:

```bash
cd /home/feng/code/project/community
rg -n "TakeModerationActionUseCase|content.service.ModerationApplicationService" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected before deletion: only the old class, old test, and old import references remain.

Delete:

```text
backend/community-app/src/main/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCase.java
backend/community-app/src/main/java/com/nowcoder/community/content/service/ModerationApplicationService.java
backend/community-app/src/test/java/com/nowcoder/community/content/app/moderation/TakeModerationActionUseCaseTest.java
```

- [x] **Step 7: Run focused moderation tests**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.ModerationApplicationServiceTest,ModerationControllerTest,DddLayeringArchTest test
```

Expected: PASS.

---

## Task 4: Remove `content.app` Production Surface

**Files:**
- Delete empty directories under `backend/community-app/src/main/java/com/nowcoder/community/content/app`
- Verify no production references to `content.app`

- [x] **Step 1: Verify no production references remain**

Run:

```bash
cd /home/feng/code/project/community
rg -n "com\\.nowcoder\\.community\\.content\\.app\\.|content/app/" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: no output.

- [x] **Step 2: Run content and architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.content.application.PostPublishingApplicationServiceTest,com.nowcoder.community.content.application.PostModerationApplicationServiceTest,com.nowcoder.community.content.application.ModerationApplicationServiceTest,PostControllerUnitTest,ModerationControllerTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,DddLayeringArchTest test
```

Expected: PASS.

- [x] **Step 3: Run full app suite**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected:

```text
Tests run: 578 or higher, Failures: 0, Errors: 0
BUILD SUCCESS
```

---

## Self-Review

### Spec Coverage

- Same-domain controller entry through `ApplicationService`: covered by Tasks 2 and 3.
- `ApplicationService` as use-case entry instead of `UseCase`: covered by deleting all remaining `content.app` production use cases.
- Domain rules in `domain.service`: covered by `PostModerationDomainService` and `ModerationDecisionDomainService`.
- Persistence behind repository interfaces: covered by extending `PostRepository` and `MyBatisPostRepository`.
- Foreign-domain API compatibility: covered by `PostModerationActionApiAdapter`.

### Placeholder Scan

No step uses TBD/TODO language. Each task lists exact files, exact command lines, and expected outcomes.

### Type Consistency

The plan consistently uses `content.application.PostPublishingApplicationService`, `content.application.PostModerationApplicationService`, `content.application.ModerationApplicationService`, `content.domain.repository.PostRepository`, and `content.infrastructure.persistence.MyBatisPostRepository`.
