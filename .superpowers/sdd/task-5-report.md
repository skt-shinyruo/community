# Task 5 Report: Ops Architecture Guardrails

## Status

Completed.

## Scope Delivered

Implemented Task 5 for ops architecture guardrails, limited to the requested ArchUnit files plus the minimum ops-side fix needed to satisfy the existing DDD vocabulary guard:

- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DomainBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/InfraBoundaryArchTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/ControllerBoundaryArchTest.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/application/OutboxHandlerCatalog.java`
- `backend/community-app/src/main/java/com/nowcoder/community/ops/infrastructure/outbox/SpringOutboxHandlerCatalog.java`

Also wrote this task report at:

- `.superpowers/sdd/task-5-report.md`

No other production or test files were edited.

## TDD Record

### RED

1. Added the three new ArchUnit rules from the brief first:
   - `ops_domain_must_not_depend_on_framework_or_persistence`
   - `ops_controllers_should_enter_ops_application_only`
   - `ops_application_must_not_depend_on_ops_infrastructure`
2. Ran the full task verification command from the brief:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

3. Observed the expected red signal on the known branch blocker:

- `com.nowcoder.community.ops.application.OutboxHandlerCatalog.topics() exposes broker routing vocabulary: topic`

The new Task 5 rules did not require weakening. The only failure was the pre-existing application-port vocabulary leak described in the brief.

### GREEN

Implemented the minimal ops-side design fix:

- removed `topics()` from the `OutboxHandlerCatalog` application interface
- kept `SpringOutboxHandlerCatalog` topic collection logic as an implementation detail used by `hasHandler(...)`

Ran focused verification on the touched behavior:

```bash
cd backend
mvn test -pl :community-app -Dtest='DddLayeringArchTest,JdbcOutboxGovernanceAdapterTest,OutboxGovernanceApplicationServiceTest'
```

Result:

```text
Tests run: 58, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

Then re-ran the full task verification command from the brief:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Result:

```text
Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Behavior Implemented

- `DomainBoundaryArchTest`
  - now rejects `ops.domain` dependencies on Spring, servlet APIs, MyBatis, mapper/dataobject packages, controller DTOs, and ops infrastructure
- `ControllerBoundaryArchTest`
  - now rejects `ops.controller` dependencies on ops domain/infrastructure, common outbox, and foreign business-domain packages listed in the brief
- `InfraBoundaryArchTest`
  - now rejects `ops.application` dependencies on `ops.infrastructure`
- `OutboxHandlerCatalog`
  - now exposes only the application-level handler-existence check actually used by `OutboxGovernanceApplicationService`

## Existing Patterns Followed

- Added the new rules in the same ArchUnit style already used in the repository's boundary suites.
- Kept the ops-side correction inside the application port / infrastructure adapter boundary already introduced by Tasks 2-4.
- Avoided weakening any ArchUnit rule and adjusted the ops implementation surface instead, exactly as the brief required.

## Verification Commands

Executed during the task:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
mvn test -pl :community-app -Dtest='DddLayeringArchTest,JdbcOutboxGovernanceAdapterTest,OutboxGovernanceApplicationServiceTest'
mvn test -pl :community-app -Dtest='*ArchTest'
git diff --check
```

Final task verification result:

```text
Tests run: 110, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## Commit

Created commit message:

- `test: guard ops reliability boundaries`

## Self-Review

Reviewed the delivered Task 5 scope against the brief and the resulting diff:

- the three ArchUnit rules match the brief verbatim
- the ops-side fix is minimal and stays within the existing application/infrastructure boundary
- no rule was weakened to accommodate the current ops implementation
- focused ops tests still pass after the application-port shrink
- full `*ArchTest` verification is green

No functional issues found in the delivered Task 5 scope.

## Notes

- Maven still prints an existing unrelated unchecked-operation warning from `RedisRegistrationCodeRepositoryTest` during `testCompile`. This task did not introduce that warning.
