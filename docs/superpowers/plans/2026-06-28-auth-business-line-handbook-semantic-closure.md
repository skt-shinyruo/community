# Auth Business Line Handbook Semantic Closure Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring the `auth` business line runtime behavior, persistence state machines, public contracts, package boundaries, tests, and handbook docs back into semantic alignment with the strict DDD Tactical Layering handbook.

**Architecture:** Keep `auth` as owner of refresh-token strategy and use-case orchestration, while `user` remains owner of DB refresh-session storage facts. Implement recoverable refresh rotation through owner-domain `user.api.*` contracts and keep HTTP cookie decisions in the controller boundary. Move the auth outbound user API adapter from `auth.application` to `auth.infrastructure.api`, and update ArchUnit rules so that package is the only auth infrastructure package allowed to depend on foreign owner APIs.

**Tech Stack:** Java 17, Spring Boot, MyBatis XML mapper, Redis `StringRedisTemplate` Lua scripts, JUnit 5, Mockito, AssertJ, ArchUnit, Maven.

## Global Constraints

- All backend business code in `backend/community-app` must continue to satisfy the repository's strict DDD Tactical Layering from `AGENTS.md`.
- Inbound adapters may only call same-domain `*ApplicationService` entry points.
- `auth` application code may call foreign owner-domain `user.api.query` and `user.api.action`, but may not reach into foreign repositories, mappers, or application internals.
- `user` remains the owner of DB refresh session storage facts; `auth` remains the owner of refresh token strategy.
- Any architecture boundary or package movement that affects backend guardrails must keep the existing ArchUnit suite passing.
- Do not add `UseCase`, `FacadeService`, `CommandService`, `app/query`, `app/command`, or new legacy root `service`, `entity`, or `mapper` surfaces.
- Do not commit changes from this plan unless the user explicitly requests a commit.

---

## File Structure

- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/RefreshTokenSessionState.java`: new plain Java enum for owner-stored refresh session states `ACTIVE`, `PENDING_ROTATION`, `CONSUMED`, `REVOKED`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/RefreshTokenSession.java`: add `state` and `pendingExpiresAt` to the domain model.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/RefreshTokenSessionResult.java`: mirror owner session facts for API adapter mapping.
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/RefreshTokenSessionView.java`: publish owner session facts to `auth`, including state and pending lease.
- `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`: add `beginRotation`, `finishRotation`, and `rollbackPendingRotation`.
- `backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java`: delegate new rotation use cases to the owner repository.
- `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/RefreshTokenSessionRepository.java`: define owner persistence operations for rotation state transitions.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java`: expose new owner API methods and map new view fields.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/RefreshTokenSessionDataObject.java`: map `state` and `pending_expires_at` columns.
- `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/RefreshTokenSessionMapper.java`: add transition methods for active, pending, terminal consumed/revoked, and expired-pending recovery.
- `backend/community-app/src/main/resources/mapper/refresh_token_session_mapper.xml`: implement atomic SQL state transitions.
- `deploy/mysql/community/020_schema_identity.sql`: add production schema columns for `state` and `pending_expires_at`.
- `backend/community-app/src/test/resources/schema.sql`: add test schema columns for `state` and `pending_expires_at`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RefreshTokenRepository.java`: replace destructive `consume` rotation with begin/finish/rollback repository contracts while keeping `find`, `findRevoked`, `revoke`, and `revokeFamily`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/port/RefreshTokenSessionPort.java`: align auth application port with owner API rotation methods.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/api/RefreshTokenSessionApplicationPortAdapter.java`: moved adapter implementation from `auth.application` into `auth.infrastructure.api`.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepository.java`: hash plaintext tokens in auth and call the user owner port for rotation transitions.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`: implement equivalent Redis rotation semantics with Lua scripts.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`: add begin/generate/finish/rollback orchestration helpers and tombstone-aware logout family revocation.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`: coordinate refresh failure handling and return a refresh failure outcome with `clearRefreshCookie` intent.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshFailure.java`: new result exception/outcome carrier for refresh error code plus clear-cookie decision.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`: clear refresh cookie according to application failure outcome instead of deriving solely from error code.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationCodeRepository.java`: add begin/promote/abort replacement contracts.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java`: store pending replacement code fields and preserve active code until mail succeeds.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`: use two-phase resend replacement and abort on mail failure.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/PasswordResetRequestResult.java`: narrow result to `issued` only.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetRequestResponse.java`: remove public `resetLink` field.
- `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`: return the narrowed request result while still generating the reset link only for mail delivery.
- `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`: update auth infrastructure foreign API rule to allow only `auth.infrastructure.api`.
- `docs/handbook/business-logic/auth.md`, `docs/handbook/business-logic/workflows/auth-registration-login.md`, `docs/handbook/auth-login-session-flow.md`, `docs/handbook/core-logic-index.md`: update documented behavior and code references.

---

### Task 1: Add User Refresh Session State Contracts

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/RefreshTokenSessionState.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/model/RefreshTokenSession.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/result/RefreshTokenSessionResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/model/RefreshTokenSessionView.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/domain/repository/RefreshTokenSessionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/action/UserRefreshTokenSessionActionApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapter.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/application/RefreshTokenSessionApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/api/RefreshTokenSessionApiAdapterTest.java`

**Interfaces:**
- Consumes: existing `RefreshTokenSessionRepository.store`, `find`, `consumeActive`, `revoke`, `revokeFamily`, `revokeByUserId`, `deleteExpiredBefore`.
- Produces: `RefreshTokenSessionState`, `beginRotation(String tokenHash, Instant pendingExpiresAt)`, `finishRotation(String pendingTokenHash, String replacementTokenHash, UUID userId, String familyId, Instant replacementExpiresAt)`, `rollbackPendingRotation(String pendingTokenHash)`.

- [ ] **Step 1: Write failing owner application tests**

  Add assertions that the application service maps `state` and `pendingExpiresAt` and delegates all three rotation methods:

  ```java
  @Test
  void rotationMethodsShouldDelegateAndMapState() {
      RefreshTokenSessionApplicationService service = new RefreshTokenSessionApplicationService(repository);
      Instant pendingExpiresAt = Instant.parse("2026-04-20T03:00:30Z");
      RefreshTokenSession pending = new RefreshTokenSession(
              TOKEN_HASH,
              USER_ID,
              "family-1",
              EXPIRES_AT,
              null,
              RefreshTokenSessionState.PENDING_ROTATION,
              pendingExpiresAt
      );
      when(repository.beginRotation(TOKEN_HASH, pendingExpiresAt)).thenReturn(pending);
      when(repository.finishRotation(TOKEN_HASH, "replacement-hash", USER_ID, "family-1", EXPIRES_AT)).thenReturn(true);
      when(repository.rollbackPendingRotation(TOKEN_HASH)).thenReturn(true);

      RefreshTokenSessionResult result = service.beginRotation(TOKEN_HASH, pendingExpiresAt);

      assertThat(result.state()).isEqualTo(RefreshTokenSessionState.PENDING_ROTATION);
      assertThat(result.pendingExpiresAt()).isEqualTo(pendingExpiresAt);
      assertThat(service.finishRotation(TOKEN_HASH, "replacement-hash", USER_ID, "family-1", EXPIRES_AT)).isTrue();
      assertThat(service.rollbackPendingRotation(TOKEN_HASH)).isTrue();
  }
  ```

- [ ] **Step 2: Run owner application/API tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest'`

  Expected: compilation fails because `RefreshTokenSessionState`, new record components, and rotation methods do not exist.

- [ ] **Step 3: Add owner domain/API contracts**

  Add the enum:

  ```java
  package com.nowcoder.community.user.domain.model;

  public enum RefreshTokenSessionState {
      ACTIVE,
      PENDING_ROTATION,
      CONSUMED,
      REVOKED
  }
  ```

  Update the owner domain/result/API records to this shape:

  ```java
  public record RefreshTokenSession(
          String tokenHash,
          UUID userId,
          String familyId,
          Instant expiresAt,
          Instant revokedAt,
          RefreshTokenSessionState state,
          Instant pendingExpiresAt
  ) {
  }
  ```

  Apply the same final two components to `RefreshTokenSessionResult` and `RefreshTokenSessionView`.

- [ ] **Step 4: Add repository and owner API methods**

  Add these signatures without changing old methods yet:

  ```java
  RefreshTokenSession beginRotation(String tokenHash, Instant pendingExpiresAt);

  boolean finishRotation(
          String pendingTokenHash,
          String replacementTokenHash,
          UUID userId,
          String familyId,
          Instant replacementExpiresAt
  );

  boolean rollbackPendingRotation(String pendingTokenHash);
  ```

  Add matching signatures to `UserRefreshTokenSessionActionApi`, with return type `RefreshTokenSessionView` for `beginRotation` and `boolean` for finish/rollback.

- [ ] **Step 5: Implement application and API adapter mapping**

  In `RefreshTokenSessionApplicationService`, delegate to the repository and include `state` and `pendingExpiresAt` in `toResult(...)`. In `RefreshTokenSessionApiAdapter`, validate token hashes with the existing `isValidTokenHash(...)`, delegate to the application service, and map result fields into `RefreshTokenSessionView`.

- [ ] **Step 6: Run owner contract tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest'`

  Expected: PASS.

---

### Task 2: Implement DB Refresh Rotation Persistence

**Files:**
- Modify: `deploy/mysql/community/020_schema_identity.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/dataobject/RefreshTokenSessionDataObject.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/mapper/RefreshTokenSessionMapper.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepository.java`
- Modify: `backend/community-app/src/main/resources/mapper/refresh_token_session_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/infrastructure/persistence/MyBatisRefreshTokenSessionRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 owner repository signatures and `RefreshTokenSessionState` enum.
- Produces: atomic DB semantics for `ACTIVE -> PENDING_ROTATION -> CONSUMED + replacement ACTIVE`, rollback to `ACTIVE`, expired pending recovery, and tombstone-preserving family revocation.

- [ ] **Step 1: Write failing DB transition tests**

  Add tests for the four core transitions:

  ```java
  @Test
  void beginFinishAndRollbackShouldPreserveRecoverableRotationState() {
      Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
      Instant pendingExpiresAt = Instant.parse("2026-04-20T03:00:30Z");
      repository.store(TOKEN_HASH, USER_ID, "family-1", expiresAt);

      RefreshTokenSession pending = repository.beginRotation(TOKEN_HASH, pendingExpiresAt);

      assertThat(pending.state()).isEqualTo(RefreshTokenSessionState.PENDING_ROTATION);
      assertThat(pending.pendingExpiresAt()).isEqualTo(pendingExpiresAt);
      assertThat(repository.find(TOKEN_HASH).revokedAt()).isNull();

      assertThat(repository.rollbackPendingRotation(TOKEN_HASH)).isTrue();
      assertThat(repository.find(TOKEN_HASH).state()).isEqualTo(RefreshTokenSessionState.ACTIVE);

      repository.beginRotation(TOKEN_HASH, pendingExpiresAt);
      assertThat(repository.finishRotation(TOKEN_HASH, REPLACEMENT_HASH, USER_ID, "family-1", expiresAt)).isTrue();
      assertThat(repository.find(TOKEN_HASH).state()).isEqualTo(RefreshTokenSessionState.CONSUMED);
      assertThat(repository.find(TOKEN_HASH).revokedAt()).isNotNull();
      assertThat(repository.find(REPLACEMENT_HASH).state()).isEqualTo(RefreshTokenSessionState.ACTIVE);
  }
  ```

  Add a second test for expired pending recovery:

  ```java
  @Test
  void beginRotationShouldRecoverExpiredPendingBeforeRetry() {
      Instant expiresAt = Instant.parse("2026-04-21T03:00:00Z");
      repository.store(TOKEN_HASH, USER_ID, "family-1", expiresAt);
      repository.beginRotation(TOKEN_HASH, Instant.parse("2026-04-20T03:00:00Z"));

      RefreshTokenSession retried = repository.beginRotation(TOKEN_HASH, Instant.now().plusSeconds(30));

      assertThat(retried).isNotNull();
      assertThat(retried.state()).isEqualTo(RefreshTokenSessionState.PENDING_ROTATION);
  }
  ```

- [ ] **Step 2: Run DB repository tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=MyBatisRefreshTokenSessionRepositoryTest`

  Expected: compilation fails first, then SQL/schema failures until columns and mapper methods exist.

- [ ] **Step 3: Extend production and test schemas**

  Update both schema files so `auth_refresh_token` contains:

  ```sql
  state varchar(32) not null default 'ACTIVE',
  pending_expires_at timestamp null default null,
  check (state in ('ACTIVE', 'PENDING_ROTATION', 'CONSUMED', 'REVOKED'))
  ```

  Keep `revoked_at` for terminal `CONSUMED` and `REVOKED` rows only. Keep existing indexes and add `key idx_refresh_state_pending (state, pending_expires_at)` in production SQL.

- [ ] **Step 4: Map state columns in data object**

  Add `RefreshTokenSessionState state` and `Instant pendingExpiresAt` fields, getters, setters, and include them in `toDomain()`. Treat a null persisted state as `ACTIVE` for backward-compatible test rows:

  ```java
  RefreshTokenSessionState normalizedState = state == null ? RefreshTokenSessionState.ACTIVE : state;
  return new RefreshTokenSession(tokenHash, userId, familyId, expiresAt, revokedAt, normalizedState, pendingExpiresAt);
  ```

- [ ] **Step 5: Add mapper methods and XML SQL**

  Add mapper signatures:

  ```java
  int recoverExpiredPending(@Param("tokenHash") String tokenHash, @Param("now") Instant now);

  int beginRotation(@Param("tokenHash") String tokenHash, @Param("pendingExpiresAt") Instant pendingExpiresAt, @Param("now") Instant now);

  int finishPendingRotation(
          @Param("pendingTokenHash") String pendingTokenHash,
          @Param("replacementTokenHash") String replacementTokenHash,
          @Param("userId") UUID userId,
          @Param("familyId") String familyId,
          @Param("replacementExpiresAt") Instant replacementExpiresAt,
          @Param("now") Instant now
  );

  int rollbackPendingRotation(@Param("tokenHash") String tokenHash);
  ```

  Implement XML so `storeIfFamilyActive` inserts `state='ACTIVE'` and `pending_expires_at=null`; `beginRotation` updates only `state='ACTIVE'`, `revoked_at is null`, and `expires_at > now`; `finishPendingRotation` uses two SQL statements from repository code: insert replacement active only when family is not revoked, then mark pending row `CONSUMED` with `revoked_at=now` and `pending_expires_at=null`.

- [ ] **Step 6: Implement application transaction boundary**

  Annotate `finishRotation(...)` in `RefreshTokenSessionApplicationService` with `@Transactional` so replacement insert and pending consumption succeed or fail together while preserving the repository rule that infrastructure persistence does not own transaction boundaries. Keep input validation no-op behavior consistent with existing repository methods.

- [ ] **Step 7: Run DB repository tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=MyBatisRefreshTokenSessionRepositoryTest`

  Expected: PASS.

---

### Task 3: Update Auth Refresh Repository Ports

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RefreshTokenRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/port/RefreshTokenSessionPort.java`
- Delete: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenSessionApplicationPortAdapter.java`
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/api/RefreshTokenSessionApplicationPortAdapter.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/DbRefreshTokenRepositoryTest.java`

**Interfaces:**
- Consumes: Task 1 owner API methods.
- Produces: auth-owned plaintext token strategy over owner-stored token hashes; adapter lives under `auth.infrastructure.api`.

- [ ] **Step 1: Write failing DB auth repository tests**

  Add tests that verify hash conversion and owner-port calls:

  ```java
  @Test
  void beginFinishAndRollbackRotationShouldHashPresentedTokens() {
      Instant pendingExpiresAt = Instant.parse("2026-04-20T03:00:30Z");
      Instant replacementExpiresAt = Instant.parse("2026-04-21T03:00:00Z");
      RefreshTokenSessionPort.RefreshTokenSession pending = new RefreshTokenSessionPort.RefreshTokenSession(
              sha256Hex("old-refresh"), USER_ID, "family-1", replacementExpiresAt, null,
              RefreshTokenSessionState.PENDING_ROTATION, pendingExpiresAt
      );
      when(refreshTokenSessionPort.beginRotation(sha256Hex("old-refresh"), pendingExpiresAt)).thenReturn(pending);
      when(refreshTokenSessionPort.finishRotation(sha256Hex("old-refresh"), sha256Hex("new-refresh"), USER_ID, "family-1", replacementExpiresAt)).thenReturn(true);
      when(refreshTokenSessionPort.rollbackPendingRotation(sha256Hex("old-refresh"))).thenReturn(true);

      assertThat(store.beginRotation("old-refresh", pendingExpiresAt).familyId()).isEqualTo("family-1");
      assertThat(store.finishRotation("old-refresh", "new-refresh", USER_ID, "family-1", replacementExpiresAt)).isTrue();
      assertThat(store.rollbackPendingRotation("old-refresh")).isTrue();
  }
  ```

- [ ] **Step 2: Run auth DB repository tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=DbRefreshTokenRepositoryTest`

  Expected: compilation fails until auth repository/port rotation methods exist and adapter package is moved.

- [ ] **Step 3: Update auth repository contracts**

  Add methods to `RefreshTokenRepository`:

  ```java
  StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt);

  boolean finishRotation(String pendingRefreshToken, String replacementRefreshToken, UUID userId, String familyId, Instant replacementExpiresAt);

  boolean rollbackPendingRotation(String refreshToken);
  ```

  Keep `consume(String refreshToken)` temporarily as a default compatibility method returning `beginRotation(refreshToken, Instant.now().plusSeconds(30))` only until Task 5 rewires callers; remove it in Task 5 if no production caller remains.

- [ ] **Step 4: Update auth application port and move adapter**

  Change `RefreshTokenSessionPort.RefreshTokenSession` to include `RefreshTokenSessionState state` and `Instant pendingExpiresAt`. Move `RefreshTokenSessionApplicationPortAdapter` to package `com.nowcoder.community.auth.infrastructure.api`, preserving `@Component` and mapping all owner `RefreshTokenSessionView` fields.

- [ ] **Step 5: Implement DB-backed auth repository methods**

  In `DbRefreshTokenRepository`, continue hashing plaintext refresh tokens with SHA-256. Implement:

  ```java
  public StoredRefreshToken beginRotation(String refreshToken, Instant pendingExpiresAt) {
      RefreshTokenSessionPort.RefreshTokenSession record = refreshTokenSessionPort.beginRotation(sha256Hex(refreshToken), pendingExpiresAt);
      return toStoredRefreshToken(refreshToken, record);
  }
  ```

  Implement `finishRotation(...)` and `rollbackPendingRotation(...)` by hashing plaintext old/new tokens and delegating to `RefreshTokenSessionPort`.

- [ ] **Step 6: Run auth DB repository tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=DbRefreshTokenRepositoryTest`

  Expected: PASS.

---

### Task 4: Implement Redis Refresh Rotation Semantics

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepository.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRefreshTokenRepositoryTest.java`

**Interfaces:**
- Consumes: Task 3 `RefreshTokenRepository` rotation methods.
- Produces: Redis store with atomic pending rotation, rollback, finish, tombstone creation, and family member cleanup.

- [ ] **Step 1: Write failing Redis tests**

  Add tests using existing mocked Redis script captures to assert the scripts contain these operations:

  ```java
  assertThat(script.getScriptAsString())
          .contains("PENDING_ROTATION")
          .contains("pendingExpiresAt")
          .contains("CONSUMED")
          .contains("auth:refresh:revoked:");
  ```

  Add an integration-style unit test with the existing test helpers that verifies `beginRotation("t1", lease)` makes `find("t1")` unavailable as active, `rollbackPendingRotation("t1")` restores it, and `finishRotation("t1", "t2", USER_ID, "family-1", expiresAt)` leaves `findRevoked("t1")` populated while `find("t2")` is active.

- [ ] **Step 2: Run Redis refresh tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=RedisRefreshTokenRepositoryTest`

  Expected: compilation or assertion failure until Redis rotation methods and scripts exist.

- [ ] **Step 3: Add Redis record states**

  Replace active `StoredRefreshToken` JSON as needed with a private record that includes `state` and `pendingExpiresAt`:

  ```java
  private record RedisRefreshRecord(
          String refreshToken,
          UUID userId,
          String familyId,
          Instant expiresAt,
          String state,
          Instant pendingExpiresAt
  ) {
  }
  ```

  Continue returning public `StoredRefreshToken` only when stored state is `ACTIVE`.

- [ ] **Step 4: Add Lua-scripted transitions**

  Implement one script per critical mutation: `BEGIN_ROTATION_SCRIPT`, `FINISH_ROTATION_SCRIPT`, and `ROLLBACK_ROTATION_SCRIPT`. `BEGIN_ROTATION_SCRIPT` must recover an expired `PENDING_ROTATION` record to active before trying to set the new pending lease. `FINISH_ROTATION_SCRIPT` must set a revoked tombstone for the pending token, store the replacement active token, remove the old token from the family set, and add the replacement token to that set in one script.

- [ ] **Step 5: Preserve tombstones in revoke paths**

  Update `revoke(String refreshToken)` and `revokeFamily(String familyId)` so active and pending records become terminal tombstones. Reuse detection must read only `auth:refresh:revoked:<token>` and never treat `PENDING_ROTATION` as terminal replay.

- [ ] **Step 6: Run Redis refresh tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest=RedisRefreshTokenRepositoryTest`

  Expected: PASS.

---

### Task 5: Rework Auth Refresh and Logout Orchestration

**Files:**
- Create: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/RefreshFailure.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RefreshTokenApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/LoginApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RefreshTokenApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/LoginApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

**Interfaces:**
- Consumes: Task 3/4 `RefreshTokenRepository.beginRotation`, `finishRotation`, `rollbackPendingRotation`.
- Produces: refresh success path with recoverable two-phase rotation; failure path with explicit `clearRefreshCookie`; logout family revocation from active token or revoked tombstone.

- [ ] **Step 1: Write failing refresh orchestration tests**

  Add tests to `LoginApplicationServiceTest` for rollback and fail-closed behavior:

  ```java
  @Test
  void refreshShouldReturnServiceUnavailableWithoutClearingCookieWhenRollbackSucceeds() {
      UUID userId = uuid(31);
      RefreshTokenRepository.StoredRefreshToken pending = new RefreshTokenRepository.StoredRefreshToken("old-refresh", userId, "family-rollback", Instant.now().plusSeconds(600));
      when(refreshTokenService.beginRotation("old-refresh")).thenReturn(pending);
      when(userCredentialQueryApi.getByUserId(userId)).thenThrow(new RuntimeException("user api down"));
      when(refreshTokenService.rollbackPendingRotation("old-refresh")).thenReturn(true);

      Throwable thrown = catchThrowable(() -> authService.refresh(new RefreshCommand("old-refresh")));

      assertThat(thrown).isInstanceOf(RefreshFailure.class);
      RefreshFailure failure = (RefreshFailure) thrown;
      assertThat(failure.getErrorCode()).isEqualTo(CommonErrorCode.SERVICE_UNAVAILABLE);
      assertThat(failure.clearRefreshCookie()).isFalse();
      verify(refreshTokenService, never()).revokeFamily("family-rollback");
  }
  ```

  Add a companion test where `rollbackPendingRotation("old-refresh")` returns false; expect `clearRefreshCookie=true` and `revokeFamily("family-rollback")`.

- [ ] **Step 2: Write failing logout tombstone test**

  Add a `RefreshTokenApplicationServiceTest` case:

  ```java
  @Test
  void logoutShouldRevokeFamilyFromRevokedTombstoneWhenActiveTokenIsGone() {
      RefreshTokenRepository repository = mock(RefreshTokenRepository.class);
      Instant now = Instant.now();
      when(repository.find("old-refresh")).thenReturn(null);
      when(repository.findRevoked("old-refresh")).thenReturn(new RefreshTokenRepository.RevokedRefreshToken(
              "old-refresh", USER_ID, "family-1", now.plusSeconds(300), now.minusSeconds(2)
      ));
      RefreshTokenApplicationService service = refreshTokenService(repository, jwtProperties());

      service.revokeFamilyByPresentedToken("old-refresh");

      verify(repository).revokeFamily("family-1");
  }
  ```

- [ ] **Step 3: Run refresh/controller tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RefreshTokenApplicationServiceTest,LoginApplicationServiceTest,AuthControllerUnitTest'`

  Expected: compilation fails until `RefreshFailure`, begin/finish/rollback service methods, and controller handling exist.

- [ ] **Step 4: Add refresh failure carrier**

  Add a class under application result package:

  ```java
  public class RefreshFailure extends BusinessException {
      private final boolean clearRefreshCookie;

      public RefreshFailure(ErrorCode errorCode, boolean clearRefreshCookie) {
          super(errorCode);
          this.clearRefreshCookie = clearRefreshCookie;
      }

      public RefreshFailure(ErrorCode errorCode, String message, Throwable cause, boolean clearRefreshCookie) {
          super(errorCode, message, cause);
          this.clearRefreshCookie = clearRefreshCookie;
      }

      public boolean clearRefreshCookie() {
          return clearRefreshCookie;
      }
  }
  ```

- [ ] **Step 5: Refactor `RefreshTokenApplicationService`**

  Add:

  ```java
  public RefreshTokenRepository.StoredRefreshToken beginRotation(String refreshToken) {
      Instant pendingExpiresAt = Instant.now().plusSeconds(30);
      RefreshTokenRepository.StoredRefreshToken pending = refreshTokenStore.beginRotation(refreshToken, pendingExpiresAt);
      if (pending == null) {
          maybeRevokeFamilyForReusedToken(refreshToken);
      }
      return pending;
  }

  public IssuedRefreshToken generateReplacementToken(UUID userId, String familyId) {
      String tokenValue = secureTokenValue();
      return new IssuedRefreshToken(tokenValue, buildCookie(tokenValue));
  }
  ```

  Add `finishRotation(...)`, `rollbackPendingRotation(...)`, and `revokeFamilyByPresentedToken(...)`. Keep `issue(...)` for login and registration login issuance.

- [ ] **Step 6: Refactor `LoginApplicationService.refresh`**

  Use this order: missing cookie -> `RefreshFailure(AuthErrorCode.REFRESH_TOKEN_INVALID, true)`; begin rotation; user lookup/refreshAllowed validation; issue access token; generate replacement plaintext; finish rotation. For exceptions after begin: try rollback; if rollback succeeds, throw `RefreshFailure(CommonErrorCode.SERVICE_UNAVAILABLE, message, ex, false)`; if rollback fails, revoke family and throw the same error with `clearRefreshCookie=true`.

  For user disabled or missing user, revoke family and throw `RefreshFailure(AuthErrorCode.USER_DISABLED, true)`.

- [ ] **Step 7: Refactor controller clear-cookie handling**

  In `AuthController.refresh`, catch `RefreshFailure` before generic `BusinessException`:

  ```java
  } catch (RefreshFailure ex) {
      if (ex.clearRefreshCookie()) {
          addRefreshCookie(response, loginApplicationService.clearRefreshCookie());
      }
      throw ex;
  }
  ```

  Keep generic `BusinessException` fallback for legacy `REFRESH_TOKEN_INVALID` and `USER_DISABLED` until all production callers are converted.

- [ ] **Step 8: Run refresh/controller tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RefreshTokenApplicationServiceTest,LoginApplicationServiceTest,AuthControllerUnitTest'`

  Expected: PASS.

---

### Task 6: Make Registration Resend Two-Phase

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/domain/repository/RegistrationCodeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationService.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/infrastructure/persistence/RedisRegistrationCodeRepositoryTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/RegistrationVerificationApplicationServiceTest.java`

**Interfaces:**
- Consumes: existing active registration code and verify pending-consumption lifecycle.
- Produces: `beginReplacement`, `promoteReplacement`, and `abortReplacement` so mail failure preserves the prior active code.

- [ ] **Step 1: Write failing resend mail failure test**

  Add to `RegistrationVerificationApplicationServiceTest`:

  ```java
  @Test
  void resendCodeShouldAbortReplacementWhenMailSendingFails() {
      UUID userId = uuid(7);
      doNothing().when(captchaChallenge).requireValidCaptcha("cid", "abcd");
      when(registrationDraftRepository.find("token")).thenReturn(Optional.of(draft(userId)));
      when(registrationCodeStore.beginReplacement(eq(userId), matches("\\d{6}"), eq(Duration.ofSeconds(600)), eq(Duration.ofSeconds(60))))
              .thenReturn(RegistrationCodeRepository.IssueResult.ISSUED);
      doThrow(new IllegalStateException("mail down")).when(mailService).sendRegistrationCodeMail(eq("alice@example.com"), matches("\\d{6}"));

      assertThatThrownBy(() -> service.resendCode(new ResendRegisterCodeCommand("token", "cid", "abcd")))
              .isInstanceOf(IllegalStateException.class)
              .hasMessage("mail down");

      verify(registrationCodeStore).abortReplacement(userId);
      verify(registrationCodeStore, never()).promoteReplacement(userId);
  }
  ```

- [ ] **Step 2: Write failing Redis repository contract tests**

  Assert the begin replacement script stores active and pending fields, verify reads active code only, promote swaps pending to active, and abort clears pending. At minimum assert captured scripts contain `pendingCode`, `PENDING_REPLACEMENT`, `promote`, and `abort`-equivalent field mutations.

- [ ] **Step 3: Run registration tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RegistrationVerificationApplicationServiceTest,RedisRegistrationCodeRepositoryTest'`

  Expected: compilation fails until repository methods exist.

- [ ] **Step 4: Add repository methods**

  Add signatures:

  ```java
  IssueResult beginReplacement(UUID userId, String code, Duration ttl, Duration cooldown);

  void promoteReplacement(UUID userId);

  void abortReplacement(UUID userId);
  ```

  Keep `issue(...)` for initial registration issuance and let `save(...)` continue delegating to initial issue.

- [ ] **Step 5: Extend Redis registration payload and scripts**

  Use a pipe-delimited payload with these fields:

  ```text
  activeCode|activeExpiresAtMs|failures|issuedAtMs|consumeState|pendingCode|pendingExpiresAtMs|pendingIssuedAtMs
  ```

  `verifyForConsumption(...)` must compare only `activeCode`. `beginReplacement(...)` must respect cooldown based on `issuedAtMs`, write pending fields, and keep active fields unchanged. `promoteReplacement(...)` must replace active fields with pending values and reset failures to `0`. `abortReplacement(...)` must clear only pending fields.

- [ ] **Step 6: Refactor resend application flow**

  Replace `registrationCodeStore.issue(...)` with `beginReplacement(...)`; send mail; on success call `promoteReplacement(...)`; on mail failure call `abortReplacement(...)` before rethrowing.

- [ ] **Step 7: Run registration tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='RegistrationVerificationApplicationServiceTest,RedisRegistrationCodeRepositoryTest'`

  Expected: PASS.

---

### Task 7: Narrow Password Reset Request Contract

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/result/PasswordResetRequestResult.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/dto/PasswordResetRequestResponse.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/application/PasswordResetApplicationService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/controller/AuthController.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/application/PasswordResetApplicationServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/controller/AuthControllerUnitTest.java`

**Interfaces:**
- Consumes: existing password reset request mail flow.
- Produces: public response contract with only `issued` and no `resetLink` field.

- [ ] **Step 1: Write failing response shape tests**

  Update password reset application tests to assert the result has no `resetLink` accessor:

  ```java
  assertThat(Arrays.stream(PasswordResetRequestResult.class.getRecordComponents())
          .map(RecordComponent::getName))
          .containsExactly("issued");
  ```

  Add controller DTO test:

  ```java
  assertThat(Arrays.stream(PasswordResetRequestResponse.class.getDeclaredFields())
          .map(Field::getName))
          .containsExactly("issued");
  ```

- [ ] **Step 2: Run reset tests and confirm failure**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='PasswordResetApplicationServiceTest,AuthControllerUnitTest'`

  Expected: tests fail because `resetLink` is still present.

- [ ] **Step 3: Narrow result and DTO**

  Change application result to:

  ```java
  public record PasswordResetRequestResult(boolean issued) {
  }
  ```

  Change controller DTO to a JavaBean with only `issued`, default constructor, `PasswordResetRequestResponse(boolean issued)`, getter, and setter.

- [ ] **Step 4: Update service and controller mapping**

  Return `new PasswordResetRequestResult(true)` in all request-reset paths. Keep `String resetLink = buildResetLink(resetBaseUrl, token);` as an internal variable used only for `mailService.sendPasswordResetMail(...)`. In `AuthController`, map with `new PasswordResetRequestResponse(result.issued())`.

- [ ] **Step 5: Run reset tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='PasswordResetApplicationServiceTest,AuthControllerUnitTest'`

  Expected: PASS.

---

### Task 8: Update Architecture Guardrails

**Files:**
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/app/arch/DddLayeringArchTest.java`

**Interfaces:**
- Consumes: Task 3 adapter move to `auth.infrastructure.api`.
- Produces: ArchUnit rule allowing only auth outbound API adapters in `auth.infrastructure.api` to depend on foreign owner `api.*` contracts.

- [ ] **Step 1: Write/update failing ArchUnit expectation**

  Replace the current broad `auth_infrastructure_must_not_call_foreign_owner_apis` rule with:

  ```java
  @ArchTest
  static final ArchRule auth_non_api_infrastructure_must_not_call_foreign_owner_apis =
          noClasses()
                  .that().resideInAnyPackage("..auth.infrastructure..")
                  .and().resideOutsideOfPackage("..auth.infrastructure.api..")
                  .should().dependOnClassesThat().resideInAnyPackage("..api.query..", "..api.action..", "..api.model..")
                  .because("foreign synchronous collaboration from auth infrastructure is limited to outbound API adapters")
                  .allowEmptyShould(true);
  ```

- [ ] **Step 2: Run architecture test and confirm current failure before change**

  Run before editing the rule after Task 3 moves the adapter: `cd backend && mvn test -am -pl :community-app -Dtest=DddLayeringArchTest`

  Expected: FAIL because `auth.infrastructure.api.RefreshTokenSessionApplicationPortAdapter` depends on `user.api.*` under the old broad rule.

- [ ] **Step 3: Apply the rule narrowing**

  Update only the auth infrastructure owner API rule. Do not weaken controller, domain, application, DTO, listener, transaction, or infra boundary rules.

- [ ] **Step 4: Run architecture tests and confirm pass**

  Run: `cd backend && mvn test -am -pl :community-app -Dtest='*ArchTest'`

  Expected: PASS.

---

### Task 9: Update Handbook Documentation

**Files:**
- Modify: `docs/handbook/business-logic/auth.md`
- Modify: `docs/handbook/business-logic/workflows/auth-registration-login.md`
- Modify: `docs/handbook/auth-login-session-flow.md`
- Modify: `docs/handbook/core-logic-index.md`

**Interfaces:**
- Consumes: completed runtime behavior from Tasks 1-8.
- Produces: docs aligned with implemented semantics and package paths.

- [ ] **Step 1: Update refresh lifecycle docs**

  In `docs/handbook/auth-login-session-flow.md`, replace direct consume wording with recoverable two-phase wording:

  ```text
  refresh first moves the presented refresh session to PENDING_ROTATION with a 30-second lease. After user-state validation and replacement session persistence succeed, the old row becomes CONSUMED and the replacement row becomes ACTIVE. If a transient failure happens after begin-rotation, auth first rolls the old row back to ACTIVE; if rollback is unsafe, auth revokes the family and clears the browser cookie.
  ```

- [ ] **Step 2: Update logout docs**

  Document that logout resolves family identity from either the active session row or the terminal tombstone kept for reuse detection, and always clears the browser cookie at the controller boundary.

- [ ] **Step 3: Update resend docs**

  Document that resend writes a pending replacement code, promotes it only after mail delivery succeeds, and aborts by leaving the prior active code valid when mail delivery fails.

- [ ] **Step 4: Update reset response docs and class links**

  Remove public `resetLink` from password reset response descriptions. Fix the `DbRefreshTokenRepository` link in `docs/handbook/auth-login-session-flow.md` so it points to `auth/infrastructure/persistence/DbRefreshTokenRepository.java`, and add the moved adapter path `auth/infrastructure/api/RefreshTokenSessionApplicationPortAdapter.java` where owner API collaboration is described.

- [ ] **Step 5: Search docs for stale direct-consume/resetLink references**

  Run: `rg -n "consume\(\.\.\.\)|resetLink|auth/application/DbRefreshTokenRepository|直接消费旧 token|先消费旧 token" docs/handbook`

  Expected: no stale public reset-link contract and no stale adapter path. Mentions of consumed terminal tombstones are acceptable only when describing replay detection.

---

### Task 10: Run Targeted Regression Suite

**Files:**
- No source files. This task verifies all previous tasks together.

**Interfaces:**
- Consumes: Tasks 1-9.
- Produces: verified backend behavior and guardrail alignment.

- [ ] **Step 1: Run targeted auth/user tests**

  Run:

  ```bash
  cd backend
  mvn test -am -pl :community-app -Dtest='RefreshTokenSessionApplicationServiceTest,RefreshTokenSessionApiAdapterTest,MyBatisRefreshTokenSessionRepositoryTest,DbRefreshTokenRepositoryTest,RedisRefreshTokenRepositoryTest,RefreshTokenApplicationServiceTest,LoginApplicationServiceTest,AuthControllerUnitTest,RegistrationVerificationApplicationServiceTest,RedisRegistrationCodeRepositoryTest,PasswordResetApplicationServiceTest'
  ```

  Expected: PASS.

- [ ] **Step 2: Run architecture guardrails**

  Run:

  ```bash
  cd backend
  mvn test -am -pl :community-app -Dtest='*ArchTest'
  ```

  Expected: PASS.

- [ ] **Step 3: Run broader community-app tests if targeted suite passes**

  Run:

  ```bash
  cd backend
  mvn test -am -pl :community-app
  ```

  Expected: PASS, or only unrelated pre-existing failures. If unrelated failures appear, record the failing test names and error summaries without changing unrelated code.

- [ ] **Step 4: Inspect final diff for boundaries and public contract**

  Run:

  ```bash
  git diff -- backend/community-app/src/main/java/com/nowcoder/community/auth backend/community-app/src/main/java/com/nowcoder/community/user backend/community-app/src/test/java/com/nowcoder/community/auth backend/community-app/src/test/java/com/nowcoder/community/user backend/community-app/src/test/java/com/nowcoder/community/app/arch docs/handbook deploy/mysql/community/020_schema_identity.sql
  ```

  Confirm: no controller calls foreign APIs, no application code depends on infrastructure, no domain code depends on Spring/API/infrastructure, `resetLink` is absent from public password reset response, and `auth.infrastructure.api` is the only auth infrastructure package calling `user.api.*`.

---

## Self-Review

**Spec coverage:** Covered refresh two-phase rotation, refresh failure rollback/fail-closed cookie behavior, logout tombstone family resolution, registration resend two-phase replacement, password reset response narrowing, adapter package move, architecture guardrail update, schema changes, Redis/DB parity, tests, and handbook docs.

**Placeholder scan:** No implementation step uses placeholder marker text. Code snippets use concrete method names, record fields, and command lines.

**Type consistency:** `RefreshTokenSessionState`, `state`, `pendingExpiresAt`, `beginRotation`, `finishRotation`, and `rollbackPendingRotation` are named consistently across domain model, application result, API model, repository, owner API, auth port, and auth repository tasks.

**Risk sequencing:** Owner API and persistence support land before auth orchestration depends on rollback semantics. Architecture rule changes land after the adapter move so the failing guardrail is meaningful. Documentation lands after runtime behavior to avoid docs leading code.
