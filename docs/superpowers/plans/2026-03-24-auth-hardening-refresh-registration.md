# Auth Hardening (Refresh + Registration) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Fix refresh concurrency logout, harden registration verification against targeted abuse, add DB refresh token reuse detection (with grace), and add expired refresh token cleanup.

**Architecture:** Keep the existing JWT+refresh-cookie model. Improve robustness by adjusting refresh-cookie clearing semantics, introducing an opaque registration context token stored server-side, adding DB-store-only reuse handling with a grace window, and adding a periodic cleanup job.

**Tech Stack:** Spring Boot (Servlet), Spring Security resource server (JWT), Redis (`StringRedisTemplate`), MySQL (`JdbcTemplate`), Vue 3 + Pinia + Axios, Vitest.

**Spec:** `docs/superpowers/specs/2026-03-24-auth-hardening-refresh-registration.md`

---

### Task 1: Stop Clearing Refresh Cookie on `REFRESH_TOKEN_INVALID`

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

- [ ] **Step 1: Write/Update failing unit test**
  - Update the test currently asserting cookie clearing on invalid refresh token to instead assert **no** `Set-Cookie` clear header is added.
  - Test name suggestion: `refreshShouldNotClearRefreshCookieWhenTokenIsInvalid`.

- [ ] **Step 2: Run backend unit tests (RED)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=AuthControllerUnitTest
```
Expected: FAIL on the updated assertion.

- [ ] **Step 3: Minimal implementation**
  - In `AuthController.shouldClearRefreshCookie(...)`, remove `AuthErrorCode.REFRESH_TOKEN_INVALID` from the clear-cookie condition.
  - Keep clearing for `AuthErrorCode.USER_DISABLED`.

- [ ] **Step 4: Re-run backend unit tests (GREEN)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=AuthControllerUnitTest
```
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java
git commit -m "fix(auth): avoid clearing refresh cookie on refresh token invalid"
```

---

### Task 2: Issue and Persist `registrationToken` on Register (Backend)

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterResponse.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationSessionStore.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisRegistrationSessionStore.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/InMemoryRegistrationSessionStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java`

- [ ] **Step 1: Add failing unit test asserting `registrationToken` is returned**
  - In `RegistrationServiceTest`, after `service.register(...)`, assert `response.getRegistrationToken()` is non-blank and matches a safe pattern (e.g. `[a-f0-9]{32}` or `\\w{16,64}`).

- [ ] **Step 2: Run backend unit test (RED)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=RegistrationServiceTest
```
Expected: FAIL (missing getter/field, null token).

- [ ] **Step 3: Implement `RegistrationSessionStore`**
  - `issue(userId, ttl)` returns a random token and stores mapping `token -> userId` with TTL.
  - `findUserId(token)` returns userId or null.
  - `delete(token)` best-effort.
  - Default implementation: Redis (`auth:regsession:<token>`), fallback: in-memory for tests.

- [ ] **Step 4: Wire `RegistrationService`**
  - After creating pending user, issue `registrationToken` with TTL equal to pending-user TTL.
  - Set it into `RegisterResponse`.

- [ ] **Step 5: Re-run backend unit test (GREEN)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=RegistrationServiceTest
```
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterResponse.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationSessionStore.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisRegistrationSessionStore.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/InMemoryRegistrationSessionStore.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationServiceTest.java
git commit -m "feat(auth): issue opaque registrationToken for pending registration"
```

---

### Task 3: Switch Resend/Verify APIs to Use `registrationToken` (Backend + Frontend)

**Backend files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterCodeResendRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterCodeVerifyRequest.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

**Frontend files:**
- Modify: `frontend/src/api/services/authService.js`
- Modify: `frontend/src/views/RegisterView.vue`
- Modify: `frontend/src/views/registerFlowState.js`
- Modify: `frontend/src/api/services/authService.test.js`

- [ ] **Step 1: Backend failing tests**
  - Update `RegistrationVerificationServiceTest` to call `resendCode(registrationToken, ...)` and `verifyAndLogin(registrationToken, ...)`.
  - Add test coverage: missing/expired `registrationToken` returns `UserErrorCode.USER_NOT_FOUND (11001)`.

- [ ] **Step 2: Implement backend switch**
  - Update DTOs to use `registrationToken` (string) instead of `userId`.
  - In `RegistrationVerificationService`, resolve `registrationToken -> userId` via `RegistrationSessionStore`.
  - On successful verify+activate, delete the session token (best-effort).
  - Ensure missing token throws `BusinessException(UserErrorCode.USER_NOT_FOUND, ...)`.

- [ ] **Step 3: Backend test run (RED->GREEN)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=RegistrationVerificationServiceTest,AuthControllerUnitTest
```

- [ ] **Step 4: Frontend switch**
  - Store `registrationToken` in register flow state persistence (`community.register.pending`).
  - Update API calls to send `registrationToken` for resend/verify.

- [ ] **Step 5: Frontend tests**

Run:
```bash
cd frontend && npm test
```

- [ ] **Step 6: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterCodeResendRequest.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/dto/RegisterCodeVerifyRequest.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RegistrationVerificationService.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RegistrationVerificationServiceTest.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java \
        frontend/src/api/services/authService.js \
        frontend/src/views/RegisterView.vue \
        frontend/src/views/registerFlowState.js \
        frontend/src/api/services/authService.test.js
git commit -m "fix(auth): use registrationToken for resend/verify to prevent userId targeting"
```

---

### Task 4: DB Refresh Token Reuse Detection (Grace Window + Family Revoke)

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/security/jwt/JwtProperties.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/DbRefreshTokenStore.java`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/DbRefreshTokenStoreTest.java`

- [ ] **Step 1: Add failing unit tests**
  - When `consume(token)` fails and `find(tokenHash)` returns revoked token older than grace, `revokeFamily(familyId)` is called.
  - When revoked within grace window, family revoke is **not** called.

- [ ] **Step 2: Run backend unit tests (RED)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=DbRefreshTokenStoreTest
```

- [ ] **Step 3: Implement**
  - Add `security.jwt.refresh-reuse-grace-seconds` to `JwtProperties` (default 10).
  - Inject `JwtProperties` into `DbRefreshTokenStore`.
  - On consume failure: lookup record via `RefreshTokenSessionService.find(tokenHash)`.
  - If revoked and outside grace and not expired: call `revokeFamily(familyId)`.

- [ ] **Step 4: Re-run tests (GREEN)**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=DbRefreshTokenStoreTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/infra/security/jwt/JwtProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/DbRefreshTokenStore.java \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/DbRefreshTokenStoreTest.java
git commit -m "feat(auth): detect refresh token reuse (db) and revoke family with grace"
```

---

### Task 5: Cleanup Job for Expired DB Refresh Tokens

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionService.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/config/RefreshTokenCleanupProperties.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJob.java`
- Modify: `backend/community-app/src/main/resources/application.yml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJobTest.java`

- [ ] **Step 1: Write failing unit test**
  - Create `RefreshTokenCleanupJobTest` verifying `cleanup()` delegates to session service and does not throw.

- [ ] **Step 2: Implement repository/service cleanup**
  - Repository method: `int deleteExpiredBefore(Instant cutoff)`.
  - Service method delegates.

- [ ] **Step 3: Implement job + properties**
  - `auth.refresh.cleanup.enabled` default true
  - `auth.refresh.cleanup.interval-ms` default 3600000
  - Job runs `deleteExpiredBefore(Instant.now())` and logs deleted count when > 0.
  - Fail-safe: catch RuntimeException and log warn.

- [ ] **Step 4: Run backend unit tests**

Run:
```bash
mvn -q test -pl backend/community-app -Dtest=RefreshTokenCleanupJobTest
```

- [ ] **Step 5: Commit**

```bash
git add backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionRepository.java \
        backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionService.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/config/RefreshTokenCleanupProperties.java \
        backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJob.java \
        backend/community-app/src/main/resources/application.yml \
        backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenCleanupJobTest.java
git commit -m "chore(auth): cleanup expired refresh token records"
```

---

### Final Verification: Full Test Runs

- [ ] **Backend**

Run:
```bash
mvn -q test -pl backend/community-app
```

- [ ] **Frontend**

Run:
```bash
cd frontend && npm test
```

