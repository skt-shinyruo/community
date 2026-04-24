# Community App Application-Layer Style Phase 3 Entry-Adapter Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining same-domain entry-adapter drift after phase 2 so controllers, listeners, and the remaining local scheduler path no longer inject raw owner services directly.

**Architecture:** Keep owner-domain raw services (`LikeService`, `FollowService`, `BlockService`, `ImPolicySnapshotService`, `UserModerationService`, `NoticeProjectionService`, score-refresh internals) as implementation details. Introduce focused owner `*ApplicationService` entry points in `..service..` packages, rewire the remaining controllers/listeners/scheduler to depend on them, and tighten ArchUnit so same-domain raw service dependencies outside legacy `..service..` packages are also caught.

**Tech Stack:** Java 17, Spring Boot 3, ArchUnit, JUnit 5, Mockito, Maven

---

## Planned File Structure

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
  Expands the same-domain entry-boundary helper so same-domain raw service classes outside a `.service` package also count as violations for controllers/listeners.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
  Keeps the controller boundary frozen with an empty legacy baseline under the stricter rule.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`
  New ArchUnit guardrail for same-domain `*Listener` classes so local event adapters can only inject owner `*ApplicationService`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/LikeApplicationService.java`
  Owner application entry for like HTTP use cases.
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/FollowApplicationService.java`
  Owner application entry for follow HTTP use cases.
- `backend/community-app/src/main/java/com/nowcoder/community/social/service/BlockApplicationService.java`
  Owner application entry for block HTTP use cases.
- `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockController.java`
  Rewired to call social owner `*ApplicationService` types only.
- `backend/community-app/src/main/java/com/nowcoder/community/im/service/ImPolicySnapshotApplicationService.java`
  Owner application entry for IM policy projection HTTP endpoints.
- `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java`
  Rewired to the owner application service instead of the raw projection service.
- `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationApplicationService.java`
  Owner application entry for moderation command listener consumption.
- `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionApplicationService.java`
  Owner application entry for local notice projection listeners.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/SocialInteractionProjectionApplicationService.java`
  Owner application entry for local social-like score enqueue projection.
- `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostScoreRefreshApplicationService.java`
  Owner application entry for the local scheduled score refresh worker.
- `backend/community-app/src/main/java/com/nowcoder/community/user/event/ModerationCommandListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/event/SocialInteractionProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`
  Rewired so each entry adapter delegates to an owner `*ApplicationService`.
- `backend/community-app/src/test/java/com/nowcoder/community/social/like/LikeControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/follow/FollowControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/social/block/BlockControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerUnitTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/event/ModerationCommandListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/score/PostScoreRefresherTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostScoreRefreshApplicationServiceTest.java`
  Lock the new owner application-service entry shape in unit tests before production rewiring.
- `docs/ARCHITECTURE.md`
- `docs/SYSTEM_DESIGN.md`
  Document that the remaining social/im/listener/score entry adapters are now closed under owner `*ApplicationService`.

---

### Task 1: Freeze The Remaining Entry-Adapter Guardrail

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ArchitectureRulesSupport.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ListenerBoundaryArchTest.java`

- [ ] **Step 1: Tighten the architecture helper and add the listener rule**

Add a helper that treats same-domain raw service-like types as violations even if they live outside `.service`, then add a new `*Listener` rule with an empty legacy baseline.

- [ ] **Step 2: Run the architecture suite to confirm the current remaining drift goes RED**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest,ListenerBoundaryArchTest test
```

Expected:

- RED
- failures mention `LikeController`, `FollowController`, `BlockController`, `ImPolicySnapshotController`, `ModerationCommandListener`, `NoticeProjectionListener`, and `SocialInteractionProjectionListener`

---

### Task 2: Close Remaining Social And IM Controller Entry Adapters

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/LikeApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/FollowApplicationService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/social/service/BlockApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/like/LikeController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/follow/FollowController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/block/BlockController.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/social/like/LikeControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/social/follow/FollowControllerTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/social/block/BlockControllerTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/im/service/ImPolicySnapshotApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/projection/ImPolicySnapshotController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/im/projection/ImPolicySnapshotControllerUnitTest.java`

- [ ] **Step 1: Write the controller-facing unit tests against owner `*ApplicationService` collaborators**

- [ ] **Step 2: Run the focused controller tests to confirm they fail before rewiring**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LikeControllerTest,FollowControllerTest,BlockControllerTest,ImPolicySnapshotControllerUnitTest test
```

Expected:

- RED
- compilation or constructor wiring failures mention missing `LikeApplicationService`, `FollowApplicationService`, `BlockApplicationService`, or `ImPolicySnapshotApplicationService`

- [ ] **Step 3: Add the owner application services and rewire the controllers**

- [ ] **Step 4: Re-run the focused controller tests**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=LikeControllerTest,FollowControllerTest,BlockControllerTest,ImPolicySnapshotControllerUnitTest,ImPolicySnapshotControllerTest test
```

Expected:

- PASS
- social and IM controllers only inject owner `*ApplicationService`

---

### Task 3: Close Remaining Listener And Local Scheduler Entry Adapters

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/service/UserModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/event/ModerationCommandListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/event/ModerationCommandListenerTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/notice/service/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/event/NoticeProjectionListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/notice/event/NoticeProjectionListenerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/event/SpringEventAdapterConstructorSelectionTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/SocialInteractionProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/event/SocialInteractionProjectionListener.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/event/SocialInteractionProjectionListenerTest.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/content/service/PostScoreRefreshApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/score/PostScoreRefresher.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/score/PostScoreRefresherTest.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/service/PostScoreRefreshApplicationServiceTest.java`

- [ ] **Step 1: Rewrite the listener/scheduler tests so entry adapters expect owner `*ApplicationService` collaborators**

- [ ] **Step 2: Run the focused suite to confirm the old wiring fails**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=ModerationCommandListenerTest,NoticeProjectionListenerTest,SpringEventAdapterConstructorSelectionTest,SocialInteractionProjectionListenerTest,PostScoreRefresherTest,PostScoreRefreshApplicationServiceTest test
```

Expected:

- RED
- compilation or constructor expectation failures mention the missing `*ApplicationService` types

- [ ] **Step 3: Add the owner application services and rewire the listeners / refresher**

- [ ] **Step 4: Re-run the focused listener / scheduler suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=ModerationCommandListenerTest,NoticeProjectionListenerTest,NoticeProjectionListenerStructureTest,SpringEventAdapterConstructorSelectionTest,SocialInteractionProjectionListenerTest,PostScoreRefresherTest,PostScoreRefreshApplicationServiceTest test
```

Expected:

- PASS
- each same-domain listener/scheduler now depends on owner `*ApplicationService`

---

### Task 4: Update Docs And Re-verify The Full Module

**Files:**
- Modify: `docs/ARCHITECTURE.md`
- Modify: `docs/SYSTEM_DESIGN.md`

- [ ] **Step 1: Document the phase 3 closure**

```markdown
<!-- docs/ARCHITECTURE.md -->
- `social` 的 like / follow / block controller 入口、`im` 的 policy snapshot controller 入口，以及 `notice` / `user` / `content` 的本地 listener 入口都已统一到 owner `*ApplicationService`。
- `PostScoreRefresher` 作为本地持续型 worker，调度壳层只负责批次触发；帖子热度刷新编排收敛到 `PostScoreRefreshApplicationService`。

<!-- docs/SYSTEM_DESIGN.md -->
Phase 3 entry-adapter closure covered:
- social like / follow / block HTTP entrypoints
- IM policy snapshot internal HTTP entrypoint
- moderation / notice / social-interaction local listeners
- post score local scheduled refresher
```

- [ ] **Step 2: Run the focused rollout suite**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app -Dtest=ControllerBoundaryArchTest,ListenerBoundaryArchTest,LikeControllerTest,FollowControllerTest,BlockControllerTest,ImPolicySnapshotControllerUnitTest,ImPolicySnapshotControllerTest,ModerationCommandListenerTest,NoticeProjectionListenerTest,NoticeProjectionListenerStructureTest,SpringEventAdapterConstructorSelectionTest,SocialInteractionProjectionListenerTest,PostScoreRefresherTest,PostScoreRefreshApplicationServiceTest test
```

Expected:

- PASS
- no remaining controller or listener depends on same-domain raw service entrypoints

- [ ] **Step 3: Run the full module test pass**

Run:

```bash
cd /home/feng/code/project/community/.worktrees/controller-application-boundary/backend
mvn -pl community-app test
```

Expected:

- PASS
- `community-app` stays green with the completed phase 1 + 2 + 3 convergence

---

## Self-Review Checklist

### Spec Coverage

- Remaining same-domain social/im controller entry drift: covered by Task 2
- Remaining same-domain listener entry drift: covered by Task 3
- Remaining local score scheduler entry drift: covered by Task 3
- Tightened architecture guardrail for controllers/listeners: covered by Task 1
- Documentation closure for the final remaining rollout slice: covered by Task 4

### Placeholder Scan

- No `TODO` / `TBD`
- Every task has concrete files, commands, and expected outcomes
- No “similar to above” shorthand

### Type Consistency

- entry-adapter-facing owner types are consistently named `*ApplicationService`
- raw owner services remain implementation details
- cross-domain collaboration surfaces remain `*QueryApi` / `*ActionApi` / `contracts.event`
