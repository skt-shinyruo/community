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
