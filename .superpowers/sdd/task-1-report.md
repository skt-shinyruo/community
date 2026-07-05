# Task 1 Report: Harden Auth Session And Captcha Entry Points

## Scope

This task hardens the command non-null contract for the auth session and captcha application entry points:

- `LoginApplicationService.login(LoginCommand command)`
- `LoginApplicationService.refresh(RefreshCommand command)`
- `LoginApplicationService.logout(LogoutCommand command)`
- `CaptchaApplicationService.issue(IssueCaptchaCommand command)`

## Implementation

- Added `Objects.requireNonNull(command, "command must not be null")` at the start of each owned entry point.
- Removed the previous null-tolerant command access patterns.
- Kept field-level validation and existing business behavior unchanged.
- Added null-contract tests in the owned application test classes:
  - `loginShouldRejectNullCommand`
  - `refreshShouldRejectNullCommand`
  - `logoutShouldRejectNullCommand`
  - `issueShouldRejectNullCommand`

## Verification

Focused test command:

```bash
cd backend
mvn test -pl :community-app -Dtest=LoginApplicationServiceTest,CaptchaApplicationServiceTest
```

Result: passed.

## Self-Review

- The guard message is consistent across all four entry points.
- The implementation fails fast before any downstream collaborator is invoked.
- No command-null fallback logic remains in the touched methods.
- The test names and assertions match the brief.

## Notes

The workspace contained unrelated modified files from other work. I left those untouched and only changed the files in scope for this task.
