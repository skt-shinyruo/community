Status: DONE

Task 7 scope completed in the owned wallet files:
- Added `Objects.requireNonNull(command, "command must not be null")` at every public wallet application entry point that accepts a command object.
- Removed command-null handling from `WalletLedgerApplicationService.validateRequest(...)` so it now validates fields only after the public guard.
- Added the requested focused null-contract tests, including the new `WalletRewardApplicationServiceTest`.

Files changed:
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletTransferApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletTransferApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardApplicationServiceTest.java`

TDD cycle:
1. Added the brief’s null-command assertions to the six wallet test classes.
2. Ran the focused wallet suite and confirmed red state on the new null-contract expectations.
3. Added the minimal public-entry guards in the six owned application services.
4. Adjusted `WalletLedgerApplicationService.validateRequest(...)` to validate field content only after the public entry guard.
5. Re-ran the same focused wallet suite and confirmed green.

Verification:
- RED before implementation: `cd backend && mvn test -pl :community-app -Dtest=WalletLedgerApplicationServiceTest,WalletRechargeApplicationServiceTest,WalletTransferApplicationServiceTest,WalletWithdrawApplicationServiceTest,WalletMarketApplicationServiceTest,WalletRewardApplicationServiceTest`
- PASS after implementation: `cd backend && mvn test -pl :community-app -Dtest=WalletLedgerApplicationServiceTest,WalletRechargeApplicationServiceTest,WalletTransferApplicationServiceTest,WalletWithdrawApplicationServiceTest,WalletMarketApplicationServiceTest,WalletRewardApplicationServiceTest`

Self-review:
- `WalletLedgerApplicationService.recentTransactions(ListWalletTransactionsCommand)` now fails fast on null command and preserves the existing `userId` business validation path.
- `WalletLedgerApplicationService.post(WalletLedgerCommand)` now fails fast on null command before entering the transactional posting workflow.
- `WalletLedgerApplicationService.validateRequest(...)` no longer tolerates null commands and still preserves the existing field-level business validation, with an explicit empty-postings check before the existing minimum-size rule.
- `WalletRechargeApplicationService`, `WalletTransferApplicationService`, `WalletWithdrawApplicationService`, `WalletMarketApplicationService`, and `WalletRewardApplicationService` now all expose the same null-command contract at their public same-domain command entry points.
- No cross-domain wiring, repository behavior, transaction annotations, or non-wallet modules were changed.

Commit:
- `refactor: harden wallet command null contracts`
