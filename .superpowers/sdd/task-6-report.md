Status: DONE_WITH_CONCERNS

Task 6 scope completed in the owned files:
- Broadened `TransactionBoundaryArchTest` from a fixed allowlist to all production `*ApplicationService` classes under `.application`.
- Removed public transactional self-invocation in `CommentApplicationService` by routing public create/update wrappers through private helpers.
- Removed public transactional self-invocation in `WalletLedgerApplicationService` by routing public post overloads through a private helper.

Files changed:
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/TransactionBoundaryArchTest.java`

TDD / guardrail cycle:
1. Broadened `TransactionBoundaryArchTest` first.
2. Ran `cd backend && mvn test -pl :community-app -am -Dtest=TransactionBoundaryArchTest`.
3. Confirmed red state. Initial failures included the expected Task 6 self-invocations plus additional pre-existing violations outside Task 6 scope.
4. Refactored only the two owned application services.
5. Ran focused behavior verification.
6. Re-ran `TransactionBoundaryArchTest` to confirm the Task 6 violations were removed.

Verification:
- PASS: `cd backend && mvn test -pl :community-app -am -Dtest='CommentApplicationServiceTest,WalletLedgerApplicationServiceTest'`
- FAIL (out-of-scope remaining violations only): `cd backend && mvn test -pl :community-app -am -Dtest=TransactionBoundaryArchTest`

Remaining `TransactionBoundaryArchTest` violations after Task 6 changes:
- `com.nowcoder.community.growth.application.UserLevelApplicationService.updateConfig(...) -> updateConfig(...)`
- `com.nowcoder.community.market.application.MarketOrderApplicationService.createOrder(...) -> createOrder(...)`
- `com.nowcoder.community.market.application.MarketWalletActionRecoveryApplicationService.reconcileOnce(...) -> recoverExpiredProcessing(...)`
- `com.nowcoder.community.wallet.application.WalletRechargeApplicationService.recharge(...) -> complete(...)`
- `com.nowcoder.community.wallet.application.WalletTransferApplicationService.transfer(...) -> create(...)`
- `com.nowcoder.community.wallet.application.WalletWithdrawApplicationService.withdraw(...) -> request(...)`

Why status is `DONE_WITH_CONCERNS`:
- The owned Task 6 code changes are complete and behavior tests pass.
- The broadened guardrail now correctly exposes additional production violations outside the files assigned to Task 6, so the final arch test cannot pass without widening scope.

Self-review:
- `CommentApplicationService` behavior is preserved: both public create entry points still validate and execute the same idempotent transactional body, and `addComment` still returns the created comment id from the same path.
- `CommentApplicationService` update behavior is preserved: both public update entry points still share the same validation and repository update flow.
- `WalletLedgerApplicationService` behavior is preserved: all public `post` entry points still execute the original validation, idempotency/replay, posting, and success-marking logic in the same transaction boundary.
- No event dispatch, API adapter naming, or docs were touched.
