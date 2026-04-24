# Community App Growth Task-Progress Application-Layer Convergence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Converge the remaining deferred `growth` task-progress owner entry so `GrowthTaskProgressActionApi` is implemented by a single owner `TaskProgressApplicationService`, not by the current `TaskProgressTriggerService -> TaskProgressProjectionService` chain.

**Architecture:** Keep the foreign-domain contract stable: `content` and `social` continue to call `GrowthTaskProgressActionApi`. Replace the current two-layer owner entry (`TaskProgressTriggerService` + `TaskProgressProjectionService`) with one owner `TaskProgressApplicationService` that translates inputs, computes `bizDate`, preserves self-like/no-op semantics, and delegates the transactional mutation to `TaskProgressService`.

**Tech Stack:** Java 17, Spring Boot 3, JUnit 5, Mockito, Maven

---

## Planned File Structure

- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressApplicationService.java`
  New owner application entry that implements `GrowthTaskProgressActionApi` and absorbs the current trigger/projection translation logic.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java`
  Retired after its entry-translation responsibilities move into `TaskProgressApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
  Retired after its command translation and `bizDate` mapping move into `TaskProgressApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`
  Kept as the transactional core; no caller-facing API change, but the plan verifies it remains the single mutation path.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java`
  New owner-entry unit tests for post/comment/like translation semantics and no-op cases.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java`
  Removed once the new owner-entry tests replace it.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
  Extended so `TaskProgressTriggerService` and `TaskProgressProjectionService` must be absent from the classpath.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
  Kept green to protect integration semantics for idempotency, period keys, and auto-grant behavior.
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceUnitTest.java`
  Kept green to protect duplicate-row recovery and duplicate-event no-op semantics.
- `docs/business-logic/growth-task-grant-level-flow.md`
  Update the growth task-progress ownership description from projection-entry wording to the new owner application entry wording.
- `docs/ARCHITECTURE.md`
  Narrow the long-term deferred application-layer rollout from `growth/ops` to the remaining growth reward/account legacy surface.
- `docs/SYSTEM_DESIGN.md`
  Mirror the same deferred-scope narrowing and record the new growth task-progress owner application entry shape.

---

### Task 1: Lock The New Owner Entry In Tests

**Files:**
- Create: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceUnitTest.java`

- [ ] **Step 1: Write the failing owner-entry tests**

Create `TaskProgressApplicationServiceTest` with direct assertions on translation and no-op behavior:

```java
package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;

import static com.nowcoder.community.support.TestUuids.uuid;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class TaskProgressApplicationServiceTest {

    @Test
    void postPublishedShouldTranslateToOwnerTaskProgressCommand() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);
        Instant createTime = Instant.parse("2026-03-22T08:15:30Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        service.triggerPostPublished(uuid(100), uuid(7), createTime);

        verify(taskProgressService).processEvent(
                uuid(7),
                ContentEventTypes.POST_PUBLISHED,
                "post-published:" + uuid(100),
                LocalDate.of(2026, 3, 22)
        );
    }

    @Test
    void selfLikeShouldRemainNoOp() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);

        LikePayload payload = new LikePayload();
        payload.setActorUserId(uuid(9));
        payload.setEntityUserId(uuid(9));
        payload.setCreateTime(Instant.parse("2026-03-22T10:30:00Z"));

        service.triggerLikeCreated("like-created-event", payload);

        verifyNoInteractions(taskProgressService);
    }

    @Test
    void commentCreatedShouldUseCommentCreateTimeAsBizDate() {
        TaskProgressService taskProgressService = mock(TaskProgressService.class);
        GrowthBusinessTimeService businessTimeService = mock(GrowthBusinessTimeService.class);
        TaskProgressApplicationService service = new TaskProgressApplicationService(taskProgressService, businessTimeService);
        Instant createTime = Instant.parse("2026-03-22T09:00:00Z");
        when(businessTimeService.dateOf(createTime)).thenReturn(LocalDate.of(2026, 3, 22));

        CommentPayload payload = new CommentPayload();
        payload.setCommentId(uuid(200));
        payload.setUserId(uuid(3));
        payload.setCreateTime(createTime);

        service.triggerCommentCreated(payload);

        verify(taskProgressService).processEvent(
                uuid(3),
                ContentEventTypes.COMMENT_CREATED,
                "comment-created:" + uuid(200),
                LocalDate.of(2026, 3, 22)
        );
    }
}
```

- [ ] **Step 2: Extend the retirement check to reject the old owner entry classes**

Add these assertions to `LegacyGrowthSurfaceRetirementTest`:

```java
assertClassIsRetired("com.nowcoder.community.growth.service.TaskProgressTriggerService");
assertClassIsRetired("com.nowcoder.community.growth.service.TaskProgressProjectionService");
```

- [ ] **Step 3: Run the focused suite to verify it goes RED**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=TaskProgressApplicationServiceTest,LegacyGrowthSurfaceRetirementTest test
```

Expected:

- RED
- compilation failure for missing `TaskProgressApplicationService`
- or retirement assertions fail because `TaskProgressTriggerService` / `TaskProgressProjectionService` still exist

- [ ] **Step 4: Commit the test lock**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java
git commit -m "test: lock growth task progress application entry"
```

---

### Task 2: Implement The Single Owner Application Entry

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressApplicationService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`

- [ ] **Step 1: Write the minimal owner application entry**

Create `TaskProgressApplicationService`:

```java
package com.nowcoder.community.growth.service;

import com.nowcoder.community.content.contracts.event.CommentPayload;
import com.nowcoder.community.content.contracts.event.ContentEventTypes;
import com.nowcoder.community.growth.api.action.GrowthTaskProgressActionApi;
import com.nowcoder.community.social.contracts.event.LikePayload;
import com.nowcoder.community.social.contracts.event.SocialEventTypes;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

@Service
public class TaskProgressApplicationService implements GrowthTaskProgressActionApi {

    private final TaskProgressService taskProgressService;
    private final GrowthBusinessTimeService growthBusinessTimeService;

    public TaskProgressApplicationService(
            TaskProgressService taskProgressService,
            GrowthBusinessTimeService growthBusinessTimeService
    ) {
        this.taskProgressService = taskProgressService;
        this.growthBusinessTimeService = growthBusinessTimeService;
    }

    @Override
    public void triggerPostPublished(UUID postId, UUID userId, Instant createTime) {
        if (postId == null || userId == null || createTime == null) {
            return;
        }
        process(userId, ContentEventTypes.POST_PUBLISHED, "post-published:" + postId, createTime);
    }

    @Override
    public void triggerCommentCreated(CommentPayload payload) {
        if (payload == null || payload.getCommentId() == null || payload.getUserId() == null || payload.getCreateTime() == null) {
            return;
        }
        process(payload.getUserId(), ContentEventTypes.COMMENT_CREATED, "comment-created:" + payload.getCommentId(), payload.getCreateTime());
    }

    @Override
    public void triggerLikeCreated(String sourceEventId, LikePayload payload) {
        if (!StringUtils.hasText(sourceEventId) || payload == null || payload.getCreateTime() == null) {
            return;
        }
        UUID toUserId = payload.getEntityUserId();
        if (toUserId == null || toUserId.equals(payload.getActorUserId())) {
            return;
        }
        process(toUserId, SocialEventTypes.LIKE_CREATED, sourceEventId.trim(), payload.getCreateTime());
    }

    private void process(UUID userId, String triggerEventType, String sourceEventId, Instant occurredAt) {
        LocalDate bizDate = growthBusinessTimeService.dateOf(occurredAt);
        if (bizDate == null) {
            return;
        }
        taskProgressService.processEvent(userId, triggerEventType, sourceEventId, bizDate);
    }
}
```

- [ ] **Step 2: Remove the retired owner entry layers**

Delete:

```text
backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressTriggerService.java
backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressProjectionService.java
backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressTriggerServiceTest.java
```

- [ ] **Step 3: Run the focused task-progress and foreign-caller suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=TaskProgressApplicationServiceTest,TaskProgressServiceTest,TaskProgressServiceUnitTest,CreatePostUseCaseTest,CommentServiceTest,LikeServiceTest,LegacyGrowthSurfaceRetirementTest test
```

Expected:

- PASS
- `GrowthTaskProgressActionApi` callers stay green unchanged
- old task-progress entry classes are gone from the classpath

- [ ] **Step 4: Commit the owner entry convergence**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java
git add -u backend/community-app/src/main/java/com/nowcoder/community/growth/service \
          backend/community-app/src/test/java/com/nowcoder/community/growth/service
git commit -m "refactor: converge growth task progress application entry"
```

---

### Task 3: Update Documentation For The New Owner Entry

**Files:**
- Modify: `docs/business-logic/growth-task-grant-level-flow.md`
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Update the growth business-logic doc**

Replace the old projection wording with the new owner-entry wording:

```markdown
`TaskProgressApplicationService` 负责把不同领域输入统一翻译成：

- `userId`
- `triggerEventType`
- `sourceEventId`
- `bizDate`

然后调用 `TaskProgressService.processEvent(...)` 完成成长任务进度推进。
```

- [ ] **Step 2: Update the broader architecture docs**

Apply these documentation changes:

```markdown
<!-- docs/ARCHITECTURE.md -->
- `growth` 的 task-progress 写入口已统一为 owner `TaskProgressApplicationService`；剩余 deferred rollout 仅保留 reward/account legacy surface。

<!-- docs/SYSTEM_DESIGN.md -->
- growth task-progress path: `foreign domain -> GrowthTaskProgressActionApi -> TaskProgressApplicationService -> TaskProgressService`
- deferred wider rollout no longer includes `ops`; remaining deferred scope is growth reward/account legacy cleanup only.
```

- [ ] **Step 3: Run doc-adjacent regression checks**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LegacyGrowthSurfaceRetirementTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,ListenerBoundaryArchTest test
```

Expected:

- PASS
- growth task-progress convergence does not reopen any architecture baseline

- [ ] **Step 4: Commit the documentation update**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add \
  docs/business-logic/growth-task-grant-level-flow.md \
  docs/ARCHITECTURE.md \
  docs/SYSTEM_DESIGN.md \
  docs/superpowers/plans/2026-04-24-community-app-growth-task-progress-application-layer-convergence.md
git commit -m "docs: record growth task progress application entry"
```

---

### Task 4: Full Verification

**Files:**
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/app/post/CreatePostUseCaseTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/service/CommentServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/service/LikeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/LegacyGrowthSurfaceRetirementTest.java`

- [ ] **Step 1: Run the focused final suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=TaskProgressApplicationServiceTest,TaskProgressServiceTest,TaskProgressServiceUnitTest,CreatePostUseCaseTest,CommentServiceTest,LikeServiceTest,LegacyGrowthSurfaceRetirementTest,ControllerBoundaryArchTest,DomainBoundaryArchTest,ListenerBoundaryArchTest test
```

Expected:

- PASS
- growth task-progress semantics stay green
- foreign callers remain contract-stable
- no retired entry class remains

- [ ] **Step 2: Run the full module suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app test
```

Expected:

- PASS
- `community-app` remains green with the growth task-progress convergence added on top of phase 1/2/3

- [ ] **Step 3: Commit the verification checkpoint**

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary
git add -A
git commit -m "test: verify growth task progress convergence"
```

---

## Self-Review Checklist

### Spec Coverage

- single owner application entry for growth task-progress: covered by Task 2
- stable `GrowthTaskProgressActionApi` foreign contract: covered by Task 2 and Task 4
- retirement of trigger/projection entry layers: covered by Task 1 and Task 2
- documentation narrowing deferred scope away from `ops`: covered by Task 3
- full-module verification of the added convergence: covered by Task 4

### Placeholder Scan

- No `TODO` / `TBD`
- Every task names exact files, commands, and expected outcomes
- Every code-changing step includes concrete code or text snippets

### Type Consistency

- owner entry is consistently named `TaskProgressApplicationService`
- foreign contract remains `GrowthTaskProgressActionApi`
- transactional core remains `TaskProgressService`
- retired owner entry layers are consistently named `TaskProgressTriggerService` and `TaskProgressProjectionService`
