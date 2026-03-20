# Backend Integrity Hardening Implementation Plan

> **For agentic workers:** REQUIRED: Use superpowers:subagent-driven-development (if subagents available) or superpowers:executing-plans to implement this plan. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Eliminate the backend correctness and security flaws identified in auth, identity, message, and IM configuration flows.

**Architecture:** Fix the issues at the ownership boundary where they originate instead of layering controller-only guards on top. The implementation should enforce database invariants in schema, make one-time token consumption atomic in storage, separate system notices from user private messages in the message model, and harden JWT validation/default-secret handling in both the monolith and IM services.

**Tech Stack:** Java 17, Spring Boot 3, Spring Security, MyBatis, JdbcTemplate, MySQL schema init SQL, Redis, Maven, JUnit 5

---

### Task 1: Identity Uniqueness And Registration Hardening

**Files:**
- Modify: `deploy/mysql-init/010_schema.sql`
- Modify: `backend/community-app/src/test/resources/schema.sql`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/service/InternalUserService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/dao/UserMapper.java`
- Modify: `backend/community-app/src/main/resources/mapper/user_mapper.xml`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/user/service/InternalUserServiceTest.java`

- [ ] **Step 1: Write the failing tests for duplicate username/email registration handling**

  Cover:
  - duplicate username returns `USER_ALREADY_EXISTS`
  - duplicate email returns `EMAIL_ALREADY_EXISTS`
  - concurrent-style duplicate insert path is translated correctly when the DB rejects the insert

- [ ] **Step 2: Run the targeted test command and confirm RED**

  Run: `mvn -pl backend/community-app -Dtest=InternalUserServiceTest test`

- [ ] **Step 3: Add database-level uniqueness constraints in schema init and test schema**

  Add:
  - unique index on `user.username`
  - unique index on `user.email`
  - compatibility-safe index creation in `deploy/mysql-init/010_schema.sql`

- [ ] **Step 4: Make registration resilient to DB-enforced duplicates**

  Implement:
  - keep pre-checks for friendly validation
  - catch duplicate-key style persistence failures during `insertUser`
  - translate username/email collisions to existing domain error codes without exposing raw SQL exceptions

- [ ] **Step 5: Re-run the targeted registration tests and verify GREEN**

  Run: `mvn -pl backend/community-app -Dtest=InternalUserServiceTest test`

- [ ] **Step 6: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 2: Auth Token Atomicity And JWT Validation Hardening

**Files:**
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/AuthService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RefreshTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/DbRefreshTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisRefreshTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/InMemoryRefreshTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/UserAuthAccess.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/application/UserAuthApiImpl.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/api/internal/UserAuthApi.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/user/session/RefreshTokenSessionRepository.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/PasswordResetTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/RedisPasswordResetTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/auth/service/InMemoryPasswordResetTokenStore.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/infra/security/autoconfig/ServletInfraSecurityConfig.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RefreshTokenServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/RedisPasswordResetTokenStoreTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/auth/service/InMemoryPasswordResetTokenStoreTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/infra/security/autoconfig/JwtDecoderIssuerValidationTest.java`

- [ ] **Step 1: Write failing tests for one-time token consumption and issuer validation**

  Cover:
  - `AuthService.refresh()` rejects a second concurrent-or-replayed use of the same refresh token
  - refresh token rotation only succeeds once for the same presented token
  - password reset token consume is atomic and one-shot
  - password reset token consume is atomic in both Redis and in-memory implementations
  - JWT decoder rejects tokens with the wrong issuer

- [ ] **Step 2: Run the targeted auth/security tests and confirm RED**

  Run: `mvn -pl backend/community-app -Dtest=RefreshTokenServiceTest,RedisPasswordResetTokenStoreTest,InMemoryPasswordResetTokenStoreTest,JwtDecoderIssuerValidationTest test`

- [ ] **Step 3: Introduce atomic consume/rotate primitives at the storage boundary**

  Implement:
  - refresh token store API that consumes or revokes only if still active
  - DB update path that uses `revoked_at is null` as compare-and-set guard
  - Redis/in-memory equivalents with one-shot semantics

- [ ] **Step 4: Refactor auth service to use the atomic storage primitive**

  Implement:
  - `refresh()` path must fail if the token was already consumed by another request
  - `logout()` family revocation should still work with the new API
  - password reset confirmation should use atomic consume, not `GET` then `DELETE`

- [ ] **Step 5: Enforce issuer validation in the resource server decoder**

  Implement:
  - attach issuer validator based on `security.jwt.issuer`
  - keep existing HMAC verification intact

- [ ] **Step 6: Re-run the targeted auth/security tests and verify GREEN**

  Run: `mvn -pl backend/community-app -Dtest=RefreshTokenServiceTest,RedisPasswordResetTokenStoreTest,InMemoryPasswordResetTokenStoreTest,JwtDecoderIssuerValidationTest test`

- [ ] **Step 7: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 3: Message Integrity And Notice Modeling

**Files:**
- Modify: `backend/community-app/src/main/resources/mapper/message_mapper.xml`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/api/MessageController.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/PrivateMessageService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/service/NoticeService.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/security/ConversationIdParser.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/entity/Message.java`
- Modify: `backend/community-app/src/main/java/com/nowcoder/community/message/dao/MessageMapper.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/PrivateMessageServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/service/NoticeServiceTest.java`
- Test: `backend/community-app/src/test/java/com/nowcoder/community/message/api/MessageControllerTest.java`

- [ ] **Step 1: Write failing tests for invalid/self-message and notice separation cases**

  Cover:
  - sending to a non-existent user is rejected even when `toId` is provided directly
  - sending a private message to self is rejected
  - system notices are not modeled by overloading a real user id
  - conversation reads still work for valid two-party conversations

- [ ] **Step 2: Run the targeted message tests and confirm RED**

  Run: `mvn -pl backend/community-app -Dtest=PrivateMessageServiceTest,NoticeServiceTest,MessageControllerTest test`

- [ ] **Step 3: Fix write-path validation before persistence**

  Implement:
  - resolve and validate `toId` consistently
  - reject self-message writes before generating `conversationId`
  - keep block/moderation checks after target validation

- [ ] **Step 4: Separate system notices from user id `1` semantics**

  Implement one explicit model and use it consistently:
  - reserve sender id `0` as the system-notice sentinel because application user ids are positive integers
  - update mapper queries accordingly so private messages and notices no longer rely on `from_id = 1`
  - document how existing historical `from_id = 1` notice rows are interpreted or migrated during rollout

- [ ] **Step 5: Align schema and mapper queries with the new notice model**

  Implement:
  - schema init changes for any new discriminator/indexes
  - MyBatis queries updated to select letters vs notices without relying on `from_id = 1`

- [ ] **Step 6: Re-run the targeted message tests and verify GREEN**

  Run: `mvn -pl backend/community-app -Dtest=PrivateMessageServiceTest,NoticeServiceTest,MessageControllerTest test`

- [ ] **Step 7: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 4: IM JWT Secret Hardening

**Files:**
- Modify: `backend/community-im/im-core/src/main/resources/application.yml`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security/ImCoreSecurityConfig.java`
- Test: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/security/ImCoreSecurityConfigTest.java`
- Optionally Modify: `docs/SECURITY.md`

- [ ] **Step 1: Write the failing test for missing/default IM JWT secret handling**

  Cover:
  - application startup/config binding rejects the known development fallback secret in hardened mode
  - valid explicit secret still works

- [ ] **Step 2: Run the targeted IM security test and confirm RED**

  Run: `mvn -pl backend/community-im/im-core -Dtest=ImCoreSecurityConfigTest test`

- [ ] **Step 3: Remove the dangerous default fallback and require explicit configuration**

  Implement:
  - no default HMAC secret in IM `application.yml`
  - startup/config validation that rejects blank or known placeholder secrets

- [ ] **Step 4: Re-run the targeted IM security test and verify GREEN**

  Run: `mvn -pl backend/community-im/im-core -Dtest=ImCoreSecurityConfigTest test`

- [ ] **Step 5: Checkpoint the diff for this task**

  Note: do not create a git commit unless the user explicitly asks for one.

### Task 5: Final Verification

**Files:**
- Verify only

- [ ] **Step 1: Run the focused suites for all touched areas**

  Run:
  - `mvn -pl backend/community-app -Dtest=InternalUserServiceTest,RefreshTokenServiceTest,RedisPasswordResetTokenStoreTest,JwtDecoderIssuerValidationTest,PrivateMessageServiceTest,NoticeServiceTest,MessageControllerTest test`
  - `mvn -pl backend/community-im/im-core -Dtest=ImCoreSecurityConfigTest test`

- [ ] **Step 2: Run a broader module-level verification**

  Run:
  - `mvn -pl backend/community-app test`
  - `mvn -pl backend/community-im/im-core test`

- [ ] **Step 3: Re-check the original findings against code and schema**

  Verify:
  - uniqueness is enforced in DB and service layer
  - refresh/reset tokens are single-use under concurrency
  - notices no longer collide with real users
  - self-message and raw invalid `toId` writes are blocked
  - IM no longer has a usable built-in JWT secret

- [ ] **Step 4: Prepare final review summary with residual risks**

  Include:
  - exact tests run
  - any follow-up migration or data cleanup needed for existing environments
