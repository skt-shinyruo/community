# Task 4 Report: Ops Admin API And Security

## Status

Completed with concern.

## Scope Delivered

Implemented Task 4 for the ops admin outbox API and security surface, limited to the requested code/test files:

- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OutboxOpsController.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxEventResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxBacklogResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayRequest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/dto/OutboxReplayResponse.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/security/OpsSecurityRules.java`
- `backend/community-app/src/test/java/com/nowcoder/community/ops/controller/OutboxOpsControllerTest.java`

Also wrote this task report at:

- `.superpowers/sdd/task-4-report.md`

No other production/test files were edited.

## TDD Record

### RED

1. Added `OutboxOpsControllerTest` first.
2. Ran the focused task command from the brief:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

3. Observed the expected missing-feature compilation failure because the controller and security types did not exist yet.

Representative failures:

- `package com.nowcoder.community.ops.security does not exist`
- `cannot find symbol class OutboxOpsController`
- `cannot find symbol class OpsSecurityRules`

This was the expected red signal, not a test typo or setup error.

### GREEN

Implemented the minimal production code required by the failing test:

- `OutboxOpsController`
- four controller DTOs
- `OpsSecurityRules`

Re-ran the same focused command and got a passing result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Ran the same task command again as the final pre-commit verification step. It passed with the same result.

## Behavior Implemented

- `GET /api/ops/outbox/backlog`
  - delegates to `OutboxGovernanceApplicationService.listBacklog()`
  - maps application results to `OutboxBacklogResponse`
- `GET /api/ops/outbox/events`
  - accepts `status`, `topic`, `eventId`, `createdFrom`, `createdTo`, and `limit`
  - delegates through `FindOutboxEventsCommand`
  - maps application results to `OutboxEventResponse`
- `POST /api/ops/outbox/events/{outboxId}/replay`
  - requires authenticated admin access
  - extracts the actor UUID with `CurrentUser.requireUserUuid(authentication)`
  - delegates through `ReplayOutboxEventCommand`
  - returns `OutboxReplayResponse`
- `OpsSecurityRules`
  - restricts `/api/ops/**` to `ROLE_ADMIN`

## Existing Patterns Followed

- Security registration follows the existing `ApiSecurityRules` + `@Order` pattern used by other domain slices.
- The controller is a thin inbound adapter and only calls the same-domain `OutboxGovernanceApplicationService`.
- JWT actor extraction uses the existing `CurrentUser.requireUserUuid(...)` helper.
- The MockMvc test follows the repository's existing admin-controller slice style with `CommunitySecurityConfig`, `WebMvcSliceJsonCodecTestConfig`, and mocked `JwtDecoder`.

## Verification Commands

Executed during the task:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
mvn test -pl :community-app -Dtest='*ArchTest'
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

Task verification result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Additional verification note:

- `mvn test -pl :community-app -Dtest='*ArchTest'` fails on a pre-existing Task 2 issue outside this task's write scope:
  - `com.nowcoder.community.ops.application.OutboxHandlerCatalog.topics() exposes broker routing vocabulary: topic`

## Commit

Created commit:

- `feat: expose outbox ops admin api`

## Self-Review

Reviewed the delivered Task 4 scope against the brief and current branch patterns:

- scope stayed inside the requested Task 4 code/test files
- controller methods remain thin and application-facing only
- security rule is narrowly scoped to `/api/ops/**`
- DTO mapping matches the brief's exposed fields and intentionally omits payload/traceparent from the event response
- MockMvc coverage proves both the admin authorization boundary and the three required endpoints

No functional issues found inside the delivered Task 4 scope.

## Notes

- The branch already uses `com.nowcoder.community.common.web.Result`, `GlobalExceptionHandler`, and `SecurityExceptionHandler`; the brief's sample imports for those types were stale, so the implementation followed the live codebase.
- Maven still prints an existing unrelated unchecked-operation warning from `RedisRegistrationCodeRepositoryTest` during `testCompile`. This task did not introduce that warning.

## Review Fix Follow-up

Applied one focused review fix in `OutboxOpsControllerTest` only. No production code change was needed.

Updated coverage:

- captured the `FindOutboxEventsCommand` forwarded by `GET /api/ops/outbox/events`
- asserted exact forwarding of `status`, `topic`, `eventId`, `createdFrom`, `createdTo`, and `limit`
- captured the `ReplayOutboxEventCommand` forwarded by `POST /api/ops/outbox/events/{outboxId}/replay`
- asserted exact forwarding of JWT-subject `actorUserId`, path `outboxId`, and JSON `reason`

Verification for the review fix:

```bash
cd backend
mvn test -pl :community-app -Dtest=OutboxOpsControllerTest
```

Result:

```text
Tests run: 2, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```
