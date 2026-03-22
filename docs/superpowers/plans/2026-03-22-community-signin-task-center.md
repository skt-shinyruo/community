# Community Sign-In And Task Center Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a user-facing growth center with daily sign-in, streak tracking, daily/weekly/lifetime task progress, and reward delivery on top of the growth foundation.

**Architecture:** Build sign-in and task center on the new growth foundation instead of extending `user.score` directly. Sign-in is a first-class write path that records one row per user per business date and grants rewards through `UnifiedGrantService`. Task progress is a projection over sign-in/content/social events, stored in `user_task_progress` keyed by user + task + period. The frontend reads a dedicated `/api/growth` surface and presents one coherent growth center rather than scattering sign-in and task UI across profile pages.

**Tech Stack:** Java 17, Spring Boot 3, MyBatis, existing local event + outbox infrastructure, Vue 3, Vue Router, Vitest, Maven

---

## File Structure Map

### Backend schema and persistence

- `deploy/mysql-init/010_schema.sql`
- `backend/community-app/src/test/resources/schema.sql`
  Role: add `growth_check_in`, `task_template`, and `user_task_progress`.
- `backend/community-app/src/main/resources/mapper/growth_check_in_mapper.xml`
- `backend/community-app/src/main/resources/mapper/task_template_mapper.xml`
- `backend/community-app/src/main/resources/mapper/user_task_progress_mapper.xml`
  Role: mapper SQL for sign-in and task projection tables.

### Backend growth domain

- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/GrowthCheckIn.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/TaskTemplate.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/UserTaskProgress.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/GrowthCheckInMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/TaskTemplateMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/UserTaskProgressMapper.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/CheckInService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskCenterService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthCenterController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInStatusResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInActionResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInCalendarResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/TaskCenterResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/TaskItemResponse.java`
  Role: sign-in and task-center read/write surface.

### Event projection integration

- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventTypes.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
  Role: translate sign-in/content/social facts into task progress and task rewards.

### Frontend

- `frontend/src/api/services/growthService.js`
- `frontend/src/views/GrowthCenterView.vue`
- `frontend/src/views/TaskCenterView.vue`
- `frontend/src/views/SignInCalendarView.vue`
- `frontend/src/views/growthCenterState.js`
- `frontend/src/views/growthCenterState.test.js`
- `frontend/src/router/index.js`
- `frontend/src/router/navigation.js`
  Role: authenticated growth center routes and state mapping for UI presentation.

### Tests

- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/CheckInServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/GrowthCenterControllerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java`
- `frontend/src/views/growthCenterState.test.js`
  Role: lock sign-in streak semantics, task projection semantics, API contracts, and UI grouping behavior.

---

### Task 1: Implement Daily Sign-In Persistence, Service, And APIs

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/GrowthCheckIn.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/GrowthCheckInMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/growth_check_in_mapper.xml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/CheckInService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInStatusResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInActionResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/CheckInCalendarResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthCenterController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/CheckInServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/controller/GrowthCenterControllerTest.java`

- [ ] **Step 1: Write failing tests for daily sign-in and streak rules**

  Cover:
  - the same user can sign in only once per business date
  - consecutive business dates increment streak
  - a gap resets streak but keeps max streak/history
  - sign-in rewards are routed through `UnifiedGrantService`
  - the status/calendar API returns enough data for a monthly view

- [ ] **Step 2: Run the targeted sign-in tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=CheckInServiceTest,GrowthCenterControllerTest test`

- [ ] **Step 3: Implement the sign-in table, mapper, service, and `/api/growth/check-in*` endpoints**

  Implement:
  - one row per user per business date
  - streak calculation that does not depend on a midnight cleanup job
  - `POST /api/growth/check-in`
  - `GET /api/growth/check-in/status`
  - `GET /api/growth/check-in/calendar`

- [ ] **Step 4: Re-run the targeted tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=CheckInServiceTest,GrowthCenterControllerTest test`

- [ ] **Step 5: Checkpoint the diff for the sign-in task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Add Task Templates, Progress Projection, And Reward Delivery

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/TaskTemplate.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/entity/UserTaskProgress.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/TaskTemplateMapper.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/mapper/UserTaskProgressMapper.java`
- Create: `backend/community-app/src/main/resources/mapper/task_template_mapper.xml`
- Create: `backend/community-app/src/main/resources/mapper/user_task_progress_mapper.xml`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskCenterService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/service/TaskProgressService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventTypes.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthLocalEvent.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/GrowthEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/LocalGrowthEventPublisher.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressProjectionListener.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxEnqueuer.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandler.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/service/TaskProgressServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressProjectionListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/event/TaskProgressOutboxHandlerTest.java`

- [ ] **Step 1: Write failing tests for progress accumulation, per-period uniqueness, and auto-grant**

  Cover:
  - daily task progress keys are unique by user + task + business date
  - weekly task progress keys are unique by user + task + week key
  - lifetime tasks use one stable period key
  - duplicate post/comment/like/sign-in events do not double-count progress
  - auto-grant tasks insert one grant record and one reward outcome only once per period

- [ ] **Step 2: Run the targeted task tests and confirm RED**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=TaskProgressServiceTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest test`

- [ ] **Step 3: Seed fixed task templates and implement the projection layer**

  Implement:
  - schema seed rows for the approved daily/weekly/lifetime tasks
  - `TaskProgressService` to update progress from sign-in/content/social facts
  - projection listeners or outbox handlers that consume existing content/social events plus new check-in events
  - `claim_required` support in the data model even if the first rollout enables only auto-grant templates

- [ ] **Step 4: Re-run the targeted task tests and verify GREEN**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=TaskProgressServiceTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest test`

- [ ] **Step 5: Checkpoint the diff for the task projection task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Build The User-Facing Growth Center Routes And UI

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/controller/GrowthCenterController.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/TaskCenterResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/growth/dto/TaskItemResponse.java`
- Create: `frontend/src/api/services/growthService.js`
- Create: `frontend/src/views/GrowthCenterView.vue`
- Create: `frontend/src/views/TaskCenterView.vue`
- Create: `frontend/src/views/SignInCalendarView.vue`
- Create: `frontend/src/views/growthCenterState.js`
- Create: `frontend/src/views/growthCenterState.test.js`
- Modify: `frontend/src/router/index.js`
- Modify: `frontend/src/router/navigation.js`

- [ ] **Step 1: Write failing UI state tests for grouping and presentation**

  Cover:
  - task groups render separately for daily, weekly, and lifetime tasks
  - claimed / claimable / in-progress tasks map to distinct UI states
  - growth summary header combines level, score, balance, and streak correctly

- [ ] **Step 2: Run the targeted frontend test and confirm RED**

  Run:
  - `cd frontend && npm test -- src/views/growthCenterState.test.js`

- [ ] **Step 3: Implement task-center read APIs and the frontend routes**

  Implement:
  - `GET /api/growth/tasks`
  - one top-level route such as `/growth`
  - dedicated subroutes or page sections for sign-in calendar and task list
  - navigation entry that is visible only for authenticated users

- [ ] **Step 4: Re-run frontend verification**

  Run:
  - `cd frontend && npm test -- src/views/growthCenterState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 5: Checkpoint the diff for the growth-center UI task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: Run Cross-Layer Verification For The Sign-In/Task Subproject

**Files:**
- Verify only

- [ ] **Step 1: Run the focused backend suites**

  Run:
  - `cd backend && mvn -pl community-app -Dtest=CheckInServiceTest,TaskProgressServiceTest,GrowthCenterControllerTest,TaskProgressProjectionListenerTest,TaskProgressOutboxHandlerTest test`

- [ ] **Step 2: Run the focused frontend suites**

  Run:
  - `cd frontend && npm test -- src/views/growthCenterState.test.js`
  - `cd frontend && npm run build`

- [ ] **Step 3: Run a broader backend module verification**

  Run:
  - `cd backend && mvn -pl community-app test`

- [ ] **Step 4: Checkpoint the diff for the sign-in/task-center plan**

  Note: do not create a git commit unless the user explicitly asks for one.
