# Community User Business Line Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the user owner boundary around registration, credentials, role/status/moderation, avatar projection, published profile contracts, and reward/event paths so the `user` business line matches the approved hardening spec end-to-end.

**Architecture:** Keep `user` as the owner of account facts, password hash acceptance, role/status/moderation state, avatar business projection, `securityVersion`, and DB refresh sessions; keep `auth` as the owner of login, refresh, captcha, registration draft, and password-reset flow orchestration. All new behavior must stay inside strict DDD tactical layering: inbound adapters call same-domain `*ApplicationService`, application orchestrates transactions and foreign `api.*`, domain owns business rules, and infrastructure implements technical ports.

**Tech Stack:** Java 17, Spring Boot, Spring Security, MyBatis, Redis, H2 test schema, MySQL migration SQL, JUnit 5, Mockito, AssertJ, Maven.

## Global Constraints

- All backend business code in `backend/community-app` MUST follow the strict DDD Tactical Layering rules from `AGENTS.md`.
- `user` remains the owner of account ID, username, email, password hash acceptance, role, account status, moderation state, avatar business projection, `securityVersion`, and DB refresh session facts.
- `auth` remains the owner of login, refresh, logout, captcha, registration draft, registration code, and password-reset flow orchestration.
- `wallet` keeps balance and account-status facts; `community-oss` keeps avatar object facts and canonical public URLs.
- Registration create must verify an owner-issued prepared proof; expired proofs require a fresh prepare and must not be refreshed in place.
- `securityVersion` for active users must always be a positive current owner fact; high-risk freshness accepts only exact current-version matches.
- `UserProfileView` and `UserProfileQueryApi` must expose only user owner facts and must not carry wallet facts.
- Reward projection must keep Kafka contract events as the only canonical production path; do not leave parallel local reward side channels behind.

---

## Scope Check

The approved spec touches registration, auth freshness, admin write paths, profile projection, and reward/event cleanup. Those areas are coupled by the same owner-contract hardening, so keeping them in one plan avoids partial contract drift. The tasks below are still sized so each one can be reviewed, tested, and committed independently.

## File Structure Map

Create:

- `backend/community-app/src/main/java/com/nowcoder/community/user/config/UserRegistrationProofProperties.java`
  Owner configuration for prepared-proof TTL and HMAC secret.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/RegistrationPreparedProofPort.java`
  Application-owned technical port for issuing and verifying owner-prepared registration proofs.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapter.java`
  Infrastructure implementation of prepared-proof signing and verification.
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicy.java`
  Owner-domain normalization and validation for username and email.
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserStatusDomainService.java`
  Owner-domain validation for admin-triggered account status changes.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserStatusCommand.java`
  Application command for admin-triggered account status changes.
- `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateUserStatusRequest.java`
  HTTP DTO for admin status changes.
- `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicyTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserStatusDomainServiceTest.java`
- `deploy/mysql/community/021_user_security_version_backfill.sql`
  One-time migration to backfill zero `security_version` rows and advance the counter.

Delete:

- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRewardActionApi.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapter.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserEventPublisher.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java`
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapterTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuerTest.java`
- `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandlerTest.java`

Modify:

- Registration contracts and drafts:
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/PreparedRegistrationUserResult.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/PreparedRegistrationUserView.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/VerifiedRegistrationUserCommand.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/CreateVerifiedRegistrationUserCommand.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapter.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java`
- Security version and token freshness:
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserCredentialView.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserCredentialResult.java`
- Admin role/status and moderation:
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserAuditLogPort.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/audit/Slf4jUserAuditLogAdapter.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java`
- Profile query narrowing and avatar regression:
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfileResult.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserProfileView.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserReadQueryApiAdapter.java`
- Published API narrowing and event cleanup:
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialApiAdapter.java`
  - `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java`
- Config and docs:
  - `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`
  - `backend/community-app/src/main/resources/application.yml`
  - `backend/community-app/src/test/resources/application.yml`
  - `docs/handbook/business-logic/user.md`
  - `docs/handbook/business-logic/auth.md`
  - `docs/handbook/security.md`
  - `docs/handbook/auth-login-session-flow.md`
  - `docs/handbook/business-flows.md`

## Implementation Rules

- Do not let controllers, listeners, filters, or jobs call foreign `api.*` directly. Keep cross-domain orchestration inside same-domain `*ApplicationService`.
- Do not add new `ApplicationService -> mapper` dependencies. All persistence stays behind `UserRepository`, `RefreshTokenSessionRepository`, and auth repositories.
- Keep the new prepared-proof HMAC logic in infrastructure behind an application-owned port; do not move secrets or HMAC logic into the domain layer.
- Keep username/email/password rules in owner domain services; HTTP DTO annotations remain a front gate only.
- When shrinking published APIs, remove the method from both the interface and its adapter/test surface in the same task so there is no half-removed contract.
- Run focused tests after every task and the architecture suite at the end.

### Task 1: Close Registration Prepare/Create With Owner Proof And Identity Rules

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/config/UserRegistrationProofProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/RegistrationPreparedProofPort.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicy.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicyTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapterTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/PreparedRegistrationUserResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/PreparedRegistrationUserView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/VerifiedRegistrationUserCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/CreateVerifiedRegistrationUserCommand.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Modify: `backend/community-app/src/test/resources/application.yml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationDraftRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/config/AuthStartupValidatorTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRegistrationApiAdapterTest.java`

**Interfaces:**
- Consumes: `PreparedRegistrationUserView UserRegistrationActionApi.prepareRegistrationUser(String username, String password, String email)`
- Consumes: `boolean RegistrationDraftRepository.store(String registrationToken, PreparedRegistrationDraft draft, Duration ttl)`
- Produces: `String RegistrationPreparedProofPort.issue(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt)`
- Produces: `void RegistrationPreparedProofPort.verify(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt, String preparedProof)`
- Produces: `PreparedRegistrationUserView(UUID userId, String username, String email, String encodedPassword, String headerUrl, String preparedProof, Instant proofExpiresAt)`
- Produces: `PreparedRegistrationDraft(UUID userId, String username, String email, String encodedPassword, String headerUrl, String preparedProof, Instant proofExpiresAt, Instant issuedAt, Instant expiresAt)`
- Produces: `CreateVerifiedRegistrationUserCommand(UUID userId, String username, String email, String encodedPassword, String headerUrl, String preparedProof, Instant proofExpiresAt)`

- [ ] **Step 1: Write the failing tests**

Add these tests first.

`backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`

```java
@Test
void prepareRegistrationUserShouldReturnPreparedProofAndExpiry() {
    UserRegistrationApplicationService service = serviceWithProof();

    PreparedRegistrationUserResult prepared = service.prepareRegistrationUser("  alice  ", "Secret12", "  alice@example.com  ");

    assertThat(prepared.username()).isEqualTo("alice");
    assertThat(prepared.email()).isEqualTo("alice@example.com");
    assertThat(prepared.preparedProof()).isNotBlank();
    assertThat(prepared.proofExpiresAt()).isAfter(NOW);
}

@Test
void createVerifiedRegistrationUserShouldRejectTamperedPreparedMaterial() {
    UserRegistrationApplicationService service = serviceWithProof();
    PreparedRegistrationUserResult prepared = service.prepareRegistrationUser("alice", "Secret12", "alice@example.com");

    assertThatThrownBy(() -> service.createVerifiedRegistrationUser(new CreateVerifiedRegistrationUserCommand(
            prepared.userId(),
            prepared.username(),
            "mallory@example.com",
            prepared.encodedPassword(),
            prepared.headerUrl(),
            prepared.preparedProof(),
            prepared.proofExpiresAt()
    ))).isInstanceOf(BusinessException.class);
}
```

`backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java`

```java
assertThat(draft.preparedProof()).isNotBlank();
assertThat(draft.proofExpiresAt()).isAfter(draft.issuedAt());
```

`backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`

```java
assertThat(createCommand.preparedProof()).isEqualTo("proof-123");
assertThat(createCommand.proofExpiresAt()).isEqualTo(Instant.parse("2026-05-03T01:30:00Z"));
```

`backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicyTest.java`

```java
@Test
void normalizeEmailShouldRejectMalformedAddress() {
    AccountIdentityPolicy policy = new AccountIdentityPolicy();

    assertThatThrownBy(() -> policy.normalizeEmail("alice-at-example.com"))
            .isInstanceOf(BusinessException.class);
}
```

- [ ] **Step 2: Run the focused test set and confirm it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,RedisRegistrationDraftRepositoryTest,AccountIdentityPolicyTest,AuthStartupValidatorTest,UserRegistrationApiAdapterTest' -DfailIfNoTests=false
```

Expected: FAIL because `preparedProof()` / `proofExpiresAt()` do not exist yet, drafts do not persist proof fields, and create still accepts caller-mutated prepared material.

- [ ] **Step 3: Implement owner proof, account identity policy, and draft contract changes**

Add the new owner technical port and proof adapter.

`backend/community-app/src/main/java/com/nowcoder/community/user/application/port/RegistrationPreparedProofPort.java`

```java
package com.nowcoder.community.user.application.port;

import java.time.Instant;
import java.util.UUID;

public interface RegistrationPreparedProofPort {

    String issue(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt);

    void verify(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt, String preparedProof);
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicy.java`

```java
package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.constants.ValidationLimits;
import com.nowcoder.community.common.exception.BusinessException;

import java.util.regex.Pattern;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class AccountIdentityPolicy {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    public String normalizeUsername(String username) {
        String value = username == null ? "" : username.trim();
        if (value.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "username 不能为空");
        }
        if (value.length() > ValidationLimits.USERNAME_MAX) {
            throw new BusinessException(INVALID_ARGUMENT, "username 过长");
        }
        return value;
    }

    public String normalizeEmail(String email) {
        String value = email == null ? "" : email.trim();
        if (value.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "email 不能为空");
        }
        if (value.length() > ValidationLimits.EMAIL_MAX || !EMAIL_PATTERN.matcher(value).matches()) {
            throw new BusinessException(INVALID_ARGUMENT, "email 格式非法");
        }
        return value;
    }
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapter.java`

```java
package com.nowcoder.community.user.infrastructure.security;

import com.nowcoder.community.common.exception.BusinessException;
import com.nowcoder.community.user.application.port.RegistrationPreparedProofPort;
import com.nowcoder.community.user.config.UserRegistrationProofProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

@Component
public class HmacRegistrationPreparedProofAdapter implements RegistrationPreparedProofPort {

    private static final String HMAC_SHA256 = "HmacSHA256";
    private final UserRegistrationProofProperties properties;
    private final Clock clock;

    public HmacRegistrationPreparedProofAdapter(UserRegistrationProofProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    public String issue(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload(userId, username, email, encodedPassword, headerUrl, proofExpiresAt)));
    }

    @Override
    public void verify(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt, String preparedProof) {
        if (proofExpiresAt == null || !Instant.now(clock).isBefore(proofExpiresAt)) {
            throw new BusinessException(INVALID_ARGUMENT, "prepared proof 已过期");
        }
        String expected = issue(userId, username, email, encodedPassword, headerUrl, proofExpiresAt);
        byte[] expectedBytes = expected.getBytes(StandardCharsets.US_ASCII);
        byte[] actualBytes = (preparedProof == null ? "" : preparedProof.trim()).getBytes(StandardCharsets.US_ASCII);
        if (!MessageDigest.isEqual(expectedBytes, actualBytes)) {
            throw new BusinessException(INVALID_ARGUMENT, "prepared proof 非法");
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA256);
            mac.init(new SecretKeySpec(properties.getHmacSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA256));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (Exception ex) {
            throw new IllegalStateException("failed to sign registration prepared proof", ex);
        }
    }

    private String payload(UUID userId, String username, String email, String encodedPassword, String headerUrl, Instant proofExpiresAt) {
        return userId + "|" + username + "|" + email + "|" + encodedPassword + "|" + headerUrl + "|" + proofExpiresAt;
    }
}
```

Thread the new fields through result, view, command, and draft records.

```java
public record PreparedRegistrationUserResult(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl,
        String preparedProof,
        Instant proofExpiresAt
) {}
```

```java
public record PreparedRegistrationDraft(
        UUID userId,
        String username,
        String email,
        String encodedPassword,
        String headerUrl,
        String preparedProof,
        Instant proofExpiresAt,
        Instant issuedAt,
        Instant expiresAt
) {}
```

Update the application flow so `prepareRegistrationUser(...)` canonicalizes through `AccountIdentityPolicy`, issues proof, and `createVerifiedRegistrationUser(...)` verifies proof before insert.

```java
RegistrationInput input = userRegistrationDomainService.requireValidRegistration(username, password, email);
Instant proofExpiresAt = Instant.now(clock).plusSeconds(proofProperties.getTtlSeconds());
String preparedProof = preparedProofPort.issue(prepared.id(), prepared.username(), prepared.email(), prepared.encodedPassword(), prepared.headerUrl(), proofExpiresAt);
return new PreparedRegistrationUserResult(prepared.id(), prepared.username(), prepared.email(), prepared.encodedPassword(), prepared.headerUrl(), preparedProof, proofExpiresAt);
```

```java
preparedProofPort.verify(
        command.userId(),
        command.username(),
        command.email(),
        encodedPassword,
        command.headerUrl(),
        command.proofExpiresAt(),
        command.preparedProof()
);
```

Add proof configuration and startup validation.

`backend/community-app/src/main/java/com/nowcoder/community/user/config/UserRegistrationProofProperties.java`

```java
package com.nowcoder.community.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "user.registration.proof")
public class UserRegistrationProofProperties {

    private int ttlSeconds = 1800;
    private String hmacSecret = "";

    public int getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(int ttlSeconds) {
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : 1800;
    }

    public String getHmacSecret() {
        return hmacSecret;
    }

    public void setHmacSecret(String hmacSecret) {
        this.hmacSecret = hmacSecret == null ? "" : hmacSecret.trim();
    }
}
```

`backend/community-app/src/main/resources/application.yml`

```yaml
user:
  registration:
    proof:
      ttl-seconds: ${USER_REGISTRATION_PROOF_TTL_SECONDS:1800}
      hmac-secret: ${USER_REGISTRATION_PROOF_HMAC_SECRET:}
```

`backend/community-app/src/test/resources/application.yml`

```yaml
user:
  registration:
    proof:
      ttl-seconds: 1800
      hmac-secret: test-user-registration-proof-secret-at-least-32bytes
```

`backend/community-app/src/main/java/com/nowcoder/community/auth/config/AuthStartupValidator.java`

```java
requireNonBlank(environment, errors, "user.registration.proof.hmac-secret",
        "设置环境变量 USER_REGISTRATION_PROOF_HMAC_SECRET（至少 32 字节）");
```

- [ ] **Step 4: Run the focused test set again**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,RedisRegistrationDraftRepositoryTest,AccountIdentityPolicyTest,HmacRegistrationPreparedProofAdapterTest,AuthStartupValidatorTest,UserRegistrationApiAdapterTest' -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add docs/superpowers/plans/2026-06-28-community-user-business-line-hardening.md \
backend/community-app/src/main/java/com/nowcoder/community/user/config/UserRegistrationProofProperties.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/port/RegistrationPreparedProofPort.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/security/HmacRegistrationPreparedProofAdapter.java \
backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/AccountIdentityPolicy.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java \
backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/auth/domain/model/PreparedRegistrationDraft.java \
backend/community-app/src/main/resources/application.yml \
backend/community-app/src/test/resources/application.yml \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java
git commit -m "feat: close user registration prepared proof flow"
```

### Task 2: Make Active User Security Versions Positive And Align High-Risk Freshness

**Files:**
- Create: `deploy/mysql/community/021_user_security_version_backfill.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java`

**Interfaces:**
- Consumes: `long UserRepository.nextUserSecurityVersion(UUID userId)`
- Produces: `UserAccount verifiedUser(UUID userId, String username, String encodedPassword, String email, String headerUrl, long securityVersion)`
- Produces: `TokenFreshnessResult verify(UUID userId, long tokenSecurityVersion)`

- [ ] **Step 1: Write the failing tests**

Update `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java`:

```java
when(userRepository.nextUserSecurityVersion(userId)).thenReturn(123L);

assertThat(inserted.securityVersion()).isEqualTo(123L);
assertThat(result.securityVersion()).isEqualTo(123L);
```

Update `backend/community-app/src/test/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationServiceTest.java`:

```java
@Test
void verifyShouldRejectZeroVersionToken() {
    TokenFreshnessResult result = service.verify(uuid(7), 0L);
    assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.STALE);
}

@Test
void verifyShouldRejectOwnerCredentialWithZeroSecurityVersion() {
    UUID userId = uuid(7);
    when(userCredentialQueryApi.getByUserId(userId))
            .thenReturn(new UserCredentialView(userId, "admin", 1, 1, "h1", 0L, true, true));

    TokenFreshnessResult result = service.verify(userId, 123L);

    assertThat(result.status()).isEqualTo(TokenFreshnessResult.Status.STALE);
}
```

Add an integration check in `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java`:

```java
assertThat(userRepository.currentUserSecurityVersion()).isGreaterThan(0L);
```

- [ ] **Step 2: Run the focused test set and confirm it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,TokenFreshnessApplicationServiceTest,MyBatisUserRepositoryTest' -DfailIfNoTests=false
```

Expected: FAIL because verified-user creation still returns `securityVersion=0`, and token freshness still treats only the JWT claim as stale.

- [ ] **Step 3: Allocate positive security versions on active create, tighten freshness, and add the DB backfill**

Change the verified-user factory and create path to allocate a positive version before insert.

`backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java`

```java
public UserAccount verifiedUser(
        UUID userId,
        String username,
        String encodedPassword,
        String email,
        String headerUrl,
        long securityVersion
) {
    return new UserAccount(
            userId,
            safeTrim(username),
            safeTrim(encodedPassword),
            "",
            safeTrim(email),
            0,
            1,
            safeTrim(headerUrl),
            Date.from(Instant.now(clock)),
            null,
            null,
            0L,
            securityVersion
    );
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java`

```java
long securityVersion = userRepository.nextUserSecurityVersion(command.userId());
UserAccount user = userRegistrationDomainService.verifiedUser(
        command.userId(),
        command.username(),
        encodedPassword,
        command.email(),
        command.headerUrl(),
        securityVersion
);
```

Tighten high-risk freshness so both the token claim and the owner current fact must be positive and equal.

`backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java`

```java
public TokenFreshnessResult verify(UUID userId, long tokenSecurityVersion) {
    if (userId == null || tokenSecurityVersion <= 0) {
        return TokenFreshnessResult.stale();
    }
    UserCredentialView credential = userCredentialQueryApi.getByUserId(userId);
    if (credential == null || !credential.loginAllowed()) {
        return TokenFreshnessResult.denied();
    }
    if (credential.securityVersion() <= 0 || credential.securityVersion() != tokenSecurityVersion) {
        return TokenFreshnessResult.stale();
    }
    return TokenFreshnessResult.accepted();
}
```

Create the production backfill migration.

`deploy/mysql/community/021_user_security_version_backfill.sql`

```sql
set @user_security_seed_version := cast(floor(unix_timestamp(current_timestamp(3)) * 1000) * 4096 as unsigned);

update user
set security_version = @user_security_seed_version
where security_version is null or security_version = 0;

create table if not exists user_security_version_counter (
  id int primary key,
  current_version bigint not null default 0
);

insert into user_security_version_counter(id, current_version)
values (1, greatest(@user_security_seed_version, (select coalesce(max(security_version), 0) from user)))
on duplicate key update current_version = greatest(current_version, values(current_version), (select coalesce(max(security_version), 0) from user));
```

- [ ] **Step 4: Run the focused test set again**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,TokenFreshnessApplicationServiceTest,MyBatisUserRepositoryTest' -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add deploy/mysql/community/021_user_security_version_backfill.sql \
backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/UserAccount.java \
backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserRegistrationDomainService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/UserRegistrationApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationService.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserRegistrationApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/auth/application/TokenFreshnessApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisUserRepositoryTest.java
git commit -m "feat: align user security version lifecycle"
```

### Task 3: Harden Role, Status, Moderation, And Refresh-Session Boundaries

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserStatusDomainService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserStatusCommand.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateUserStatusRequest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserStatusDomainServiceTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserAuditLogPort.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/audit/Slf4jUserAuditLogAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/app/config/DomainServiceConfig.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserModerationDomainServiceTest.java`

**Interfaces:**
- Produces: `void updateStatus(UpdateUserStatusCommand command)`
- Produces: `record SecurityImpactDecision(boolean loginAffected, boolean bumpSecurityVersion, boolean revokeRefreshSessions) {}`
- Consumes: `void RefreshTokenSessionRepository.revokeByUserId(UUID userId)`
- Consumes: `void UserRepository.updateStatus(UUID userId, int status, long securityVersion)`

- [ ] **Step 1: Write the failing tests**

Add a status write-path test in `backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java`:

```java
@Test
void updateStatusShouldIncrementSecurityVersionRevokeSessionsAndWriteAuditLog() {
    AdminUserApplicationService service = service();
    UpdateUserStatusCommand command = new UpdateUserStatusCommand(ACTOR_ID, TARGET_ID, 0, "disable account", true);
    when(userRepository.findById(TARGET_ID)).thenReturn(Optional.of(user(TARGET_ID, "admin", "admin@example.com", 1, 1, "h8", new Date())));
    when(userRepository.nextUserSecurityVersion(TARGET_ID)).thenReturn(222L);

    service.updateStatus(command);

    InOrder inOrder = inOrder(userRepository, refreshTokenSessionRepository, userAuditLogPort);
    inOrder.verify(userRepository).updateStatus(TARGET_ID, 0, 222L);
    inOrder.verify(refreshTokenSessionRepository).revokeByUserId(TARGET_ID);
    inOrder.verify(userAuditLogPort).recordStatusUpdated(ACTOR_ID, TARGET_ID, 1, 0, "disable account");
}
```

Extend moderation tests in `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java`:

```java
@Test
void applyModerationShouldBumpVersionAndRevokeOnActiveBanLift() {
    when(userRepository.findById(USER_ID_7)).thenReturn(Optional.of(account(USER_ID_7, null, Instant.now().plusSeconds(120))));
    when(userRepository.nextUserPolicyVersion(USER_ID_7)).thenReturn(101L);
    when(userRepository.nextUserSecurityVersion(USER_ID_7)).thenReturn(202L);

    service.applyModeration(new ApplyUserModerationCommand(USER_ID_7, "unban", 0));

    verify(refreshTokenSessionRepository).revokeByUserId(USER_ID_7);
    verify(userRepository).updateModerationUntil(eq(USER_ID_7), eq(null), eq(null), eq(101L), eq(202L));
}
```

Add the new controller delegation test in `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java`:

```java
verify(adminUserApplicationService).updateStatus(new UpdateUserStatusCommand(
        actorUserId,
        targetUserId,
        0,
        "disable account",
        true
));
```

- [ ] **Step 2: Run the focused test set and confirm it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AdminUserApplicationServiceTest,AdminUserControllerUnitTest,UserModerationApplicationServiceTest,UserModerationDomainServiceTest,UserStatusDomainServiceTest' -DfailIfNoTests=false
```

Expected: FAIL because there is no status command/DTO/domain service/controller endpoint yet, and moderation still only bumps on one ban-transition shape.

- [ ] **Step 3: Implement admin status writes and explicit moderation security-impact policy**

Add the new status command and DTO.

`backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserStatusCommand.java`

```java
package com.nowcoder.community.user.application.command;

import java.util.UUID;

public record UpdateUserStatusCommand(
        UUID actorUserId,
        UUID targetUserId,
        int status,
        String reason,
        boolean confirm
) {
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateUserStatusRequest.java`

```java
package com.nowcoder.community.user.controller.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public class UpdateUserStatusRequest {

    private UUID targetUserId;

    @Min(value = 0, message = "status 非法")
    @Max(value = 1, message = "status 非法")
    private int status;

    @NotBlank(message = "reason 不能为空")
    @Size(max = 200, message = "reason 过长（max=200）")
    private String reason;

    private boolean confirm;

    // getters and setters
}
```

Add the owner-domain status validation and application write path.

`backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserStatusDomainService.java`

```java
package com.nowcoder.community.user.domain.service;

import com.nowcoder.community.common.exception.BusinessException;

import java.util.UUID;

import static com.nowcoder.community.common.exception.CommonErrorCode.INVALID_ARGUMENT;

public class UserStatusDomainService {

    public String requireValidCommand(boolean commandPresent, UUID targetUserId, int status, String reason, boolean confirm) {
        if (!commandPresent) {
            throw new BusinessException(INVALID_ARGUMENT, "request 不能为空");
        }
        if (targetUserId == null) {
            throw new BusinessException(INVALID_ARGUMENT, "targetUserId 非法");
        }
        if (status != 0 && status != 1) {
            throw new BusinessException(INVALID_ARGUMENT, "用户状态非法");
        }
        if (!confirm) {
            throw new BusinessException(INVALID_ARGUMENT, "需要二次确认（confirm=true）");
        }
        String normalizedReason = reason == null ? "" : reason.trim();
        if (normalizedReason.isEmpty()) {
            throw new BusinessException(INVALID_ARGUMENT, "reason 不能为空");
        }
        return normalizedReason;
    }
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java`

```java
@Transactional
public void updateStatus(UpdateUserStatusCommand command) {
    if (command == null) {
        userStatusDomainService.requireValidCommand(false, null, 0, null, false);
        return;
    }
    String reason = userStatusDomainService.requireValidCommand(true, command.targetUserId(), command.status(), command.reason(), command.confirm());
    UserAccount target = userRepository.findById(command.targetUserId()).orElse(null);
    if (target == null || target.id() == null) {
        throw new BusinessException(INVALID_ARGUMENT, "目标用户不存在");
    }
    if (target.status() == command.status()) {
        return;
    }
    long securityVersion = userRepository.nextUserSecurityVersion(command.targetUserId());
    userRepository.updateStatus(command.targetUserId(), command.status(), securityVersion);
    refreshTokenSessionRepository.revokeByUserId(command.targetUserId());
    userAuditLogPort.recordStatusUpdated(command.actorUserId(), command.targetUserId(), target.status(), command.status(), reason);
}
```

Add explicit moderation security-impact evaluation.

`backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java`

```java
public record SecurityImpactDecision(boolean loginAffected, boolean bumpSecurityVersion, boolean revokeRefreshSessions) {
}

public SecurityImpactDecision evaluateSecurityImpact(Instant previousBanUntil, Instant nextBanUntil, Instant now) {
    Instant basis = now == null ? Instant.now() : now;
    boolean wasActive = previousBanUntil != null && previousBanUntil.isAfter(basis);
    boolean isActive = nextBanUntil != null && nextBanUntil.isAfter(basis);
    boolean changed = previousBanUntil == null ? nextBanUntil != null : !previousBanUntil.equals(nextBanUntil);
    boolean loginAffected = wasActive || isActive;
    boolean bump = changed && loginAffected;
    return new SecurityImpactDecision(loginAffected, bump, bump);
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java`

```java
UserModerationDomainService.SecurityImpactDecision impact =
        userModerationDomainService.evaluateSecurityImpact(previousBanUntil, next.banUntil(), now);
long securityVersion = impact.bumpSecurityVersion() ? userRepository.nextUserSecurityVersion(userId) : 0L;
userRepository.updateModerationUntil(userId, versionedNext.muteUntil(), versionedNext.banUntil(), version, securityVersion);
if (impact.revokeRefreshSessions()) {
    refreshTokenSessionRepository.revokeByUserId(userId);
}
```

Wire the new admin endpoint and audit port.

```java
@PostMapping("/status")
public Result<Void> updateStatus(Authentication authentication, @Valid @RequestBody UpdateUserStatusRequest request) {
    UUID actorUserId = CurrentUser.requireUserUuid(authentication);
    adminUserApplicationService.updateStatus(new UpdateUserStatusCommand(
            actorUserId,
            request.getTargetUserId(),
            request.getStatus(),
            request.getReason(),
            request.isConfirm()
    ));
    return Result.ok();
}
```

```java
public interface UserAuditLogPort {
    void recordRoleUpdated(UUID actorUserId, UUID targetUserId, int fromType, int toType, String reason);
    void recordStatusUpdated(UUID actorUserId, UUID targetUserId, int fromStatus, int toStatus, String reason);
}
```

- [ ] **Step 4: Run the focused test set again**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='AdminUserApplicationServiceTest,AdminUserControllerUnitTest,UserModerationApplicationServiceTest,UserModerationDomainServiceTest,UserStatusDomainServiceTest' -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserStatusDomainService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/command/UpdateUserStatusCommand.java \
backend/community-app/src/main/java/com/nowcoder/community/user/controller/dto/UpdateUserStatusRequest.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/AdminUserApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/controller/AdminUserController.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserAuditLogPort.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/audit/Slf4jUserAuditLogAdapter.java \
backend/community-app/src/main/java/com/nowcoder/community/user/domain/service/UserModerationDomainService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/UserModerationApplicationService.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/AdminUserApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserModerationApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserModerationDomainServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/domain/service/UserStatusDomainServiceTest.java
git commit -m "feat: harden admin user policy write paths"
```

### Task 4: Narrow User Profile Contracts And Strengthen Avatar Confirm Regression Tests

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfileResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserProfileView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserReadQueryApiAdapter.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserProfileApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`

**Interfaces:**
- Produces: `UserProfileResult(UUID userId, String username, String headerUrl, int type, int status, Date createTime)`
- Produces: `UserProfileView(UUID userId, String username, String headerUrl, int type, int status, Date createTime)`

- [ ] **Step 1: Write the failing tests**

Update `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java`:

```java
UserProfileResult profile = service.getProfile(userId);

assertThat(profile).extracting(
        UserProfileResult::userId,
        UserProfileResult::username,
        UserProfileResult::headerUrl,
        UserProfileResult::type,
        UserProfileResult::status,
        UserProfileResult::createTime
).containsExactly(userId, "alice", "h7", 2, 1, createTime);
verifyNoInteractions(walletAccountQueryApi);
```

Add avatar negative-path tests in `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java`:

```java
@Test
void resolvePublicAvatarUrlShouldRejectInactiveAvatarObject() {
    when(ossClient.getMetadata(objectId)).thenReturn(new OssMetadataResponse(
            objectId, versionId, "USER_AVATAR", "community-app", "user", "avatar",
            userId.toString(), "PUBLIC", "DELETED", "avatar.png", "image/png", 6, "sha256-avatar", publicUrl
    ));

    assertThatThrownBy(() -> adapter.resolvePublicAvatarUrl(userId, objectId))
            .isInstanceOf(BusinessException.class);
}
```

Add a no-write regression in `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java`:

```java
when(avatarStoragePort.resolvePublicAvatarUrl(userId, objectId)).thenThrow(new BusinessException(FORBIDDEN, "invalid avatar"));

assertThatThrownBy(() -> service.updateAvatar(userId, userId, objectId)).isInstanceOf(BusinessException.class);
verify(userRepository, never()).updateHeaderUrl(userId, "ignored");
```

- [ ] **Step 2: Run the focused test set and confirm it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserReadApplicationServiceTest,UserProfileApplicationServiceTest,UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest' -DfailIfNoTests=false
```

Expected: FAIL because `UserReadApplicationService` still depends on wallet and the result/view records still expose wallet fields.

- [ ] **Step 3: Remove wallet facts from user profile contracts and keep avatar logic locked down**

Shrink the user profile result and published view.

`backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfileResult.java`

```java
public record UserProfileResult(
        UUID userId,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime
) {
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserProfileView.java`

```java
public record UserProfileView(
        UUID userId,
        String username,
        String headerUrl,
        int type,
        int status,
        Date createTime
) {
}
```

Remove the wallet query dependency from the user owner read path.

`backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java`

```java
public class UserReadApplicationService {

    private final UserRepository userRepository;
    private final UserReadDomainService userReadDomainService;

    public UserReadApplicationService(
            UserRepository userRepository,
            UserReadDomainService userReadDomainService
    ) {
        this.userRepository = userRepository;
        this.userReadDomainService = userReadDomainService;
    }

    public UserProfileResult getProfile(UUID userId) {
        userReadDomainService.assertValidUserId(userId);
        UserProfile profile = userRepository.findProfileById(userId)
                .orElseThrow(() -> new BusinessException(USER_NOT_FOUND));
        return new UserProfileResult(
                profile.id(),
                profile.username(),
                profile.headerUrl(),
                profile.type(),
                profile.status(),
                profile.createTime()
        );
    }
}
```

Update the published query adapter mapping.

```java
return user == null ? null : new UserProfileView(
        user.userId(),
        user.username(),
        user.headerUrl(),
        user.type(),
        user.status(),
        user.createTime()
);
```

Keep the avatar owner projection behavior unchanged; only add the negative-path regression tests from Step 1.

- [ ] **Step 4: Run the focused test set again**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserReadApplicationServiceTest,UserProfileApplicationServiceTest,UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest' -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/application/UserReadApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/result/UserProfileResult.java \
backend/community-app/src/main/java/com/nowcoder/community/user/api/model/UserProfileView.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserReadQueryApiAdapter.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserReadApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserProfileApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserAvatarApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/oss/OssAvatarStorageAdapterTest.java
git commit -m "feat: narrow user profile owner contracts"
```

### Task 5: Remove Half-Finished Published APIs And Collapse Reward/Event Surfaces To The Canonical Path

**Files:**
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRewardActionApi.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapter.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserEventPublisher.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapterTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuerTest.java`
- Delete: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandlerTest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialApiAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapterTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java`

**Interfaces:**
- Produces: `UserCredentialActionApi` with only `validatePasswordPolicy(String newPassword)` and `resetPasswordAndRevokeRefreshSessions(UUID userId, String newPassword)`
- Produces: `UserRefreshTokenSessionActionApi` with only `store`, `consume`, `revoke`, `revokeFamily`, and `deleteExpiredBefore`

- [ ] **Step 1: Write the failing tests**

Use reflection-based contract assertions so the tests fail before deletions land.

`backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapterTest.java`

```java
@Test
void publishedRefreshTokenSessionActionApiShouldNotExposeRevokeByUserId() {
    assertThatThrownBy(() -> UserRefreshTokenSessionActionApi.class.getMethod("revokeByUserId", UUID.class))
            .isInstanceOf(NoSuchMethodException.class);
}
```

`backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java`

```java
@Test
void publishedCredentialActionApiShouldNotExposeRawPasswordUpdate() {
    assertThatThrownBy(() -> UserCredentialActionApi.class.getMethod("updatePassword", UUID.class, String.class))
            .isInstanceOf(NoSuchMethodException.class);
}
```

`backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java`

```java
@Test
void legacyRewardAndUserEventSurfacesShouldBeAbsent() {
    assertThat(ClassUtils.isPresent("com.nowcoder.community.user.api.action.UserRewardActionApi", getClass().getClassLoader())).isFalse();
    assertThat(ClassUtils.isPresent("com.nowcoder.community.user.application.port.UserEventPublisher", getClass().getClassLoader())).isFalse();
}
```

- [ ] **Step 2: Run the focused test set and confirm it fails**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserCredentialApplicationServiceTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest,UserRewardKafkaListenerTest,CommentApplicationServiceTest' -DfailIfNoTests=false
```

Expected: FAIL because the published APIs still expose the raw methods and the legacy reward/event classes still exist.

- [ ] **Step 3: Shrink the surviving APIs and delete the dead surfaces**

Keep only complete credential and refresh-session actions.

`backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java`

```java
public interface UserCredentialActionApi {

    void validatePasswordPolicy(String newPassword);

    void resetPasswordAndRevokeRefreshSessions(UUID userId, String newPassword);
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`

```java
public interface UserRefreshTokenSessionActionApi {

    void store(String tokenHash, UUID userId, String familyId, Instant expiresAt);

    RefreshTokenSessionView consume(String tokenHash);

    void revoke(String tokenHash);

    int revokeFamily(String familyId);

    int deleteExpiredBefore(Instant cutoff);
}
```

Remove the raw application methods that only existed to back those half-actions.

`backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java`

```java
public void validatePasswordPolicy(String newPassword) {
    passwordPolicyDomainService.requireValidPassword(newPassword);
}

@Transactional
public void resetPasswordAndRevokeRefreshSessions(UUID userId, String newPassword) {
    updatePasswordOnly(userId, newPassword);
    refreshTokenSessionRepository.revokeByUserId(userId);
}
```

`backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java`

```java
public int deleteExpiredBefore(Instant cutoff) {
    return repository.deleteExpiredBefore(cutoff);
}
```

Delete the dead classes in one change set.

```bash
git rm \
backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRewardActionApi.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapter.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/port/UserEventPublisher.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/LocalUserEventPublisher.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuer.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandler.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/UserRewardApiAdapterTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxEnqueuerTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/CommentRewardOutboxHandlerTest.java
```

Leave `UserRewardKafkaListener` and `UserRewardApplicationService` as the canonical reward projection path.

- [ ] **Step 4: Run the focused test set again**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserCredentialApplicationServiceTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest,UserRewardKafkaListenerTest,CommentApplicationServiceTest' -DfailIfNoTests=false
```

Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserCredentialActionApi.java \
backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/UserCredentialApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/UserCredentialApiAdapter.java \
backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/UserCredentialApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationServiceTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapterTest.java \
backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/event/UserRewardKafkaListenerTest.java \
backend/community-app/src/test/java/com/nowcoder/community/content/application/CommentApplicationServiceTest.java
git commit -m "refactor: narrow user published action surfaces"
```

### Task 6: Update Handbook Docs And Run The Full Regression Gate

**Files:**
- Modify: `docs/handbook/business-logic/user.md`
- Modify: `docs/handbook/business-logic/auth.md`
- Modify: `docs/handbook/security.md`
- Modify: `docs/handbook/auth-login-session-flow.md`
- Modify: `docs/handbook/business-flows.md`

**Interfaces:**
- Consumes: the final implementation from Tasks 1-5
- Produces: handbook text that matches the code paths, contracts, and verification behavior shipped by Tasks 1-5

- [ ] **Step 1: Write the failing doc-alignment checks**

Before editing docs, capture the exact outdated claims to replace.

`docs/handbook/business-logic/user.md`

```md
- `prepareRegistrationUser(...)`: returns canonical username/email, encoded password, default avatar projection, prepared proof, and proof expiry; it does not write a user row.
- `createVerifiedRegistrationUser(...)`: verifies the prepared proof, inserts one active user row with a positive `securityVersion`, then publishes user policy changed.
- `UserProfileQueryApi` exposes only `userId`, `username`, `headerUrl`, `type`, `status`, and `createTime`.
```

`docs/handbook/security.md`

```md
- `securityVersion` changes on active user create, role change, password change, account status change, and active ban activate/adjust/deactivate.
- Role change, password change, account status change, and active ban activate/adjust/deactivate revoke refresh sessions.
```

- [ ] **Step 2: Run the final regression commands once before the doc edits**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,RedisRegistrationDraftRepositoryTest,AuthStartupValidatorTest,TokenFreshnessApplicationServiceTest,AdminUserApplicationServiceTest,AdminUserControllerUnitTest,UserModerationApplicationServiceTest,UserModerationDomainServiceTest,UserReadApplicationServiceTest,UserProfileApplicationServiceTest,UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest,UserCredentialApplicationServiceTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest,UserRewardKafkaListenerTest,MyBatisUserRepositoryTest' -DfailIfNoTests=false
```

Expected: PASS. If this fails, stop and fix code before touching docs.

- [ ] **Step 3: Update the handbook to match the shipped behavior**

Make these concrete doc edits:

`docs/handbook/business-logic/user.md`

```md
- 注册 create 只接受 owner-prepared canonical fields plus `preparedProof` and `proofExpiresAt`; auth draft may cache them but may not rewrite them.
- `muteUntil` only affects speaking; active `banUntil` and account status affect login / refresh and high-risk freshness.
- reward projection uses Kafka content/social contract events as the canonical source; legacy direct reward action APIs are retired.
```

`docs/handbook/business-logic/auth.md`

```md
- registration draft persists `preparedProof` and `proofExpiresAt`, and verify forwards both back to user owner create.
- high-risk token freshness accepts only exact positive `securityVersion` matches from user owner.
```

`docs/handbook/auth-login-session-flow.md`

```md
7. user owner returns a positive `securityVersion` for every active user credential projection.
8. admin role/status changes, password reset, and active account-ban changes revoke refresh sessions at the user owner boundary.
```

`docs/handbook/business-flows.md`

```md
7. User inserts one active `user` row with `status=1` and a positive `security_version`, then publishes `UserPolicyChanged(userExists=true)`.
```

- [ ] **Step 4: Run the final code and architecture gates**

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='UserRegistrationApplicationServiceTest,RegistrationApplicationServiceTest,RegistrationVerificationApplicationServiceTest,RedisRegistrationDraftRepositoryTest,AuthStartupValidatorTest,TokenFreshnessApplicationServiceTest,AdminUserApplicationServiceTest,AdminUserControllerUnitTest,UserModerationApplicationServiceTest,UserModerationDomainServiceTest,UserReadApplicationServiceTest,UserProfileApplicationServiceTest,UserAvatarApplicationServiceTest,OssAvatarStorageAdapterTest,UserCredentialApplicationServiceTest,RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest,UserRewardKafkaListenerTest,MyBatisUserRepositoryTest' -DfailIfNoTests=false
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

Expected: PASS for both commands.

- [ ] **Step 5: Commit**

```bash
git add docs/handbook/business-logic/user.md \
docs/handbook/business-logic/auth.md \
docs/handbook/security.md \
docs/handbook/auth-login-session-flow.md \
docs/handbook/business-flows.md
git commit -m "docs: align user business line hardening handbook"
```
