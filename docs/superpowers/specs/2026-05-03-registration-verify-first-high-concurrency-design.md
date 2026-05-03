# Registration Verify-First High Concurrency Design

**Date:** 2026-05-03
**Status:** Proposed
**Owner:** Codex

---

## 1. Goal

Current registration creates a `user` row with `status=0` before email verification. That makes unverified registration traffic write the core user table, occupy username/email unique indexes, trigger pending-user cleanup, and publish user policy events that IM must consume.

The target design changes registration to Verify-First:

- registration request creates only an expiring registration draft and verification code;
- no `user` row exists until the email code is verified;
- successful verification creates the real active user in one user-domain transaction;
- user policy events are published only for real users.

This design targets high-concurrency and high-traffic registration bursts where most attempts may never verify.

---

## 2. Existing Problems

### 2.1 Pending Users Pollute The User Table

`user.application.UserRegistrationApplicationService.registerPendingUser` inserts a `user` row with `status=0`. Expired rows are later removed by scheduled cleanup. Under large traffic, the core `user` table absorbs unverified traffic and cleanup pressure.

### 2.2 Cleanup Correctness Depends On Repeated Scans

`cleanupExpiredPendingUsers` scans up to 500 expired pending users, deletes them one by one, and returns the number deleted. Callers loop while `deleted > 0`. If a batch scans candidates that concurrent requests already changed or deleted, the method can return `0` even though later expired rows still exist.

### 2.3 Pending Users Emit Downstream Policy Churn

Pending creation publishes `UserPolicyChanged(userExists=true)`. Pending deletion publishes `UserPolicyChanged(userExists=false)`. IM policy projection must process users that never completed registration.

### 2.4 Activation Has A Race At The Expiry Boundary

Verification first calls `getPendingUser`, then validates the code, then calls `activatePendingUser`. Cleanup can delete the pending row between these steps. The delete SQL is safe, but the user experience can degrade to an internal update failure.

---

## 3. Design Principles

- Unverified traffic must not write the core `user` table.
- Physical cleanup must not be required for registration correctness.
- User uniqueness must still be enforced by the user table unique indexes at final creation.
- No plaintext password may be stored in Redis, memory repositories, logs, or mail payloads.
- Cross-domain collaboration must follow repository DDD rules:
  - auth application may call user `api.action` / `api.query`;
  - controllers and jobs call same-domain application services only;
  - user domain owns final user creation and user policy event publication.
- Public registration semantics should remain close to current behavior: register returns a `registrationToken`; verify returns login credentials.

---

## 4. Target Flow

### 4.1 Register

`AuthController -> RegistrationApplicationService.register`

1. Validate request shape, captcha, and registration fields.
2. Apply IP/email/device rate limits before expensive password hashing.
3. Call user-domain registration preparation API:
   - normalize username and email;
   - validate password policy;
   - generate the final user id;
   - encode the password with BCrypt;
   - generate the default header URL.
4. Optionally call a user query API for immediate username/email availability feedback. This is advisory only.
5. Generate a six-digit registration code.
6. Store an expiring registration draft keyed by `registrationToken`.
7. Store the registration code keyed by the draft user id or registration id.
8. Queue or send the registration email.
9. Return `RegisterResult` with:
   - generated provisional user id;
   - registration token;
   - masked email;
   - debug code only when configured.

No `user` row is inserted in this flow. No `UserPolicyChanged` event is published.

The returned user id preserves the current response shape, but it is not a real user until verification succeeds. Clients must continue registration with `registrationToken`; other business domains must not treat the provisional id as an existing actor.

### 4.2 Resend Code

`AuthController -> RegistrationVerificationApplicationService.resendCode`

1. Validate captcha.
2. Resolve the registration draft from `registrationToken`.
3. Reject missing or expired drafts as invalid registration context.
4. Issue a new code using the configured cooldown.
5. Send the code to the draft email.

No user-domain pending-user lookup is needed.

### 4.3 Verify And Login

`AuthController -> RegistrationVerificationApplicationService.verifyAndLogin`

1. Resolve the registration draft from `registrationToken`.
2. Verify and consume the code by draft id.
3. If the code succeeds, call user-domain final creation API with the prepared draft material.
4. User application inserts an active `user` row with `status=1`.
5. User application translates username/email unique-key races into business errors.
6. User application publishes `UserPolicyChanged(userExists=true)` for the new real user.
7. Auth application issues login tokens from the created user credential view.
8. Auth application deletes the registration draft and code as best-effort cleanup.

If a concurrent verified registration already claimed the same username or email, final insertion returns `USER_ALREADY_EXISTS` or `EMAIL_ALREADY_EXISTS`; the user must restart registration.

---

## 5. Data And Repository Model

### 5.1 Registration Draft

Add an auth-domain repository contract, replacing and removing the current token-to-user-id-only `RegistrationSessionRepository` semantics:

```java
public interface RegistrationDraftRepository {
    String issue(PreparedRegistrationDraft draft, Duration ttl);
    Optional<PreparedRegistrationDraft> find(String registrationToken);
    void delete(String registrationToken);
}
```

`PreparedRegistrationDraft` is auth application/domain data, not an HTTP DTO:

```text
userId
username
email
encodedPassword
headerUrl
issuedAt
expiresAt
```

The Redis implementation stores one JSON value with TTL:

```text
auth:regdraft:{registrationToken} -> prepared draft JSON
```

The in-memory implementation mirrors the same semantics for tests and local development.

The old `RegistrationSessionRepository`, `RedisRegistrationSessionRepository`, and `InMemoryRegistrationSessionRepository` are deleted in this migration. No production code should keep resolving `registrationToken -> userId`; the token must resolve to the complete prepared registration draft.

### 5.2 Registration Code

The existing code repository can continue to key by generated `userId`, because the id is generated during preparation and later used as the real user id. The user id is reserved in the draft but does not exist in the `user` table until verification succeeds.

### 5.3 User Creation API

The user domain publishes a synchronous action API for final creation:

```java
UserCredentialView createVerifiedRegistrationUser(VerifiedRegistrationUserCommand command);
```

The API model carries prepared user material:

```text
userId
username
email
encodedPassword
headerUrl
```

The user application validates the command again at the application boundary, inserts `status=1`, and publishes the user policy event after successful insert.

### 5.4 User Preparation API

The user domain publishes a synchronous action API for preparing registration material:

```java
PreparedRegistrationUserView prepareRegistrationUser(String username, String password, String email);
```

This call performs no database write. It exists to keep password policy, username/email normalization, id generation, password encoding, and default header URL generation in the user owner domain.

---

## 6. Concurrency Semantics

### 6.1 Duplicate Registration Attempts

Multiple users can request codes for the same username or email. This is acceptable in Verify-First. The first verified request that inserts into `user` wins. Later verified requests fail on the unique index and receive a business duplicate error.

This keeps the hot path scalable because unverified attempts do not contend on the database unique indexes.

High-traffic deployments should skip username/email precheck or keep it explicitly advisory and rate-limited. The precheck must never become a required correctness step.

### 6.2 Duplicate Verify Submissions

Verification must be idempotent at the code layer. `verifyAndConsume` must atomically consume the code so only one request can continue to final user creation.

If retry happens after successful code consumption but before the client receives login response, the registration draft may still exist while the code is consumed. The retry returns code invalid or registration context invalid. This is acceptable for the first implementation; later hardening can add a short-lived verified-result cache keyed by registration token.

### 6.3 Final User Insert Race

Final insert relies on:

- primary key on `user.id`;
- unique key `uk_user_username`;
- unique key `uk_user_email`.

The user application translates these database races into stable business errors. No precheck is considered authoritative.

### 6.4 Expiry

Draft and code TTLs define validity. Physical cleanup is no longer a correctness mechanism. Redis TTL removes expired drafts and codes automatically; in-memory cleanup is best effort.

---

## 7. Event Strategy

The new event policy is:

- registration draft created: no user event;
- registration draft expired: no user event;
- registration code resent: no user event;
- verified user created: publish `UserPolicyChanged(userExists=true)`;
- user deletion/disable/moderation changes: keep existing user policy event behavior.

This prevents IM policy projection from processing non-users and removes `UserPolicyChanged(userExists=false)` churn caused only by abandoned registrations.

---

## 8. Cleanup Strategy

Remove pending-user cleanup from the registration correctness path:

- `PendingRegistrationUserCleanupJob` and XXL pending-user cleanup handler become migration-only or are removed after old pending rows are drained.
- Existing `user.status=0` rows should be cleaned by a one-time migration or temporary compatibility job.
- No new code should insert `user.status=0` registration rows.

If a compatibility period is needed, keep the old cleanup job enabled only until the maximum old pending TTL has elapsed after deployment.

---

## 9. Security And Abuse Controls

High traffic registration must be constrained before expensive work:

- captcha verification before password hashing;
- IP/email/device cooldown before password hashing and mail sending;
- registration code resend cooldown;
- max verification failures per draft/code;
- no plaintext password in Redis or logs;
- BCrypt hash stored in draft with the same sensitivity as the final DB password hash;
- registration draft JSON must not be logged.

Mail sending should be queue-backed or rate-limited behind `MailPort`. The HTTP request may wait for successful enqueue rather than direct SMTP delivery.

---

## 10. Migration Plan

1. Add user APIs for preparation and verified final creation.
2. Add auth registration draft repository with Redis and in-memory implementations.
3. Change register to create draft + code instead of pending user.
4. Change resend and verify to read draft instead of pending user.
5. Delete the old registration session repository interface and implementations in the same migration; do not keep a compatibility token-to-user-id session store.
6. Keep user unique-key duplicate translation on final creation.
7. Stop publishing user policy events for unverified registration.
8. Disable pending-user creation and mark old pending APIs as migration-only.
9. Keep old cleanup temporarily for already-created `status=0` rows.
10. Remove old pending APIs and cleanup jobs after compatibility window.

---

## 11. Testing Strategy

Application tests:

- register creates draft, issues code, sends mail, and does not call pending-user creation;
- resend reads draft and enforces cooldown;
- verify success consumes code, creates verified user, deletes draft, and issues login;
- verify duplicate code submission does not create two users;
- final username/email unique conflicts map to duplicate business errors;
- expired draft returns invalid registration context;
- mail failure does not leave a durable user row.

Infrastructure tests:

- Redis draft repository stores JSON with TTL and resolves malformed/missing values safely;
- in-memory draft repository expires values;
- Redis code repository remains atomic for issue and consume.
- no main or test code references `RegistrationSessionRepository` or `auth:regsession`.

Architecture tests:

- auth controllers call only auth application services;
- auth application uses user `api.action` / `api.query`, not user application or infrastructure;
- user preparation/final creation APIs expose only `api.model` command/view types.

Integration tests:

- register + verify returns login and creates one active user row;
- abandoned registration creates no user row;
- two registration drafts for the same username race at verify and only one active user is created;
- no IM policy event is emitted before verification.

---

## 12. Non-Goals

This design does not add username/email reservation during the verification window. That is an intentional throughput tradeoff. If product requirements later demand reservation, add an identity lease table as a separate design.

This design does not change login, refresh token, captcha generation, or IM message authorization except for reducing registration-related user policy events.

This design does not require a new database table for drafts when Redis is available. A DB-backed draft store can be added later if operational policy requires Redis-independent registration recovery.
