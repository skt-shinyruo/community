# Task 6 Report: Projection Lag Visibility

## Scope

Implemented Task 6 only in the `bbs-reliability-platform` worktree.

Touched code files:

- `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionLagPort.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/application/result/ProjectionLagResult.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java`
- `backend/community-app/src/test/java/com/nowcoder/community/ops/application/ProjectionGovernanceApplicationServiceTest.java`

Additional artifact requested by the task:

- `.superpowers/sdd/task-6-report.md`

## Requirement Resolution

The brief sample used `com.nowcoder.community.common.result.Result`, but the live branch uses `com.nowcoder.community.common.web.Result` in `OutboxOpsController`. Per user resolution, Task 6 follows the live branch convention and keeps the same route shape from the brief.

## TDD Record

### RED

Added `ProjectionGovernanceApplicationServiceTest` first, then ran:

```bash
cd backend
mvn test -pl :community-app -Dtest=ProjectionGovernanceApplicationServiceTest
```

Observed expected compilation failure because the new projection classes did not exist yet:

- `ProjectionLagResult` not found
- `ProjectionLagPort` not found
- `ProjectionGovernanceApplicationService` not found

This was the intended RED signal for the new feature surface.

### GREEN

Added the minimal production implementation:

- `ProjectionLagResult` record
- `ProjectionLagPort` application port
- `ProjectionGovernanceApplicationService` delegating to the port
- `OutboxProjectionLagAdapter` querying `outbox_event`
- `ProjectionOpsController` exposing `GET /api/ops/projections/lag`

Re-ran:

```bash
cd backend
mvn test -pl :community-app -Dtest=ProjectionGovernanceApplicationServiceTest
```

Result:

- `Tests run: 1, Failures: 0, Errors: 0, Skipped: 0`

## Task Verification

Ran the task verification command from the brief:

```bash
cd backend
mvn test -pl :community-app -Dtest=ProjectionGovernanceApplicationServiceTest,OutboxOpsControllerTest
```

Latest result:

- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- Build success

This confirms:

- the new projection application test passes
- the existing ops controller security coverage remains intact for `/api/ops/**`

## Implementation Notes

- `ProjectionGovernanceApplicationService.listProjectionLag()` returns the port rows unchanged, as required.
- `OutboxProjectionLagAdapter` reads projection topics from `outbox_event`, filters statuses to `PENDING`, `PROCESSING`, and `DEAD`, groups by topic and status, and computes `oldestAge` from `min(created_at)`.
- `ProjectionOpsController` exposes `GET /api/ops/projections/lag` under the existing ops admin path.

## Self-Review

No functional issues found in the scoped changes.

Checked specifically:

- package placement matches the existing ops application/infrastructure/controller split
- the controller uses the live branch `com.nowcoder.community.common.web.Result`
- no unrelated files were edited
- task verification command passed after implementation

## Fix Wave 1

Applied one focused fix wave for the Task 6 review items.

### Fix Scope

Updated:

- `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/ProjectionOpsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/ProjectionLagResponse.java`
- `backend/community-app/src/test/java/com/nowcoder/community/ops/infrastructure/outbox/OutboxProjectionLagAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/ProjectionOpsControllerTest.java`

### Review Item 1: Configurable Projection Topics

Problem:

- `OutboxProjectionLagAdapter` used `topic like 'projection.%'`
- this missed configured custom projection topics such as `custom.projection.search.post`

Change:

- removed the fixed SQL prefix assumption
- derived tracked projection topics from the live outbox handler catalog in infrastructure
- queried `outbox_event` using the registered tracked topics instead of a hard-coded prefix

Focused proof:

- `OutboxProjectionLagAdapterTest` registers both `custom.projection.search.post` and `eventbus.content`
- inserts rows for both topics
- verifies the lag result contains only the custom registered projection topic row

### Review Item 2: Controller DTO Boundary

Problem:

- `ProjectionOpsController` returned `ProjectionLagResult` directly

Change:

- added `ops.controller.dto.ProjectionLagResponse`
- mapped application results to controller DTOs in `ProjectionOpsController`
- kept the route shape unchanged: `GET /api/ops/projections/lag`

Focused proof:

- `ProjectionOpsControllerTest` calls the controller directly and verifies the returned data element is `ProjectionLagResponse`
- the same test also exercises `GET /api/ops/projections/lag` through `MockMvc`

### Fix-Wave TDD Record

#### RED

Added the new focused tests first, then ran:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxProjectionLagAdapterTest,ProjectionOpsControllerTest
```

Observed expected RED failures:

- `ProjectionLagResponse` not found
- `OutboxProjectionLagAdapter` constructor did not accept handler-catalog wiring

#### GREEN

Implemented the minimal production changes above and re-ran:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxProjectionLagAdapterTest,ProjectionOpsControllerTest
```

Result:

- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- Build success

### Verification After Fix Wave

Re-ran the original task gate exactly as requested:

```bash
cd backend
mvn test -pl :community-app -Dtest=ProjectionGovernanceApplicationServiceTest,OutboxOpsControllerTest
```

Result:

- `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0`
- Build success

### Fix-Wave Self-Review

Checked specifically:

- custom registered projection topics are included without depending on the old SQL prefix
- non-projection handler topics covered by the focused test do not leak into the projection lag result
- the controller now maps to a DTO instead of returning the application result type directly
- the original Task 6 verification gate still passes unchanged
