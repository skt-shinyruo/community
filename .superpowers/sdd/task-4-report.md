# Task 4 Report: Harden Dispatch, Projection, And Search Entry Points

## Status

Completed.

## Scope Implemented

- Added `Objects.requireNonNull(command, "command must not be null")` at the public same-domain application entry points in:
  - `ContentEventDispatchApplicationService`
  - `SocialEventDispatchApplicationService`
  - `UserEventDispatchApplicationService`
  - `NoticeApplicationService`
  - `NoticeProjectionApplicationService`
  - `SearchApplicationService`
  - `SearchPostProjectionApplicationService`
  - `TaskProgressApplicationService`
  - `TaskProgressOutboxDispatchApplicationService`
  - `ImPolicyEventDispatchApplicationService`
- Removed redundant helper-level command-null tolerance in:
  - `NoticeProjectionApplicationService.commandForContentEvent(...)`
  - `NoticeProjectionApplicationService.commandForSocialEvent(...)`
- Kept existing business-invalid behavior intact after the public null guard:
  - blank payload checks
  - unsupported event handling
  - null/blank field checks inside already-non-null commands
  - no new `if (command == null) return` paths

## Tests Added Or Adjusted

- Added null-command contract tests to all task-owned test classes from the brief.
- Asserted:
  - `NullPointerException`
  - exact message: `command must not be null`

## Red/Green Verification

### Red

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionApplicationServiceTest,SearchApplicationServiceTest,SearchPostProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest
```

Result:

- Failed as expected.
- Failures showed the pre-hardening behaviors the brief targeted:
  - dispatch services converting null command into existing payload-invalid errors
  - notice/search entry points throwing incidental field-access NPEs with JVM-generated messages
  - projection/growth/search projection/im dispatch entry points silently returning on null command

### Green

Command:

```bash
cd backend
mvn test -pl :community-app -Dtest=ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionApplicationServiceTest,SearchApplicationServiceTest,SearchPostProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest
```

Result:

- Passed.
- Summary: `Tests run: 96, Failures: 0, Errors: 0, Skipped: 0`

## Self-Review

- Verified every owned public command entry point now fails fast on null command at method entry.
- Verified `NoticeProjectionApplicationService` helper methods no longer contain redundant `command == null` branches.
- Verified unchanged downstream business-invalid behavior by keeping existing tests green.
- Verified no unrelated files were staged from other workers' changes.

## Concerns

None within task scope.
