# Auth And Ops DDD Tactical Layering Migration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Migrate `auth` and `ops` into strict DDD Tactical Layering so HTTP controllers, filters, and jobs no longer call raw service implementations directly.

**Architecture:** Auth controller calls `auth.application.AuthApplicationService`; auth application services orchestrate login, refresh, registration, captcha, password reset, and cleanup use cases. Auth domain services own authentication, token, captcha, registration, password-reset, and rate-limit rules; Redis/DB/in-memory stores, JWT encoding, mail, origin guard filter, and cleanup jobs live in infrastructure. Ops is an adapter domain: `OpsController` calls `ops.application.OpsApplicationService`, which collaborates with owner-domain `search.api.action.SearchReindexActionApi`.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security OAuth2 resource server, Redis, MyBatis/JDBC, JavaMail, ArchUnit, JUnit 5, Mockito, Maven.

---

## Scope

This plan covers these final strict-DDD slices:

- `auth`
- `ops`

It assumes `content`, `user`, `social`, `wallet`, `market`, `growth`, `notice`, `search`, and `analytics` are migrated by their own plans.

---

## Target Package Shape

```text
com.nowcoder.community.auth
  controller
    dto
  application
    command
    result
  domain
    model
    repository
    service
  infrastructure
    job
    jwt
    mail
    persistence
    web
  service              # empty after migration

com.nowcoder.community.ops
  controller
    dto
  application
    command
    result
  infrastructure
    security
```

---

## File Structure Map

### Auth Controller And DTOs

- Move all files under `backend/community-app/src/main/java/com/nowcoder/community/auth/dto` to `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto`.
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`

### Auth Application

- Move: `auth/service/AuthApplicationService.java` -> `auth/application/AuthApplicationService.java`
- Create:
  - `auth/application/LoginApplicationService.java`
  - `auth/application/RefreshTokenApplicationService.java`
  - `auth/application/RegistrationApplicationService.java`
  - `auth/application/RegistrationVerificationApplicationService.java`
  - `auth/application/CaptchaApplicationService.java`
  - `auth/application/PasswordResetApplicationService.java`
  - `auth/application/LoginRateLimitApplicationService.java`
- Create command records:
  - `auth/application/command/LoginCommand.java`
  - `auth/application/command/RefreshCommand.java`
  - `auth/application/command/LogoutCommand.java`
  - `auth/application/command/RegisterCommand.java`
  - `auth/application/command/ResendRegisterCodeCommand.java`
  - `auth/application/command/VerifyRegisterCodeCommand.java`
  - `auth/application/command/IssueCaptchaCommand.java`
  - `auth/application/command/VerifyCaptchaCommand.java`
  - `auth/application/command/RequestPasswordResetCommand.java`
  - `auth/application/command/ConfirmPasswordResetCommand.java`
- Create result records:
  - `auth/application/result/LoginResult.java`
  - `auth/application/result/RefreshResult.java`
  - `auth/application/result/RegisterResult.java`
  - `auth/application/result/RegisterCodeResendResult.java`
  - `auth/application/result/CaptchaIssueResult.java`
  - `auth/application/result/PasswordResetRequestResult.java`

### Auth Domain

- Create models:
  - `auth/domain/model/AuthCredential.java`
  - `auth/domain/model/AuthTokens.java`
  - `auth/domain/model/CaptchaChallenge.java`
  - `auth/domain/model/RefreshTokenRecord.java`
  - `auth/domain/model/RegistrationCode.java`
  - `auth/domain/model/RegistrationSession.java`
  - `auth/domain/model/PasswordResetToken.java`
  - `auth/domain/model/LoginRateLimitKey.java`
- Create repository interfaces:
  - `auth/domain/repository/CaptchaRepository.java`
  - `auth/domain/repository/RefreshTokenRepository.java`
  - `auth/domain/repository/RegistrationCodeRepository.java`
  - `auth/domain/repository/RegistrationSessionRepository.java`
  - `auth/domain/repository/PasswordResetTokenRepository.java`
  - `auth/domain/repository/LoginRateLimitRepository.java`
- Create domain services:
  - `auth/domain/service/AuthDomainService.java`
  - `auth/domain/service/CaptchaDomainService.java`
  - `auth/domain/service/RefreshTokenDomainService.java`
  - `auth/domain/service/RegistrationDomainService.java`
  - `auth/domain/service/PasswordResetDomainService.java`
  - `auth/domain/service/LoginRateLimitDomainService.java`

### Auth Infrastructure

- Move stores to `auth/infrastructure/persistence`:
  - `CaptchaStore.java`, `InMemoryCaptchaStore.java`, `RedisCaptchaStore.java`
  - `RefreshTokenStore.java`, `InMemoryRefreshTokenStore.java`, `RedisRefreshTokenStore.java`, `DbRefreshTokenStore.java`
  - `RegistrationCodeStore.java`, `InMemoryRegistrationCodeStore.java`, `RedisRegistrationCodeStore.java`
  - `RegistrationSessionStore.java`, `InMemoryRegistrationSessionStore.java`, `RedisRegistrationSessionStore.java`
  - `PasswordResetTokenStore.java`, `InMemoryPasswordResetTokenStore.java`, `RedisPasswordResetTokenStore.java`
- Rename store interfaces to repository implementations when they implement a domain repository:
  - `RedisCaptchaRepository`, `InMemoryCaptchaRepository`
  - `RedisRefreshTokenRepository`, `DbRefreshTokenRepository`, `InMemoryRefreshTokenRepository`
  - `RedisRegistrationCodeRepository`, `InMemoryRegistrationCodeRepository`
  - `RedisRegistrationSessionRepository`, `InMemoryRegistrationSessionRepository`
  - `RedisPasswordResetTokenRepository`, `InMemoryPasswordResetTokenRepository`
- Move:
  - `auth/service/JwtTokenService.java` -> `auth/infrastructure/jwt/JwtTokenService.java`
  - `auth/service/MailService.java` -> `auth/application/port/MailPort.java`
  - `auth/service/LogMailService.java` -> `auth/infrastructure/mail/LogMailAdapter.java`
  - `auth/service/SmtpMailService.java` -> `auth/infrastructure/mail/SmtpMailAdapter.java`
  - `auth/web/AuthOriginGuardFilter.java` -> `auth/infrastructure/web/AuthOriginGuardFilter.java`
  - `auth/service/RefreshTokenCleanupJob.java` -> `auth/infrastructure/job/RefreshTokenCleanupJob.java`
  - `auth/service/PendingRegistrationUserCleanupJob.java` -> `auth/infrastructure/job/PendingRegistrationUserCleanupJob.java`
- Delete after replacement:
  - `auth/service/AuthService.java`
  - `auth/service/RegistrationService.java`
  - `auth/service/RegistrationVerificationService.java`
  - `auth/service/CaptchaService.java`
  - `auth/service/PasswordResetService.java`
  - `auth/service/RefreshTokenService.java`
  - `auth/service/LoginRateLimitService.java`
  - all moved store/mail/job classes from `auth.service`
  - `auth/web/AuthOriginGuardFilter.java`

### Ops Application And DTOs

- Move: `ops/dto/SearchReindexResponse.java` -> `ops/controller/dto/SearchReindexResponse.java`
- Create:
  - `ops/application/OpsApplicationService.java`
  - `ops/application/command/ReindexSearchCommand.java`
  - `ops/application/result/SearchReindexResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/ops/controller/OpsController.java`

### Guardrails And Docs

- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/security/PublicReadEndpointSecurityTest.java` if DTO package moves affect imports.
- Modify docs:
  - `docs/ARCHITECTURE.md`
  - `docs/SYSTEM_DESIGN.md`
  - `docs/CORE_LOGIC.md`

---

## Task 1: Add Auth And Ops RED Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DtoBoundaryArchTest.java`

 - [x] **Step 1: Add auth and ops retirement rules**

Add these rules to `DddLayeringArchTest`:

```java
@ArchTest
static final ArchRule auth_service_package_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..auth.service..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule legacy_auth_web_package_must_stay_retired =
        noClasses()
                .should().resideInAnyPackage("..auth.web..")
                .allowEmptyShould(true);

@ArchTest
static final ArchRule auth_controller_and_jobs_must_not_depend_on_legacy_surfaces =
        noClasses()
                .that().resideInAnyPackage("..auth.controller..", "..auth.infrastructure.job..", "..auth.infrastructure.web..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..auth.service..",
                        "..auth.web..",
                        "..auth.domain..",
                        "..auth.infrastructure.persistence..",
                        "..auth.infrastructure.jwt..",
                        "..auth.infrastructure.mail.."
                )
                .allowEmptyShould(true);

@ArchTest
static final ArchRule ops_controller_must_call_ops_application_only =
        noClasses()
                .that().resideInAnyPackage("..ops.controller..")
                .should().dependOnClassesThat().resideInAnyPackage(
                        "..search.api..",
                        "..search.application..",
                        "..ops.infrastructure.."
                )
                .allowEmptyShould(true);
```

 - [x] **Step 2: Remove auth DTO boundary exceptions**

Remove these entries from `DtoBoundaryArchTest.LEGACY_SERVICE_RESPONSE_DTO_CALLERS`:

```java
"com.nowcoder.community.auth.service.AuthApplicationService",
"com.nowcoder.community.auth.service.RegistrationService",
"com.nowcoder.community.auth.service.RegistrationVerificationService"
```

 - [x] **Step 3: Run RED architecture check**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DddLayeringArchTest,DtoBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected: FAIL because auth still contains `auth.service`/`auth.web`, and ops controller calls `search.api.action.SearchReindexActionApi` directly.

---

## Task 2: Establish Auth Domain And Infrastructure Repositories

**Files:**
- Create/move auth domain and infrastructure files listed in the File Structure Map.

 - [x] **Step 1: Write auth domain tests**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/AuthDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/CaptchaDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/RefreshTokenDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/RegistrationDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/PasswordResetDomainServiceTest.java
backend/community-app/src/test/java/com/nowcoder/community/auth/domain/service/LoginRateLimitDomainServiceTest.java
```

Minimum cases:

```java
@Test
void captchaShouldRejectBlankCode() {
    CaptchaDomainService service = new CaptchaDomainService();

    assertThatThrownBy(() -> service.validateCode(""))
            .isInstanceOf(BusinessException.class);
}

@Test
void refreshTokenShouldRejectExpiredToken() {
    RefreshTokenDomainService service = new RefreshTokenDomainService();

    assertThat(service.isExpired(Instant.parse("2026-04-27T00:00:00Z"), Instant.parse("2026-04-28T00:00:00Z")))
            .isTrue();
}

@Test
void rateLimitKeyShouldIncludeUsernameAndIp() {
    LoginRateLimitDomainService service = new LoginRateLimitDomainService();

    assertThat(service.keyOf("Alice", "127.0.0.1").username()).isEqualTo("alice");
}
```

 - [x] **Step 2: Run auth domain tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.auth.domain.service.AuthDomainServiceTest,com.nowcoder.community.auth.domain.service.CaptchaDomainServiceTest,com.nowcoder.community.auth.domain.service.RefreshTokenDomainServiceTest,com.nowcoder.community.auth.domain.service.RegistrationDomainServiceTest,com.nowcoder.community.auth.domain.service.PasswordResetDomainServiceTest,com.nowcoder.community.auth.domain.service.LoginRateLimitDomainServiceTest test
```

Expected: compile failure until auth domain package exists.

 - [x] **Step 3: Move auth stores behind domain repository interfaces**

Move store interfaces to `auth.domain.repository` and rename behaviorally:

```java
public interface CaptchaRepository {
    void save(String owner, String code, Duration ttl);
    CaptchaVerifyResult verifyAndConsume(String owner, String code);
    int incrementFailures(String owner, Duration ttl);
}

public interface RefreshTokenRepository {
    void store(String refreshToken, UUID userId, String familyId, Instant expiresAt);
    RefreshTokenRecord find(String refreshToken);
    RefreshTokenRecord consume(String refreshToken);
    void revoke(String refreshToken);
    void revokeFamily(String familyId);
}
```

Keep equivalent interfaces for registration code/session and password reset tokens. Implement them in `auth.infrastructure.persistence` using the existing Redis, DB, and in-memory logic.

 - [x] **Step 4: Move auth technical adapters**

Move JWT, mail, origin guard filter, and cleanup jobs to infrastructure:

```text
auth.infrastructure.jwt.JwtTokenService
auth.application.port.MailPort
auth.infrastructure.mail.LogMailAdapter
auth.infrastructure.mail.SmtpMailAdapter
auth.infrastructure.web.AuthOriginGuardFilter
auth.infrastructure.job.RefreshTokenCleanupJob
auth.infrastructure.job.PendingRegistrationUserCleanupJob
```

Cleanup jobs must inject application services, not raw service classes:

```java
private final RefreshTokenApplicationService refreshTokenApplicationService;
private final RegistrationApplicationService registrationApplicationService;
```

 - [x] **Step 5: Run auth infrastructure tests GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=InMemoryPasswordResetTokenStoreTest,RedisCaptchaStoreTest,RedisRefreshTokenStoreTest,DbRefreshTokenStoreTest,InMemoryRefreshTokenStoreTest,InMemoryCaptchaStoreTest,RedisRegistrationCodeStoreTest,InMemoryRegistrationCodeStoreTest,RedisPasswordResetTokenStoreTest,AuthOriginGuardFilterTest test
```

Expected: PASS after test packages and imports are updated to the new infrastructure package names.

---

## Task 3: Move Auth Application Services And Controller DTOs

**Files:**
- Move auth application, controller DTO, and tests listed in the File Structure Map.

 - [x] **Step 1: Move auth service tests**

Move:

```bash
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/AuthServiceLoginTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/RefreshTokenApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/PasswordResetServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/CaptchaServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/CaptchaApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/LoginRateLimitServiceTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginRateLimitApplicationServiceTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJobTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/job/RefreshTokenCleanupJobTest.java
git mv backend/community-app/src/test/java/com/nowcoder/community/auth/service/PendingRegistrationUserCleanupJobTest.java backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/job/PendingRegistrationUserCleanupJobTest.java
```

Rewrite tests to use command/result records, not HTTP DTOs or raw service return types.

 - [x] **Step 2: Run auth application tests RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.auth.application.LoginApplicationServiceTest,com.nowcoder.community.auth.application.RefreshTokenApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationVerificationApplicationServiceTest,com.nowcoder.community.auth.application.PasswordResetApplicationServiceTest,com.nowcoder.community.auth.application.CaptchaApplicationServiceTest,AuthControllerUnitTest test
```

Expected: compile failure until auth application package, commands/results, and controller DTO package exist.

 - [x] **Step 3: Implement auth application services**

`AuthApplicationService` keeps the controller-facing API and delegates to focused application services:

```java
LoginResult login(LoginCommand command);
RefreshResult refresh(RefreshCommand command);
void logout(LogoutCommand command);
RegisterResult register(RegisterCommand command);
RegisterCodeResendResult resendRegisterCode(ResendRegisterCodeCommand command);
LoginResult verifyRegisterCode(VerifyRegisterCodeCommand command);
CaptchaIssueResult captcha(IssueCaptchaCommand command);
boolean verifyCaptcha(VerifyCaptchaCommand command);
PasswordResetRequestResult requestPasswordReset(RequestPasswordResetCommand command);
boolean confirmPasswordReset(ConfirmPasswordResetCommand command);
```

Focused application services own transaction boundaries, foreign `user.api.*` calls, repository calls, JWT/mail adapter calls, and result assembly. They must not import `auth.controller.dto`, `auth.infrastructure.persistence`, `auth.infrastructure.mail`, `auth.infrastructure.jwt`, or `auth.infrastructure.web`.

 - [x] **Step 4: Move controller DTO conversion**

`AuthController` imports only:

```java
com.nowcoder.community.auth.application.AuthApplicationService
com.nowcoder.community.auth.application.command.*
com.nowcoder.community.auth.application.result.*
com.nowcoder.community.auth.controller.dto.*
```

Controller conversion helper:

```java
private LoginResponse toResponse(LoginResult result) {
    LoginResponse response = new LoginResponse();
    response.setAccessToken(result.accessToken());
    return response;
}
```

Cookie handling remains in the controller boundary because it is HTTP response binding. The controller reads any `ResponseCookie` from application results and adds it to the servlet response.

 - [x] **Step 5: Delete auth raw service surfaces**

Delete all old production classes under:

```text
auth/service
auth/web
```

No production class should remain in `auth.service` or `auth.web`.

 - [x] **Step 6: Run auth suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=AuthControllerUnitTest,PublicReadEndpointSecurityTest,com.nowcoder.community.auth.application.LoginApplicationServiceTest,com.nowcoder.community.auth.application.RefreshTokenApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationVerificationApplicationServiceTest,com.nowcoder.community.auth.application.PasswordResetApplicationServiceTest,com.nowcoder.community.auth.application.CaptchaApplicationServiceTest,com.nowcoder.community.auth.application.LoginRateLimitApplicationServiceTest,com.nowcoder.community.auth.infrastructure.job.RefreshTokenCleanupJobTest,com.nowcoder.community.auth.infrastructure.job.PendingRegistrationUserCleanupJobTest,DddLayeringArchTest,DtoBoundaryArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 4: Move Ops Behind Its Own Application Service

**Files:**
- Create/move ops files listed in the File Structure Map.

 - [x] **Step 1: Write ops application test**

Create:

```text
backend/community-app/src/test/java/com/nowcoder/community/ops/application/OpsApplicationServiceTest.java
```

Test:

```java
@Test
void reindexSearchShouldDelegateToSearchOwnerApi() {
    SearchReindexActionApi searchApi = mock(SearchReindexActionApi.class);
    when(searchApi.reindex()).thenReturn(new SearchReindexResult("job-1", 12, false, null));
    OpsApplicationService service = new OpsApplicationService(searchApi);

    com.nowcoder.community.ops.application.result.SearchReindexResult result =
            service.reindexSearch(new ReindexSearchCommand());

    assertThat(result.jobId()).isEqualTo("job-1");
    assertThat(result.indexedCount()).isEqualTo(12);
    verify(searchApi).reindex();
}
```

 - [x] **Step 2: Run ops application test RED**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.ops.application.OpsApplicationServiceTest,OpsControllerTest test
```

Expected: compile failure until `OpsApplicationService` and ops command/result records exist.

 - [x] **Step 3: Implement ops application service**

Create:

```java
@Service
public class OpsApplicationService {
    private final SearchReindexActionApi searchReindexActionApi;

    public SearchReindexResult reindexSearch(ReindexSearchCommand command) {
        com.nowcoder.community.search.api.model.SearchReindexResult result = searchReindexActionApi.reindex();
        return new SearchReindexResult(result.jobId(), result.indexedCount(), result.skipped(), result.reason());
    }
}
```

Ops application may depend on foreign `search.api.action` and `search.api.model`. Ops controller must not.

 - [x] **Step 4: Move ops controller DTO conversion**

`OpsController` imports only:

```java
com.nowcoder.community.ops.application.OpsApplicationService
com.nowcoder.community.ops.application.command.ReindexSearchCommand
com.nowcoder.community.ops.application.result.SearchReindexResult
com.nowcoder.community.ops.controller.dto.SearchReindexResponse
```

It must not import `search.api.*`.

 - [x] **Step 5: Run ops suite GREEN**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=com.nowcoder.community.ops.application.OpsApplicationServiceTest,OpsControllerTest,DddLayeringArchTest,ControllerBoundaryArchTest test
```

Expected: PASS.

---

## Task 5: Docs, Scans, And Verification

**Files:**
- Modify docs listed in the Guardrails And Docs section.
- Verify only for scans and Maven commands.

 - [x] **Step 1: Scan for retired auth and ops surfaces**

Run:

```bash
cd /home/feng/code/project/community
rg -n "auth\\.(service|web)\\.|ops\\.dto\\.|ops\\.controller.*search\\.api" backend/community-app/src/main/java backend/community-app/src/test/java
```

Expected: only ArchUnit rule strings remain. No production code imports `auth.service`, `auth.web`, `ops.dto`, or `search.api` from `ops.controller`.

 - [x] **Step 2: Update docs**

Update:

```text
docs/ARCHITECTURE.md
docs/SYSTEM_DESIGN.md
docs/CORE_LOGIC.md
```

Document:

```text
AuthController -> AuthApplicationService -> focused auth application services -> auth domain/repositories -> infrastructure stores/JWT/mail
Auth infrastructure jobs -> auth application services
OpsController -> OpsApplicationService -> search.api.action.SearchReindexActionApi
```

 - [x] **Step 3: Run focused auth and ops suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=AuthControllerUnitTest,AuthOriginGuardFilterTest,PublicReadEndpointSecurityTest,OpsControllerTest,com.nowcoder.community.ops.application.OpsApplicationServiceTest,com.nowcoder.community.auth.application.LoginApplicationServiceTest,com.nowcoder.community.auth.application.RefreshTokenApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationApplicationServiceTest,com.nowcoder.community.auth.application.RegistrationVerificationApplicationServiceTest,com.nowcoder.community.auth.application.PasswordResetApplicationServiceTest,com.nowcoder.community.auth.application.CaptchaApplicationServiceTest test
```

Expected: PASS.

 - [x] **Step 4: Run architecture suite**

Run:

```bash
cd /home/feng/code/project/community/backend
mvn -pl community-app -am -Dtest=DomainBoundaryArchTest,ControllerBoundaryArchTest,ListenerBoundaryArchTest,DddLayeringArchTest,DtoBoundaryArchTest test
```

Expected: PASS.

 - [x] **Step 5: Run full backend verification**

Run:

```bash
cd /home/feng/code/project/community
mvn -f backend/pom.xml -pl community-app -am test
```

Expected: PASS with zero failures and zero errors.

---

## Self-Review

### Spec Coverage

- Auth controller calls same-domain application service only: Task 3.
- Auth domain rules and repository contracts are independent of infrastructure: Task 2.
- Redis/DB/in-memory stores, JWT, mail, web filter, and cleanup jobs move to infrastructure: Task 2.
- Ops controller stops calling foreign `search.api.*` directly: Task 4.
- Legacy `auth.service`, `auth.web`, and `ops.dto` packages are retired: Tasks 1, 3, 4, and 5.

### Placeholder Scan

No step uses placeholder markers. Every task lists exact file paths, concrete class names, exact commands, and expected outcomes.

### Type Consistency

Auth command/result records are used at controller/application boundaries. Auth domain services use domain models and repository interfaces. Infrastructure owns store, JWT, mail, web-filter, and job implementation details. Ops application result records are distinct from search API result records.
