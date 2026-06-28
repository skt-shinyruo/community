# Auth Business Line Handbook Semantic Closure Design

## Background

This design covers the `auth` business line in `backend/community-app` only. The focus is not feature expansion; it is semantic closure: bringing the current implementation back into alignment with the handbook and the repository's strict DDD Tactical Layering.

The design is based on:

- `AGENTS.md` at the repository root
- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/workflows/auth-registration-login.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/core-logic-index.md`
- `docs/handbook/security.md`

The current audit found five concrete gaps in the `auth` line:

1. refresh rotation is modeled as direct one-way consumption, so transient failures can destroy the caller's session instead of preserving the documented lifecycle.
2. logout can fail to revoke a live refresh family when the caller presents an already-rotated refresh token.
3. verify-first registration resend can overwrite the active verification code before mail delivery is known to have succeeded.
4. password reset request still exposes a public response DTO with `resetLink`, even though the handbook says the HTTP contract must not return the link.
5. the DB refresh session cross-domain adapter sits in `auth.application`, which violates the repository's DDD layering rules.

This package fixes those problems together because they share one root cause: the runtime state machines, the published handbook semantics, and the code/package boundaries are no longer describing the same system.

## Goals

- Restore handbook-authored semantics for verify-first registration, login session issuance, refresh rotation, logout, captcha/risk cooperation, password reset, and refresh cookie lifecycle.
- Preserve domain ownership: `auth` owns session issuance and refresh strategy; `user` owns account facts, password hash, and refresh session storage facts.
- Replace destructive refresh consumption with a recoverable two-phase lifecycle that can survive transient dependency failures without corrupting session state.
- Make logout family revocation work from either an active refresh token or a recognized revoked-token tombstone.
- Ensure registration resend does not advance verification state unless the replacement code is deliverable.
- Remove public password reset response affordances that contradict the documented security contract.
- Move cross-domain outbound adapter implementations back into `auth.infrastructure` so package boundaries match the DDD rules.
- Add or update tests so the corrected semantics are enforced at unit, integration, and architecture levels.

## Non-goals

- Do not change domain ownership between `auth` and `user`.
- Do not replace JWT access tokens with centralized introspection or a global access-token blacklist.
- Do not redesign captcha UX, login thresholds, or user password rules beyond what is needed to match the documented semantics.
- Do not rework unrelated `user`, `content`, `market`, or `wallet` business logic.
- Do not introduce a generic saga framework or a new platform-wide session abstraction.

## Selected Approach

Use a semantic-alignment refactor.

This approach keeps the current domain ownership model, but rewrites the broken state transitions so the implementation matches the handbook. It is broader than a hotfix and intentionally narrower than a domain re-ownership project.

This is the correct scope for three reasons:

- the current defects are not isolated null-handling bugs; they come from mismatched lifecycle design;
- the handbook already states the target behavior, so the work is convergence rather than invention;
- package-boundary cleanup is part of the same correction and should ship with the lifecycle fixes instead of being deferred.

## Global Constraints

- All backend business code in `backend/community-app` must continue to satisfy the repository's strict DDD Tactical Layering from `AGENTS.md`.
- Inbound adapters may only call same-domain `*ApplicationService` entry points.
- `auth` application code may call foreign owner-domain `user.api.query` and `user.api.action`, but may not reach into foreign repositories, mappers, or application internals.
- `user` remains the owner of DB refresh session storage facts; `auth` remains the owner of refresh token strategy.
- Any architecture boundary or package movement that affects backend guardrails must keep the existing ArchUnit suite passing.

## Owner Boundaries

### Auth owner

`auth` continues to own:

- login, refresh, logout, registration draft flow, registration verification code flow, captcha, password reset token flow, and login risk orchestration;
- access token claim assembly and signing;
- refresh token family strategy, cookie contract, reuse detection, and family-level failure semantics.

`auth` application services remain the same business entry style:

- `LoginApplicationService`
- `RegistrationApplicationService`
- `RegistrationVerificationApplicationService`
- `CaptchaApplicationService`
- `PasswordResetApplicationService`
- `RefreshTokenApplicationService`
- `LoginRateLimitApplicationService`

### User owner

`user` continues to own:

- account facts and availability for login/refresh;
- password hash and password-update side effects;
- DB refresh session persistence facts;
- owner APIs used by `auth` to read or mutate those facts.

No part of this design moves refresh token strategy into `user`. The owner relationship remains:

```text
auth ApplicationService
  -> user.api.query / user.api.action
  -> user owner application
  -> user domain / repository
```

## Target Semantic Closure

The fixed system must satisfy these externally visible rules.

### Refresh

- The documented business order remains: consume old token, validate current user state, issue new access token, issue a new refresh token in the same family.
- A transient infrastructure failure must no longer strand the caller between old-token destruction and new-token issuance.
- `REFRESH_TOKEN_INVALID` is reserved for truly invalid or no-longer-acceptable refresh tokens, not for internal write failures.
- If the system cannot safely restore an in-flight refresh state, it must fail closed by revoking the family and clearing the cookie.

### Logout

- Logout must revoke the current token or the whole family, even when the caller presents a recently rotated old token that has already left the active set.
- Clear-cookie behavior remains controller-owned.

### Verify-first resend

- Mail delivery failure must not replace a still-valid registration code with an undeliverable new code.
- The replacement code becomes effective only when delivery succeeds.

### Password reset request contract

- The public HTTP response no longer exposes `resetLink`.
- The reset link is generated and used internally for mail delivery only.

### DDD layering

- Outbound adapters that call `user.api.*` must live in `auth.infrastructure`, not `auth.application`.

## Refresh Rotation Design

### Problem statement

The current implementation directly consumes the old refresh token before user-state validation and new-token persistence are complete. That means a failure after consumption can irreversibly destroy the only valid session credential even when the failure is transient.

This violates the handbook's session-lifecycle expectations and produces inconsistent controller behavior:

- some transient failures get mapped to `REFRESH_TOKEN_INVALID` and clear the cookie;
- others leave the cookie in the browser even though the token has already become unusable.

### Design summary

Refresh rotation becomes a two-phase state transition coordinated by `auth` and persisted by the `user` owner.

Lifecycle:

```text
ACTIVE
  -> PENDING_ROTATION
      -> CONSUMED     + replacement token ACTIVE
      -> ACTIVE       + no replacement token issued (rollback)
```

The `user` owner API surface is extended with concrete action methods so `auth` can drive those transitions without bypassing the owner boundary:

- `UserRefreshTokenSessionActionApi.beginRotation(String tokenHash, Instant pendingExpiresAt)`
- `UserRefreshTokenSessionActionApi.finishRotation(String pendingTokenHash, String replacementTokenHash, UUID userId, String familyId, Instant replacementExpiresAt)`
- `UserRefreshTokenSessionActionApi.rollbackPendingRotation(String pendingTokenHash)`

The existing `UserRefreshTokenSessionQueryApi.find(String tokenHash)` remains the read path used to inspect active or terminally revoked records.

`beginRotation(...)` must only transition `ACTIVE -> PENDING_ROTATION` and must return the pending session facts needed by `auth`. `finishRotation(...)` must atomically store the replacement active token and move the pending token to terminal `CONSUMED`. `rollbackPendingRotation(...)` must move `PENDING_ROTATION -> ACTIVE`.

### Detailed flow

The corrected `refresh` path becomes:

```text
AuthController.refresh
  -> LoginApplicationService.refresh
      -> RefreshTokenApplicationService.beginRotation(refreshToken)
          -> auth RefreshTokenRepository
          -> user owner refresh-session action API
      -> UserCredentialQueryApi.getByUserId(userId)
      -> validate loginAllowed / refreshAllowed / existence
      -> LoginTokenIssuer.issueAccessToken(...)
      -> RefreshTokenApplicationService.generateReplacementToken(...)
      -> RefreshTokenApplicationService.finishRotation(...)
```

`PENDING_ROTATION` uses a fixed 30-second owner-stored lease via `pendingExpiresAt`. If the process crashes after `beginRotation(...)` and before either `finishRotation(...)` or `rollbackPendingRotation(...)`, the next `beginRotation(...)` call for that token must first auto-recover an expired pending record back to `ACTIVE` and then retry the transition.

`generateReplacementToken(...)` remains auth-owned. It generates the new opaque refresh token plaintext, derives its hash in `auth`, and passes the replacement facts into `finishRotation(...)` so `user` stores only refresh-session facts, not strategy.

If any step after `beginRotation(...)` fails:

- first choice: roll the old token back to `ACTIVE`;
- if rollback succeeds, return a dependency failure without clearing the cookie;
- if rollback fails or state is no longer trustworthy, revoke the family and clear the cookie.

### Failure semantics

`REFRESH_TOKEN_INVALID` remains for:

- missing or blank cookie;
- token not found;
- token already terminally revoked and not acceptable under the reuse rules;
- token family already revoked;
- expired token;
- replay outside the configured grace window.

`USER_DISABLED` remains for:

- user no longer exists;
- `loginAllowed=false`;
- `refreshAllowed=false`.

`SERVICE_UNAVAILABLE` becomes the expected error for:

- user-owner lookup failure;
- replacement refresh-session persistence failure;
- state-transition persistence failure when the old token has been successfully restored.

If a refresh attempt reaches an unrecoverable in-between state, `auth` must revoke the family and treat the browser credential as lost. That is fail-closed, but it is explicit and deterministic rather than accidental.

Controller contract rule: refresh-cookie clearing must no longer be inferred only from the top-level error code. `LoginApplicationService.refresh(...)` must surface a failure outcome that carries both the business error and a `clearRefreshCookie` decision. `REFRESH_TOKEN_INVALID` and `USER_DISABLED` always clear; `SERVICE_UNAVAILABLE` clears only when the refresh family was explicitly revoked during fail-closed recovery.

### Reuse detection

The existing family reuse policy stays in `auth`, but it must operate only on terminal revoked/consumed tokens, not on `PENDING_ROTATION` tokens.

That means the storage model must distinguish:

- temporarily in-flight rotation state;
- terminal revoked/consumed tombstone used for replay detection.

The grace-window policy documented in the handbook still applies after a token has reached its terminal revoked state.

## Logout Design

### Problem statement

Current logout resolves family revocation only from an active refresh token lookup. If the caller presents a token that has already been rotated away, the active lookup returns nothing and family revocation is skipped.

### Design summary

Logout must resolve family identity from either:

- the active refresh session record; or
- the revoked-token tombstone kept for replay detection.

### Detailed flow

The corrected logout path becomes:

```text
AuthController.logout
  -> LoginApplicationService.logout
      -> RefreshTokenApplicationService.revokeFamilyByPresentedToken(refreshToken)
          -> find family from active record or revoked tombstone
          -> revoke the specific token if still active
          -> revoke the whole family when familyId is known
  -> controller writes clear cookie
```

Behavior rules:

- no refresh token: best-effort no-op plus clear cookie;
- active token present: revoke token and family;
- revoked-but-recognized token present: revoke family;
- unrecognized token present: no backend mutation, but still clear cookie.

This preserves handbook logout semantics without requiring the presented token to still be in the active set.

## Verify-First Resend Design

### Problem statement

The current resend flow issues the replacement code into storage before mail delivery succeeds. If mail sending fails, the old code may already have been invalidated and the user is left with no deliverable valid code.

### Design summary

Resend becomes a two-phase replacement. A newly generated code must not become the authoritative active code until delivery succeeds.

Lifecycle:

```text
ACTIVE(old code)
  -> PENDING_REPLACEMENT(new code candidate)
      -> ACTIVE(new code)   when mail succeeds
      -> ACTIVE(old code)   when mail fails
```

### Detailed flow

Corrected resend path:

```text
AuthController.resendRegisterCode
  -> RegistrationVerificationApplicationService.resendCode
      -> captcha validation
      -> resolve registration draft
      -> RegistrationCodeRepository.beginReplacement(...)
      -> MailPort.sendRegistrationCodeMail(...)
      -> RegistrationCodeRepository.promoteReplacement(...)
```

On mail failure:

- `RegistrationCodeRepository.abortReplacement(...)` restores the original active code;
- cooldown and failure counters must remain coherent with the pre-resend active code;
- the caller receives the send failure and can retry.

Repository contract requirement: `beginReplacement(...)` must preserve the current active code and write the candidate code only into pending replacement fields. Verification continues to accept the old active code until `promoteReplacement(...)` succeeds.

### Verification flow interactions

The existing verify-first pending-consume model remains valid:

- `ACTIVE -> PENDING_CONSUMPTION -> CONSUMED` for final user creation;
- failed user creation restores `PENDING_CONSUMPTION -> ACTIVE`.

The resend replacement state and the verify pending-consumption state must not be conflated. They represent different business transitions and need distinct repository semantics.

## Password Reset Request Contract Design

### Problem statement

The runtime currently returns an empty `resetLink`, but the public DTO and controller contract still expose the field. That leaves an unnecessary contract surface that contradicts the handbook and can regress into a leak later.

### Design summary

The public reset-request response becomes a narrow acknowledgement contract with no reset link field.

Recommended shape:

```text
PasswordResetRequestResponse {
  issued: boolean
}
```

The application-layer result should be reduced accordingly so the controller no longer even has a public field to map. The reset link remains an internal variable used only to compose and send mail.

### Security implication

This change removes an unnecessary public capability rather than changing visible user behavior. The user-facing semantics remain:

- accepted response for existing and non-existing users;
- no reset link in the response body;
- reset link delivered only by email.

## Captcha, Risk, and Cookie Lifetimes

The audit focus for this package is not to redesign captcha or login rate limiting, but the corrected flows must continue to respect the handbook rules:

- captcha and login risk remain fail-closed when their backing store is unavailable;
- refresh cookie clear behavior remains controller-owned;
- refresh cookie attributes continue to come from `security.jwt.refresh-cookie-*`;
- no change is introduced to access-token TTL, refresh-token TTL, or OriginGuard coverage as part of this package.

## Package and Boundary Design

### Move outbound adapter to infrastructure

`RefreshTokenSessionApplicationPortAdapter` is an outbound adapter from `auth` to `user` owner APIs. It should be moved from:

```text
com.nowcoder.community.auth.application
```

to:

```text
com.nowcoder.community.auth.infrastructure.api
```

Rationale:

- `auth.application` should own orchestration and ports, not adapter implementations;
- the adapter depends on foreign owner-domain APIs and is therefore infrastructure from `auth`'s perspective;
- this move aligns code structure with the repository's DDD rules and existing architecture guardrails.

### Keep application entry style stable

Do not introduce new `FacadeService`, `UseCase`, or `CommandService` entry styles. Same-domain business entry points remain `*ApplicationService` classes under `auth.application`.

## Required Owner API Evolution

This package requires targeted expansion of the `user` owner refresh-session API surface with the following concrete methods:

- `UserRefreshTokenSessionActionApi.beginRotation(String tokenHash, Instant pendingExpiresAt)`
- `UserRefreshTokenSessionActionApi.finishRotation(String pendingTokenHash, String replacementTokenHash, UUID userId, String familyId, Instant replacementExpiresAt)`
- `UserRefreshTokenSessionActionApi.rollbackPendingRotation(String pendingTokenHash)`

The existing `UserRefreshTokenSessionQueryApi.find(String tokenHash)` remains the single lookup method used by `auth` to recover family identity from either an active or terminally revoked session record.

These are still owner-domain APIs. `auth` must not bypass them by reaching into `user` persistence types.

## Data and Persistence Implications

### DB-backed refresh store

The current DB refresh-session model in `user` must be extended so it can represent an in-flight rotation state in addition to terminal revocation.

The DB schema is extended explicitly:

- add `state` to `auth_refresh_token` with values `ACTIVE`, `PENDING_ROTATION`, `CONSUMED`, `REVOKED`;
- add nullable `pending_expires_at` to `auth_refresh_token`;
- keep `revoked_at` for terminal `CONSUMED` and `REVOKED` rows only.

Required semantics:

- the old token must not become terminal until replacement storage succeeds;
- the system must be able to roll back or fail closed deterministically;
- an expired `PENDING_ROTATION` row must be restorable to `ACTIVE` by `beginRotation(...)` before a retry proceeds;
- replay detection must still have tombstone information after terminal revocation.

### Redis-backed refresh store

The optional Redis refresh-token repository must also preserve those semantics atomically.

Required properties:

- one Lua-scripted transition per critical mutation;
- no `get`/`delete` split that can lose tombstone information on crash;
- no replay blind spot between consumption and tombstone creation.

### Registration code store

The registration-code repository must represent both:

- resend replacement lifecycle; and
- verification pending-consumption lifecycle.

The record format is extended explicitly so one logical registration-code record carries:

- active verification code fields;
- optional pending replacement code fields;
- verification consumption state fields for `ACTIVE` and `PENDING_CONSUMPTION`;
- existing failure-count and cooldown metadata.

Verification reads the active code fields only. Resend writes pending replacement fields first, promotes on mail success, and aborts by clearing the pending replacement fields on mail failure.

## Testing Strategy

### Auth application tests

Add or update unit tests for:

- refresh success across begin-rotation, replacement issuance, and completion;
- refresh user-disabled path with family revocation and clear-cookie behavior;
- refresh user-owner lookup failure with successful rollback and `SERVICE_UNAVAILABLE`;
- refresh replacement-store failure with successful rollback and `SERVICE_UNAVAILABLE`;
- refresh rollback failure leading to family revocation and cookie clear;
- logout with active token;
- logout with a rotated old token that is only resolvable through a tombstone;
- resend mail failure preserving the previously active code;
- password reset request response contract no longer containing `resetLink`.

### User owner tests

Add or update unit/integration tests for:

- begin/complete/rollback rotation state transitions;
- expired pending-rotation recovery on retry;
- family revoke after irrecoverable refresh failure;
- family resolution from both active and revoked records;
- concurrent refresh attempts against the same token;
- Redis and DB stores preserving replay-detection tombstones.

### Controller and contract tests

Add or update controller/DTO tests for:

- password reset request response shape without `resetLink`;
- refresh clear-cookie behavior for terminal token failure and user-disabled failure;
- logout always clearing the browser cookie.

### Architecture tests

Because package boundaries are changing, run the backend architecture guardrails after implementation:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

If the move into `auth.infrastructure` reveals a missing guardrail, extend the relevant ArchUnit suite under:

```text
backend/community-app/src/test/java/com/nowcoder/community/app/arch
```

## Documentation Updates

Keep the following documents aligned with the implemented behavior:

- `docs/handbook/business-logic/auth.md`
- `docs/handbook/business-logic/workflows/auth-registration-login.md`
- `docs/handbook/auth-login-session-flow.md`
- `docs/handbook/core-logic-index.md`

Documentation changes required by this package:

- refresh implementation now explicitly uses a recoverable two-phase rotation model while preserving the same external handbook sequence;
- logout may revoke family based on active session or revoked tombstone lookup;
- registration resend guarantees no state advancement on mail failure;
- password reset request response no longer includes `resetLink`.

## Rollout and Migration Notes

Implementation should be sequenced so that owner API and persistence support land before `auth` starts depending on the new semantics.

Recommended rollout order:

1. extend `user` owner session APIs and persistence state model;
2. update DB and optional Redis repositories to support begin/complete/rollback semantics;
3. switch `auth` refresh/logout orchestration to the new owner API;
4. update registration resend repository semantics;
5. narrow password reset request DTO/result contract;
6. move outbound adapter package and fix architecture tests;
7. update handbook and run full targeted regression.

During rollout, do not ship an `auth` refresh caller that expects rollback semantics before the `user` owner store can actually provide them.

## Risks

- refresh state-machine changes touch both `auth` and `user`, so partial rollout can create behavioral mismatches;
- DB and Redis optional stores must agree on business semantics or test coverage will drift;
- replay detection can regress silently if tombstone handling is not verified under concurrency and failure injection;
- narrowing the password reset response contract may require coordinated frontend cleanup if any code still reads the legacy field.

## Success Criteria

This package is complete when all of the following are true:

- refresh no longer destroys a valid browser session on transient post-consumption failures unless the system explicitly chooses fail-closed family revocation;
- logout can revoke a still-live family when presented with either the current token or a recently rotated old token;
- registration resend does not invalidate the last deliverable code on mail failure;
- password reset request public response contract no longer exposes `resetLink`;
- the refresh-session outbound adapter lives in `auth.infrastructure`;
- handbook docs, automated tests, and architecture guardrails all describe the same corrected behavior.
