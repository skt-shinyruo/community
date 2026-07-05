# Community App Command Non-Null Contract Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every same-domain `*ApplicationService` command entry point in `community-app` reject `null` commands immediately and stop treating `null` as a valid control-flow input.

**Architecture:** This change is a boundary hardening pass, not a behavior redesign. Each touched public application entry point adds an explicit `Objects.requireNonNull(...)` guard, any downstream `command == null` branches are removed or split into non-null field checks, and tests are updated so null-command handling is asserted as a programmer-error contract.

**Tech Stack:** Java 17, Spring Boot, Maven Surefire, JUnit 5, AssertJ, Mockito

## Global Constraints

- Only modify same-domain `*ApplicationService` command entry points under `backend/community-app/src/main/java`.
- Do not change field-level validation semantics in this batch.
- Do not move required-field checks into command record constructors in this batch.
- Do not modify `controller`, `infrastructure`, `api adapter`, or `domain` command consumers in this batch.
- Do not add a new shared command-validation helper abstraction in this batch.
- Do not change public HTTP response shapes or error-code contracts unless a current test proves they already expose raw programmer errors.

---

## File Map

### Auth Session And Captcha

- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
  - Harden `login(LoginCommand)`, `refresh(RefreshCommand)`, and `logout(LogoutCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`
  - Harden `issue(IssueCaptchaCommand)`.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
  - Add explicit null-command contract tests for session entry points.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/CaptchaApplicationServiceTest.java`
  - Add explicit null-command contract test for captcha issuance.

### Auth Registration And Password Reset

- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
  - Harden `register(RegisterCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
  - Harden `resendCode(ResendRegisterCodeCommand)` and `verifyAndLogin(VerifyRegisterCodeCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
  - Harden `requestReset(RequestPasswordResetCommand)` and `confirmReset(ConfirmPasswordResetCommand)`.
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`
  - Add null-command contract tests and keep existing business assertions intact.

### Content And Social Core Write Paths

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
  - Harden `create(String, CreateCommentCommand)` and `update(UpdateCommentCommand)`, remove redundant helper null guards in `createFromCommand(...)` and `updateFromCommand(...)`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
  - Harden `create(String, CreatePostCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java`
  - Harden `prepareUpload(PreparePostMediaUploadCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java`
  - Harden `takeAction(TakeModerationActionCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
  - Harden `block(BlockCommand)` and `unblock(UnblockCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
  - Harden `follow(FollowCommand)` and `unfollow(UnfollowCommand)`.
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
  - Harden `setLike(SetLikeCommand)`.
- Corresponding tests:
  - `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`

### Dispatch, Projection, And Search Flows

- `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
  - Harden public entry points and remove now-redundant command-null branches in `commandForContentEvent(...)` and `commandForSocialEvent(...)`.
- `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationService.java`
- Corresponding tests:
  - `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserEventDispatchApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationServiceTest.java`

### Drive And Avatar Upload

- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveEntryApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
  - Harden public command entry points and simplify private command validators once the boundary guard exists.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
  - Harden `createUploadSession(UUID, UUID, CreateAvatarUploadSessionCommand)`.
- Corresponding tests:
  - `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveEntryApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`

### Market, User, Analytics, And Level Config

- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketAddressApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketInventoryApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketListingApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
  - Harden public command entry points; simplify helper command-null checks in address, inventory, and listing validators.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java`
  - Harden public command entry points while preserving non-null field behavior.
- `backend/community-app/src/main/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationService.java`
  - Harden `recordRequest(...)` and `recordLoginSuccess(...)`.
- `backend/community-app/src/main/java/com/nowcoder/community/growth/application/UserLevelApplicationService.java`
  - Harden both public `UpdateUserLevelConfigCommand` entry points and rename the overload parameter from `request` to `command` for consistency.
- Tests:
  - `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketAddressApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketListingApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketInventoryApplicationServiceTest.java` (new)
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRewardApplicationServiceTest.java` (new)
  - `backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/growth/application/UserLevelApplicationServiceUnitTest.java`

### Wallet

- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
  - Harden `recentTransactions(...)` and `post(WalletLedgerCommand)`; simplify `validateRequest(...)`.
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletTransferApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java`
- `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
  - Harden public command entry points and keep current field/value semantics intact.
- Tests:
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletTransferApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java`
  - `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardApplicationServiceTest.java` (new)

## Task 1: Harden Auth Session And Captcha Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/CaptchaApplicationServiceTest.java`

**Interfaces:**
- Consumes:
  - `LoginApplicationService.login(LoginCommand command): LoginResult`
  - `LoginApplicationService.refresh(RefreshCommand command): RefreshResult`
  - `LoginApplicationService.logout(LogoutCommand command): void`
  - `CaptchaApplicationService.issue(IssueCaptchaCommand command): CaptchaIssueResult`
- Produces:
  - explicit `Objects.requireNonNull(command, "command must not be null")` at each method entry,
  - direct field reads after the guard,
  - null-contract tests named `*ShouldRejectNullCommand`.

- [ ] **Step 1: Write the failing tests**

```java
// LoginApplicationServiceTest.java
@Test
void loginShouldRejectNullCommand() {
    assertThatThrownBy(() -> authService.login(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void refreshShouldRejectNullCommand() {
    assertThatThrownBy(() -> authService.refresh(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void logoutShouldRejectNullCommand() {
    assertThatThrownBy(() -> authService.logout(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// CaptchaApplicationServiceTest.java
@Test
void issueShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.issue(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LoginApplicationServiceTest,CaptchaApplicationServiceTest
```

Expected: FAIL with missing `NullPointerException` assertions because current code still tolerates or defers null handling.

- [ ] **Step 3: Write the minimal implementation**

```java
// LoginApplicationService.java
import java.util.Objects;

public LoginResult login(LoginCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String username = command.username();
    String password = command.password();
    String captchaId = command.captchaId();
    String captchaCode = command.captchaCode();
    String ip = command.clientIp();
    String ipSource = command.clientIpSource();
    // keep the remaining body unchanged
}

public RefreshResult refresh(RefreshCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String refreshToken = command.refreshToken();
    // keep the remaining body unchanged
}

public void logout(LogoutCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String refreshToken = command.refreshToken();
    if (StringUtils.hasText(refreshToken)) {
        refreshTokenService.revokeFamilyByToken(refreshToken);
    }
}

// CaptchaApplicationService.java
import java.util.Objects;

public CaptchaIssueResult issue(IssueCaptchaCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    enforceIssueRateLimit(command.clientIp());
    // keep the remaining body unchanged
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=LoginApplicationServiceTest,CaptchaApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/CaptchaApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application/CaptchaApplicationServiceTest.java
git commit -m "refactor: harden auth session command null contracts"
```

## Task 2: Harden Auth Registration And Password Reset Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`

**Interfaces:**
- Consumes:
  - `register(RegisterCommand command): RegisterResult`
  - `resendCode(ResendRegisterCodeCommand command): RegisterCodeResendResult`
  - `verifyAndLogin(VerifyRegisterCodeCommand command): LoginResult`
  - `requestReset(RequestPasswordResetCommand command): PasswordResetRequestResult`
  - `confirmReset(ConfirmPasswordResetCommand command): boolean`
- Produces:
  - explicit non-null boundary checks on all five methods,
  - removal of `command == null ? ... : ...` local-variable setup,
  - one null-contract test per public method.

- [ ] **Step 1: Write the failing tests**

```java
// RegistrationApplicationServiceTest.java
@Test
void registerShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.register(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// RegistrationVerificationApplicationServiceTest.java
@Test
void resendCodeShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.resendCode(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void verifyAndLoginShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.verifyAndLogin(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// PasswordResetApplicationServiceTest.java
@Test
void requestResetShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.requestReset(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void confirmResetShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.confirmReset(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,PasswordResetApplicationServiceTest
```

Expected: FAIL because the services still accept null commands or derive nullable locals from them.

- [ ] **Step 3: Write the minimal implementation**

```java
// RegistrationApplicationService.java
import java.util.Objects;

public RegisterResult register(RegisterCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    // keep the existing body unchanged
}

// RegistrationVerificationApplicationService.java
import java.util.Objects;

public RegisterCodeResendResult resendCode(ResendRegisterCodeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String registrationToken = command.registrationToken();
    String captchaId = command.captchaId();
    String captchaCode = command.captchaCode();
    // keep the remaining body unchanged
}

public LoginResult verifyAndLogin(VerifyRegisterCodeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String registrationToken = command.registrationToken();
    String code = command.code();
    // keep the remaining body unchanged
}

// PasswordResetApplicationService.java
import java.util.Objects;

public PasswordResetRequestResult requestReset(RequestPasswordResetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String email = command.email();
    String captchaId = command.captchaId();
    String captchaCode = command.captchaCode();
    String clientIp = command.clientIp();
    // keep the remaining body unchanged
}

public boolean confirmReset(ConfirmPasswordResetCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String resetToken = command.resetToken();
    String newPassword = command.newPassword();
    String captchaId = command.captchaId();
    String captchaCode = command.captchaCode();
    // keep the remaining body unchanged
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,PasswordResetApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java
git commit -m "refactor: harden auth registration command null contracts"
```

## Task 3: Harden Content And Social Core Write Paths

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`

**Interfaces:**
- Consumes:
  - `create(String idempotencyKey, CreateCommentCommand command): CommentCreateResult`
  - `update(UpdateCommentCommand command): void`
  - `create(String idempotencyKey, CreatePostCommand command): PostCreateResult`
  - `prepareUpload(PreparePostMediaUploadCommand command): PostMediaUploadSessionResult`
  - `takeAction(TakeModerationActionCommand command): UUID`
  - `block(BlockCommand command): void`
  - `unblock(UnblockCommand command): void`
  - `follow(FollowCommand command): void`
  - `unfollow(UnfollowCommand command): void`
  - `setLike(SetLikeCommand command): LikeResult`
- Produces:
  - immediate null-command rejection at the public entry boundary,
  - helper methods in `CommentApplicationService` and `PostPublishingApplicationService` that no longer carry redundant command-null branches.

- [ ] **Step 1: Write the failing tests**

```java
// CommentApplicationServiceTest.java
@Test
void createShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.create("idem-null", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void updateShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.update(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// PostPublishingApplicationServiceTest.java
@Test
void createShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.create("idem-null", null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// PostMediaApplicationServiceTest.java
@Test
void prepareUploadShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.prepareUpload(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// ModerationApplicationServiceTest.java
@Test
void takeActionShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.takeAction(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// BlockApplicationServiceTest.java
@Test
void blockShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.block(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void unblockShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.unblock(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// FollowApplicationServiceTest.java
@Test
void followShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.follow(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void unfollowShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.unfollow(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// LikeApplicationServiceTest.java
@Test
void setLikeShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.setLike(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostMediaApplicationServiceTest,ModerationApplicationServiceTest,BlockApplicationServiceTest,FollowApplicationServiceTest,LikeApplicationServiceTest
```

Expected: FAIL on the new null-contract assertions.

- [ ] **Step 3: Write the minimal implementation**

```java
// CommentApplicationService.java
import java.util.Objects;

public CommentCreateResult create(String idempotencyKey, CreateCommentCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return createFromCommand(idempotencyKey, command);
}

public void update(UpdateCommentCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    updateFromCommand(command);
}

private CommentCreateResult createFromCommand(String idempotencyKey, CreateCommentCommand command) {
    UUID userId = command.userId();
    UUID postId = command.postId();
    // remove the old command-null branch and keep field validation unchanged
}

private void updateFromCommand(UpdateCommentCommand command) {
    UUID userId = command.userId();
    // remove the old command-null branch and keep the remaining body unchanged
}

// PostPublishingApplicationService.java
public PostCreateResult create(String idempotencyKey, CreatePostCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    // keep the remaining body unchanged
}

// PostMediaApplicationService.java, ModerationApplicationService.java,
// BlockApplicationService.java, FollowApplicationService.java,
// LikeApplicationService.java
// Add the same first-line Objects.requireNonNull(command, "command must not be null");
// and keep all current field validation/business logic unchanged.
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=CommentApplicationServiceTest,PostPublishingApplicationServiceTest,PostMediaApplicationServiceTest,ModerationApplicationServiceTest,BlockApplicationServiceTest,FollowApplicationServiceTest,LikeApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/CommentApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostPublishingApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/PostMediaApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/ModerationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/application/BlockApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/application/FollowApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostPublishingApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/PostMediaApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/ModerationApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/BlockApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/FollowApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java
git commit -m "refactor: harden content and social command null contracts"
```

## Task 4: Harden Dispatch, Projection, And Search Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserEventDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationServiceTest.java`

**Interfaces:**
- Consumes the existing command-based public methods in these classes.
- Produces:
  - public entry-point null guards for dispatch/projection/search methods,
  - helper methods in `NoticeProjectionApplicationService` that assume a non-null command after public entry validation,
  - existing business-invalid checks (blank payload, unsupported event, etc.) unchanged.

- [ ] **Step 1: Write the failing tests**

```java
// ContentEventDispatchApplicationServiceTest.java
@Test
void dispatchShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.dispatch(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// SocialEventDispatchApplicationServiceTest.java
@Test
void dispatchShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.dispatch(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// UserEventDispatchApplicationServiceTest.java
@Test
void dispatchShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.dispatch(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// NoticeApplicationServiceTest.java
@Test
void createNoticeShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.createNotice(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void listNoticeItemsShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.listNoticeItems(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void markReadShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.markRead(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// NoticeProjectionApplicationServiceTest.java
@Test
void projectContentEventShouldRejectNullCommand() {
    assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class)).projectContentEvent(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void projectSocialEventShouldRejectNullCommand() {
    assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class)).projectSocialEvent(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void projectContentEventReliablyShouldRejectNullCommand() {
    assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class)).projectContentEventReliably(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void projectSocialEventReliablyShouldRejectNullCommand() {
    assertThatThrownBy(() -> projectionService(mock(NoticeApplicationService.class), mock(NoticeProjectionEventRecorder.class)).projectSocialEventReliably(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// SearchApplicationServiceTest.java
@Test
void searchPostsShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.searchPosts(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void syncPostProjectionShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.syncPostProjection(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void deletePostShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.deletePost(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// SearchPostProjectionApplicationServiceTest.java
@Test
void projectPostFromOutboxShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.projectPostFromOutbox(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// TaskProgressApplicationServiceTest.java
@Test
void triggerPostPublishedShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.triggerPostPublished(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void triggerCommentCreatedShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.triggerCommentCreated(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void triggerLikeCreatedShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.triggerLikeCreated(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void triggerLikeRemovedShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.triggerLikeRemoved(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// TaskProgressOutboxDispatchApplicationServiceTest.java
@Test
void dispatchShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.dispatch(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// ImPolicyEventDispatchApplicationServiceTest.java
@Test
void dispatchShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.dispatch(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionApplicationServiceTest,SearchApplicationServiceTest,SearchPostProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest
```

Expected: FAIL on the new null-contract assertions.

- [ ] **Step 3: Write the minimal implementation**

```java
// For each public command entry point in the files above:
// add Objects.requireNonNull(command, "command must not be null"); as the first executable statement.

// Example: ContentEventDispatchApplicationService.java
public void dispatch(DispatchContentEventCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!StringUtils.hasText(command.payloadJson())) {
        throw new IllegalStateException("content event outbox payload is blank");
    }
    // keep the remaining body unchanged
}

// Example: NoticeProjectionApplicationService.java
public void projectContentEvent(ProjectContentNoticeCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    try {
        project(commandForContentEvent(command));
    } catch (RuntimeException e) {
        log.warn("[notice] projection failed after commit (eventId={}, type={}): {}", command.sourceEventId(), command.eventType(), e.toString());
    }
}

NoticeProjection commandForContentEvent(ProjectContentNoticeCommand command) {
    // remove the old `if (command == null) return null;` branch
    if (ContentEventTypes.COMMENT_CREATED.equals(command.eventType()) && command.payload() instanceof CommentPayload payload) {
        return projection(command.sourceEventId(), command.eventType(), NoticeTopic.COMMENT, payload.getTargetUserId(), payload);
    }
    return null;
}

// Example: TaskProgressOutboxDispatchApplicationService.java
public void dispatch(DispatchTaskProgressEventCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.kind() == null || !StringUtils.hasText(command.payloadJson())) {
        throw new IllegalStateException("task progress dispatch payload is invalid");
    }
    // keep the remaining body unchanged
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=ContentEventDispatchApplicationServiceTest,SocialEventDispatchApplicationServiceTest,UserEventDispatchApplicationServiceTest,NoticeApplicationServiceTest,NoticeProjectionApplicationServiceTest,SearchApplicationServiceTest,SearchPostProjectionApplicationServiceTest,TaskProgressApplicationServiceTest,TaskProgressOutboxDispatchApplicationServiceTest,ImPolicyEventDispatchApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserEventDispatchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/content/application/ContentEventDispatchApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/social/application/SocialEventDispatchApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserEventDispatchApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/notice/application/NoticeProjectionApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/search/application/SearchPostProjectionApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/application/TaskProgressOutboxDispatchApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/im/application/ImPolicyEventDispatchApplicationServiceTest.java
git commit -m "refactor: harden dispatch command null contracts"
```

## Task 5: Harden Drive And Avatar Upload Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveEntryApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveEntryApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`

**Interfaces:**
- Consumes:
  - `createFolder(CreateDriveFolderCommand)`
  - `rename(RenameDriveEntryCommand)`
  - `move(MoveDriveEntryCommand)`
  - `createShare(CreateDriveShareCommand)`
  - `verifyShare(VerifyDriveShareCommand)`
  - `prepareUpload(PrepareDriveUploadCommand)`
  - `completeUpload(CompleteDriveUploadCommand)`
  - `createUploadSession(UUID actorUserId, UUID userId, CreateAvatarUploadSessionCommand command)`
- Produces:
  - explicit null guards in public entry points,
  - helper validators in `DriveUploadApplicationService` that only validate fields, not command presence.

- [ ] **Step 1: Write the failing tests**

```java
// DriveEntryApplicationServiceTest.java
@Test
void createFolderShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.createFolder(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void renameShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.rename(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void moveShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.move(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// DriveShareApplicationServiceTest.java
@Test
void createShareShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.createShare(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void verifyShareShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.verifyShare(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// DriveUploadApplicationServiceTest.java
@Test
void prepareUploadShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.prepareUpload(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void completeUploadShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.completeUpload(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// UserAvatarApplicationServiceTest.java
@Test
void createUploadSessionShouldRejectNullCommand() {
    UserAvatarApplicationService service = new UserAvatarApplicationService(avatarStoragePort, userRepository);
    UUID userId = uuid(7);

    assertThatThrownBy(() -> service.createUploadSession(userId, userId, null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=DriveEntryApplicationServiceTest,DriveShareApplicationServiceTest,DriveUploadApplicationServiceTest,UserAvatarApplicationServiceTest
```

Expected: FAIL on the new null-contract assertions.

- [ ] **Step 3: Write the minimal implementation**

```java
// DriveEntryApplicationService.java
public DriveEntryResult createFolder(CreateDriveFolderCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    // keep the remaining body unchanged
}

public DriveEntryResult rename(RenameDriveEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.entryId() == null) {
        throw new BusinessException(INVALID_ARGUMENT, "重命名参数非法");
    }
    // keep the remaining body unchanged
}

public DriveEntryResult move(MoveDriveEntryCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.entryId() == null) {
        throw new BusinessException(INVALID_ARGUMENT, "移动参数非法");
    }
    // keep the remaining body unchanged
}

// DriveShareApplicationService.java, DriveUploadApplicationService.java,
// UserAvatarApplicationService.java
// Add the same entry-point guard and remove any helper-level command-null branch
// that becomes redundant after the public method rejects null.
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=DriveEntryApplicationServiceTest,DriveShareApplicationServiceTest,DriveUploadApplicationServiceTest,UserAvatarApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveEntryApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveShareApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/drive/application/DriveUploadApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAvatarApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveEntryApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveUploadApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java
git commit -m "refactor: harden drive command null contracts"
```

## Task 6: Harden Market, User, Analytics, And Level-Config Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketAddressApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketInventoryApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketListingApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/growth/application/UserLevelApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketAddressApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketInventoryApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketListingApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRewardApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/growth/application/UserLevelApplicationServiceUnitTest.java`

**Interfaces:**
- Consumes the public command entry points in the files above.
- Produces:
  - explicit public null guards,
  - helper validators that no longer own command presence checks,
  - new focused unit tests for the three classes that currently have no same-name test file (`MarketInventoryApplicationService`, `UserRewardApplicationService`, `WalletRewardApplicationService` is handled in Task 7).

- [ ] **Step 1: Write the failing tests**

```java
// MarketAddressApplicationServiceTest.java
@Test
void createAddressShouldRejectNullCommand() {
    assertThatThrownBy(() -> marketAddressService.createAddress(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void updateAddressShouldRejectNullCommand() {
    assertThatThrownBy(() -> marketAddressService.updateAddress(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// MarketInventoryApplicationServiceTest.java (new)
@ExtendWith(MockitoExtension.class)
class MarketInventoryApplicationServiceTest {
    @Test
    void appendInventoryShouldRejectNullCommand() {
        MarketInventoryApplicationService service = new MarketInventoryApplicationService(
                mock(MarketListingRepository.class),
                mock(MarketInventoryRepository.class),
                new UuidV7Generator()
        );

        assertThatThrownBy(() -> service.appendInventory(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}

// AdminUserApplicationServiceTest.java
@Test
void updateRoleShouldRejectNullCommand() {
    AdminUserApplicationService service = service();

    assertThatThrownBy(() -> service.updateRole(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// AnalyticsIngestApplicationServiceTest.java
@Test
void recordRequestShouldRejectNullCommand() {
    AnalyticsIngestApplicationService service = newService(mock(AnalyticsRepository.class), mock(AnalyticsUserOrdinalRepository.class));

    assertThatThrownBy(() -> service.recordRequest(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void recordLoginSuccessShouldRejectNullCommand() {
    AnalyticsIngestApplicationService service = newService(mock(AnalyticsRepository.class), mock(AnalyticsUserOrdinalRepository.class));

    assertThatThrownBy(() -> service.recordLoginSuccess(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// UserLevelApplicationServiceUnitTest.java
@Test
void updateConfigShouldRejectNullCommand() {
    UserLevelApplicationService service = new UserLevelApplicationService(
            mock(UserTaskProgressRepository.class),
            mock(UserLevelRuleConfigRepository.class),
            mock(GrowthBusinessTimeService.class),
            new UserLevelDomainService(),
            new UuidV7Generator()
    );

    assertThatThrownBy(() -> service.updateConfig((UpdateUserLevelConfigCommand) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void updateConfigWithActorShouldRejectNullCommand() {
    UserLevelApplicationService service = new UserLevelApplicationService(
            mock(UserTaskProgressRepository.class),
            mock(UserLevelRuleConfigRepository.class),
            mock(GrowthBusinessTimeService.class),
            new UserLevelDomainService(),
            new UuidV7Generator()
    );

    assertThatThrownBy(() -> service.updateConfig(uuid(99), null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// UserRewardApplicationServiceTest.java (new)
@ExtendWith(MockitoExtension.class)
class UserRewardApplicationServiceTest {
    @Test
    void applyShouldRejectNullCommand() {
        UserRewardApplicationService service = new UserRewardApplicationService(mock(WalletRewardActionApi.class));

        assertThatThrownBy(() -> service.apply(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=MarketAddressApplicationServiceTest,MarketInventoryApplicationServiceTest,MarketListingApplicationServiceTest,MarketOrderApplicationServiceUnitTest,AdminUserApplicationServiceTest,UserModerationApplicationServiceTest,UserRegistrationApplicationServiceTest,UserRewardApplicationServiceTest,AnalyticsIngestApplicationServiceTest,UserLevelApplicationServiceUnitTest
```

Expected: FAIL on the new null-contract assertions or on missing new test classes before implementation is complete.

- [ ] **Step 3: Write the minimal implementation**

```java
// MarketAddressApplicationService.java
public MarketAddressResult createAddress(CreateMarketAddressCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    validateUserId(command.userId());
    validateCreateRequest(command);
    // keep the remainder of the method body unchanged
}

private void validateCreateRequest(CreateMarketAddressCommand command) {
    // remove the old command-null branch
    requireText(command.receiverName(), "receiverName");
    requireText(command.receiverPhone(), "receiverPhone");
    requireText(command.province(), "province");
    requireText(command.city(), "city");
    requireText(command.district(), "district");
    requireText(command.detailAddress(), "detailAddress");
}

// MarketInventoryApplicationService.java
public void appendInventory(AddMarketInventoryBatchCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    validateInventoryRequest(command);
    // keep the remainder of the method body unchanged
}

private void validateInventoryRequest(AddMarketInventoryBatchCommand command) {
    // remove the old command-null branch
    if (!StringUtils.hasText(command.payloadType())) {
        throw new BusinessException(INVALID_ARGUMENT, "inventory payloadType must not be blank");
    }
    if (command.payloads() == null || command.payloads().isEmpty()) {
        throw new BusinessException(INVALID_ARGUMENT, "inventory payloads must not be empty");
    }
    boolean hasBlankPayload = command.payloads().stream().anyMatch(payload -> !StringUtils.hasText(payload));
    if (hasBlankPayload) {
        throw new BusinessException(INVALID_ARGUMENT, "inventory payload must not be blank");
    }
}

// MarketListingApplicationService.java
public MarketListingResult createListing(CreateMarketListingCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    validateCreateRequest(command);
    // keep the remainder of the method body unchanged
}

private void validateCreateRequest(CreateMarketListingCommand command) {
    // remove the old command-null branch
    validateCommonFields(
            command.title(),
            command.description(),
            command.unitPrice(),
            command.minPurchaseQuantity(),
            command.maxPurchaseQuantity()
    );
    // keep the remaining create-specific branches unchanged
}

// MarketOrderApplicationService.java, UserModerationApplicationService.java,
// UserRegistrationApplicationService.java
// Add entry-point Objects.requireNonNull(command, "command must not be null");
// and keep existing field/business checks unchanged.

// AdminUserApplicationService.java
public void updateRole(UpdateUserRoleCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    String reason = userRoleDomainService.requireValidCommand(
            true,
            command.targetUserId(),
            command.type(),
            command.reason(),
            command.confirm()
    );
    // keep the remainder of the method body unchanged
}

// UserRewardApplicationService.java
public void apply(RewardCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.userId() == null
            || command.delta() == 0
            || !StringUtils.hasText(command.sourceEventId())
            || !StringUtils.hasText(command.sourceEventType())) {
        return;
    }
    walletRewardActionApi.applyDelta(
            "wallet-reward:" + command.sourceEventId().trim(),
            command.userId(),
            command.delta(),
            command.sourceEventType().trim()
    );
}

// AnalyticsIngestApplicationService.java
public void recordRequest(RecordRequestCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    LocalDate today = LocalDate.now(clock);
    AnalyticsRequestEvent event = new AnalyticsRequestEvent(
            command.ip(),
            command.userId(),
            command.recordUv(),
            command.recordDau()
    );
    // keep the remaining body unchanged
}

public void recordLoginSuccess(RecordLoginSuccessCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (!command.recordDau()) {
        return;
    }
    AnalyticsRequestEvent event = new AnalyticsRequestEvent(null, command.userId(), false, true);
    // keep the remaining body unchanged
}

// UserLevelApplicationService.java
public UserLevelConfigResult updateConfig(UpdateUserLevelConfigCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return updateConfigInternal(command);
}

public UserLevelConfigResult updateConfig(UUID actorUserId, UpdateUserLevelConfigCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    command.setActorUserId(actorUserId);
    return updateConfigInternal(command);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=MarketAddressApplicationServiceTest,MarketInventoryApplicationServiceTest,MarketListingApplicationServiceTest,MarketOrderApplicationServiceUnitTest,AdminUserApplicationServiceTest,UserModerationApplicationServiceTest,UserRegistrationApplicationServiceTest,UserRewardApplicationServiceTest,AnalyticsIngestApplicationServiceTest,UserLevelApplicationServiceUnitTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketAddressApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketInventoryApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketListingApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/market/application/MarketOrderApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRewardApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/growth/application/UserLevelApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketAddressApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketInventoryApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketListingApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/market/application/MarketOrderApplicationServiceUnitTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRewardApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/analytics/application/AnalyticsIngestApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/growth/application/UserLevelApplicationServiceUnitTest.java
git commit -m "refactor: harden market and user command null contracts"
```

## Task 7: Harden Wallet Entry Points

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletTransferApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletTransferApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardApplicationServiceTest.java`

**Interfaces:**
- Consumes all wallet application entry points that accept command objects.
- Produces:
  - explicit null guards for every public command entry point,
  - `WalletLedgerApplicationService.validateRequest(...)` that no longer performs command-null handling,
  - new focused unit test for `WalletRewardApplicationService`.

- [ ] **Step 1: Write the failing tests**

```java
// WalletLedgerApplicationServiceTest.java
@Test
void recentTransactionsShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.recentTransactions((ListWalletTransactionsCommand) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void postShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.post((WalletLedgerCommand) null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// WalletRechargeApplicationServiceTest.java
@Test
void rechargeShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.recharge(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// WalletTransferApplicationServiceTest.java
@Test
void transferShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.transfer(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// WalletWithdrawApplicationServiceTest.java
@Test
void withdrawShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.withdraw(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// WalletMarketApplicationServiceTest.java
@Test
void escrowOrderShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.escrowOrder(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void releaseOrderShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.releaseOrder(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

@Test
void refundOrderShouldRejectNullCommand() {
    assertThatThrownBy(() -> service.refundOrder(null))
            .isInstanceOf(NullPointerException.class)
            .hasMessage("command must not be null");
}

// WalletRewardApplicationServiceTest.java (new)
@ExtendWith(MockitoExtension.class)
class WalletRewardApplicationServiceTest {
    @Test
    void issueShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.issue(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void revokeShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.revoke(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }

    @Test
    void applyDeltaShouldRejectNullCommand() {
        WalletRewardApplicationService service = new WalletRewardApplicationService(
                mock(WalletAccountApplicationService.class),
                mock(WalletLedgerApplicationService.class)
        );

        assertThatThrownBy(() -> service.applyDelta(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessage("command must not be null");
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletLedgerApplicationServiceTest,WalletRechargeApplicationServiceTest,WalletTransferApplicationServiceTest,WalletWithdrawApplicationServiceTest,WalletMarketApplicationServiceTest,WalletRewardApplicationServiceTest
```

Expected: FAIL on the new null-contract assertions or on the new test file before implementation exists.

- [ ] **Step 3: Write the minimal implementation**

```java
// WalletLedgerApplicationService.java
public List<WalletTransactionResult> recentTransactions(ListWalletTransactionsCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.userId() == null) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "userId must not be null");
    }
    // keep the remainder of the method body unchanged
}

public WalletTxnResult post(WalletLedgerCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    return postInsideTransaction(command);
}

private void validateRequest(WalletLedgerCommand command) {
    // remove the old command-null branch
    String requestId = validateText(command.requestId(), "requestId");
    WalletTxnType txnType = command.txnType();
    List<WalletPosting> postings = command.postings();
    if (txnType == null) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "txnType must not be null");
    }
    if (postings == null || postings.isEmpty()) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "postings must not be empty");
    }
}

// WalletRechargeApplicationService.java, WalletTransferApplicationService.java,
// WalletWithdrawApplicationService.java, WalletMarketApplicationService.java
// Add Objects.requireNonNull(command, "command must not be null"); to each public command entry point.

// WalletRewardApplicationService.java
public void issue(WalletRewardCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.amount() <= 0) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
    }
    postRewardTxn(command.requestId(), command.userId(), command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
}

public void revoke(WalletRewardCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.amount() <= 0) {
        throw new BusinessException(WalletErrorCode.INVALID_REQUEST, "reward amount must be positive");
    }
    postRewardTxn(command.requestId(), command.userId(), -command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
}

public void applyDelta(WalletRewardCommand command) {
    Objects.requireNonNull(command, "command must not be null");
    if (command.amount() == 0) {
        return;
    }
    postRewardTxn(command.requestId(), command.userId(), command.amount(), command.sourceType(), WalletTxnType.REWARD_ISSUE);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest=WalletLedgerApplicationServiceTest,WalletRechargeApplicationServiceTest,WalletTransferApplicationServiceTest,WalletWithdrawApplicationServiceTest,WalletMarketApplicationServiceTest,WalletRewardApplicationServiceTest
```

Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletTransferApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletMarketApplicationService.java \
  backend/community-app/src/main/java/com/nowcoder/community/wallet/application/WalletRewardApplicationService.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletLedgerApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRechargeApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletTransferApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletWithdrawApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletMarketApplicationServiceTest.java \
  backend/community-app/src/test/java/com/nowcoder/community/wallet/application/WalletRewardApplicationServiceTest.java
git commit -m "refactor: harden wallet command null contracts"
```

## Task 8: Final Sweep And Verification

**Files:**
- Modify: only files from Tasks 1-7 if final cleanup is required after the broad verification pass.
- Test: no new files; this task is verification plus any last small fixups discovered by the verification run.

**Interfaces:**
- Consumes the completed code from Tasks 1-7.
- Produces:
  - no remaining `command == null ?` or `if (command == null)` command-boundary logic in application services,
  - full green targeted application-service test pass,
  - a clean summary of remaining intentional non-command null returns, if any.

- [ ] **Step 1: Run a grep sweep for leftover command-null handling**

Run:

```bash
rg -n "command == null \\?|if \\(command == null\\)" backend/community-app/src/main/java/com/nowcoder/community/*/application
```

Expected: no matches

- [ ] **Step 2: Run the full application-service verification batch**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ApplicationServiceTest,*ApplicationServiceUnitTest'
```

Expected: PASS

- [ ] **Step 3: If verification reveals a straggler, fix only the failing file**

```text
1. add the missing Objects.requireNonNull(command, "command must not be null");
2. remove the redundant command-null branch;
3. add or adjust the matching null-contract test;
4. do not expand scope beyond the failing file.
```

- [ ] **Step 4: Re-run verification**

Run:

```bash
rg -n "command == null \\?|if \\(command == null\\)" backend/community-app/src/main/java/com/nowcoder/community/*/application
cd backend
mvn test -pl :community-app -Dtest='*ApplicationServiceTest,*ApplicationServiceUnitTest'
```

Expected: no grep matches, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java backend/community-app/src/test/java
git commit -m "refactor: enforce application command non-null contracts"
```
