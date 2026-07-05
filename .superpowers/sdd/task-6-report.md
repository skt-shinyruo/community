Task 6 Report: Harden Market, User, Analytics, And Level-Config Entry Points

Summary
- Hardened the owned same-domain `*ApplicationService` command entry points to fail fast with `Objects.requireNonNull(command, "command must not be null")`.
- Removed command-null handling from helper validators so they now stay focused on field and business validation.
- Added the focused null-contract tests from the brief, including new unit test classes for `MarketInventoryApplicationService` and `UserRewardApplicationService`.

Production changes
- `MarketAddressApplicationService`
  - Added public null guards to `createAddress` and `updateAddress`.
  - Removed command-null branches from `validateCreateRequest` and `validateUpdateRequest`.
- `MarketInventoryApplicationService`
  - Added a public null guard to `appendInventory`.
  - Removed the command-null branch from `validateInventoryRequest`.
- `MarketListingApplicationService`
  - Added public null guards to `createListing` and `updateListing`.
  - Removed command-null branches from `validateCreateRequest` and `validateUpdateRequest`.
- `MarketOrderApplicationService`
  - Added a public null guard to `createOrder(CreateMarketOrderCommand command)`.
- `AdminUserApplicationService`
  - Added a public null guard to `updateRole`.
  - Removed the prior null-tolerant delegation path.
- `UserModerationApplicationService`
  - Added a public null guard to `applyModeration`.
- `UserRegistrationApplicationService`
  - Added a public null guard to `createVerifiedRegistrationUser`.
  - Kept the existing `userId` business validation unchanged after the entry guard.
- `UserRewardApplicationService`
  - Added a public null guard to `apply`.
  - Kept the remaining business-driven early return behavior for invalid field content.
- `AnalyticsIngestApplicationService`
  - Added public null guards to `recordRequest` and `recordLoginSuccess`.
  - Preserved fail-open analytics write behavior for runtime repository errors.
- `UserLevelApplicationService`
  - Added public null guards to both `updateConfig` overloads.
  - Kept actor assignment in the overload after the entry guard.

Test changes
- Added null-contract assertions to:
  - `MarketAddressApplicationServiceTest`
  - `MarketListingApplicationServiceTest`
  - `MarketOrderApplicationServiceUnitTest`
  - `AdminUserApplicationServiceTest`
  - `UserModerationApplicationServiceTest`
  - `UserRegistrationApplicationServiceTest`
  - `AnalyticsIngestApplicationServiceTest`
  - `UserLevelApplicationServiceUnitTest`
- Added new focused unit tests:
  - `MarketInventoryApplicationServiceTest`
  - `UserRewardApplicationServiceTest`

TDD evidence
- Red:
  - Ran the focused Maven suite before implementation.
  - Observed failures on the new null-contract assertions and the new test classes, confirming the tests were exercising missing behavior rather than passing against existing code.
- Green:
  - Re-ran the same focused Maven suite after implementation and got `Tests run: 45, Failures: 0, Errors: 0, Skipped: 0`.

Verification
- Command run:
  - `cd backend && mvn test -pl :community-app -Dtest=MarketAddressApplicationServiceTest,MarketInventoryApplicationServiceTest,MarketListingApplicationServiceTest,MarketOrderApplicationServiceUnitTest,AdminUserApplicationServiceTest,UserModerationApplicationServiceTest,UserRegistrationApplicationServiceTest,UserRewardApplicationServiceTest,AnalyticsIngestApplicationServiceTest,UserLevelApplicationServiceUnitTest`
- Result:
  - Pass

Self-review
- Confirmed the public entry-point guard message is exactly `command must not be null`.
- Confirmed helper validators in the owned market and growth services no longer own command-presence checks.
- Confirmed the changes stayed within the owned Task 6 files and did not touch unrelated worker edits.

Concerns
- None.
