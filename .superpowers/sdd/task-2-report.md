# Task 2 Report: Harden Auth Registration And Password Reset Entry Points

## Summary

Implemented fail-fast null contracts at the entry points of the three owned auth application services:

- `RegistrationApplicationService.register`
- `RegistrationVerificationApplicationService.resendCode`
- `RegistrationVerificationApplicationService.verifyAndLogin`
- `PasswordResetApplicationService.requestReset`
- `PasswordResetApplicationService.confirmReset`

The services now reject a null command immediately with:

`Objects.requireNonNull(command, "command must not be null")`

I did not change any downstream field-level validation or business flow beyond removing the old nullable command handling.

## Tests Added

Added one null-contract test per public method:

- `registerShouldRejectNullCommand`
- `resendCodeShouldRejectNullCommand`
- `verifyAndLoginShouldRejectNullCommand`
- `requestResetShouldRejectNullCommand`
- `confirmResetShouldRejectNullCommand`

## Verification

1. Ran the targeted Maven test slice first and confirmed the new tests failed for the expected reason before implementation.
2. Added the boundary checks in the three application services.
3. Re-ran verification through a narrowed JUnit ConsoleLauncher invocation for only the three owned auth test classes.

Result: all 35 selected tests passed.

## Notes

Running the repo-wide Maven test slice for these classes exposed an unrelated compile failure in `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java`:

- missing `assertThatThrownBy` static import

I did not modify that file because it is outside the owned scope for this task. To keep verification accurate, I used a direct JUnit runner for the three affected auth test classes instead.
