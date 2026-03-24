# Auth Hardening (Refresh + Registration) Spec

**Date:** 2026-03-24

## Background / Problems

This repo uses **JWT access token (in response body)** + **HttpOnly refresh cookie** (`/api/auth/refresh`) as the session model.

Current issues discovered from code review:

1. **Random logout / session loss in concurrent refresh scenarios**
   - `POST /api/auth/refresh` clears the refresh cookie when it throws `AuthErrorCode.REFRESH_TOKEN_INVALID`.
   - With refresh token rotation, concurrent refresh requests (multi-tab / multi-request) cause one request to succeed (rotates) and another to fail (invalid), and the failing response may clear the cookie set by the successful response.

2. **Registration code verification can be anonymously abused**
   - `/api/auth/register/code/verify` and `/api/auth/register/code/resend` are anonymous.
   - The API uses `userId` as the primary handle, making targeted abuse feasible if `userId` is guessable.
   - The registration email code store enforces max-failure invalidation; an attacker can intentionally burn attempts.

3. **Refresh token reuse detection is weak for DB store**
   - Rotation exists (consume old token, issue new), but we do not currently detect or respond to reused tokens in a way that revokes the token family.
   - We must avoid false positives for legitimate near-simultaneous refresh requests.

4. **DB refresh token table has no cleanup**
   - `auth_refresh_token` records accumulate over time without periodic deletion of expired entries.

## Goals

1. Prevent refresh-cookie clearing from causing random logout during concurrent refresh.
2. Make registration resend/verify un-targetable by guessing `userId` by introducing an opaque registration context token.
3. Add refresh token reuse detection for DB store with a configurable grace window to avoid concurrency false positives.
4. Add a periodic cleanup job for expired DB refresh tokens.

## Non-Goals

- Replacing the auth model (OIDC/SSO) or introducing full CSRF token flows.
- Reworking login rate limiting semantics (fail-open vs fail-closed).
- Major changes to error code taxonomy beyond what is required for the above fixes.

## API Contract Changes

### Register

`POST /api/auth/register` response (`RegisterResponse`) adds:
- `registrationToken: string` (opaque token; UUID-ish 32 chars recommended)

### Resend registration code

`POST /api/auth/register/code/resend` request changes from:
- `userId` -> `registrationToken`

### Verify registration code

`POST /api/auth/register/code/verify` request changes from:
- `userId` -> `registrationToken`

Backend should continue to accept/return existing fields unless explicitly removed.

## Acceptance Criteria

### A. Refresh cookie clearing
- When `POST /api/auth/refresh` fails with `AuthErrorCode.REFRESH_TOKEN_INVALID`, response must **NOT** set a `Set-Cookie` header that clears the refresh cookie.
- When `POST /api/auth/refresh` fails with `AuthErrorCode.USER_DISABLED`, response may clear the refresh cookie (keeps current behavior).
- Update unit tests accordingly.

### B. Registration context token
- `registrationToken` is issued at successful registration and stored server-side with TTL (use pending-user TTL as default).
- Resend/verify endpoints must resolve `registrationToken -> userId`; if missing/expired, return `UserErrorCode.USER_NOT_FOUND (11001)` to trigger frontend flow reset.
- An attacker without the `registrationToken` cannot target another pending registration by guessing `userId`.
- Update backend unit tests and frontend flow + tests.

### C. Refresh token reuse detection (DB store)
- For DB store only, when a refresh token is presented that is already revoked (consumed) and the revocation time is **outside** the configured grace window, the server must revoke the entire token family (`revokeFamily(familyId)`).
- If the token was revoked **within** the grace window, do not revoke the family (treat as benign concurrency).
- Provide property `security.jwt.refresh-reuse-grace-seconds` (default 10 seconds).
- Add unit tests for the new behavior.

### D. Refresh token DB cleanup
- Add a scheduled job that periodically deletes expired entries from `auth_refresh_token`.
- Configurable via `auth.refresh.cleanup.enabled` (default true) and `auth.refresh.cleanup.interval-ms` (default 3600000).
- Add unit test that verifies the job calls the cleanup logic and does not throw.

