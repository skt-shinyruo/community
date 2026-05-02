# Community Consistency-First Hardening Design

## Context

The current implementation has security, financial consistency, social graph, content cleanup, and frontend contract issues across `auth`, `user`, `content`, `social`, `growth`, `wallet`, `market`, `notice`, `im`, and `frontend`.

This project will apply a breaking, consistency-first repair. There is no production historical data to preserve, so the implementation does not need compatibility adapters or historical repair jobs. The goal is to make the current behavior safe and internally consistent rather than preserve old response shapes or unsafe defaults.

## Scope

Fix all reviewed issues in one coordinated change set:

- Password reset link exposure and logging.
- Password reset session invalidation.
- Refresh-token reuse behavior parity across DB and Redis stores.
- Weak and inconsistent password policy.
- Public profile wallet data leakage.
- Stale likes after content deletion and deleted-content unlike failure.
- Block/follow relationship inconsistency.
- Comment deletion count and score inconsistency, including deleted parent comments.
- Repeat like/unlike growth task farming.
- Market wallet release/refund stuck states caused by recipient wallet status.
- Market order amount overflow.
- Notice mark-read UUID mismatch.
- User profile private-message entry behavior.
- Market address default field binding ambiguity.
- Wallet transfer target validation UX.

## Non-Goals

- Preserve old unsafe response fields or request names.
- Add backward-compatible DTO aliases.
- Build historical data repair tooling.
- Redesign unrelated UI surfaces.
- Convert unrelated legacy packages unless directly touched by these fixes.

## Architecture

All backend changes must follow the repository's DDD tactical layering rules:

- Controllers keep HTTP binding, validation, authentication extraction, and DTO conversion only.
- Application services own orchestration, transactions, idempotency, domain calls, and cross-domain API calls.
- Domain services own business rules.
- Infrastructure owns MyBatis, Redis, and event implementation details.
- Same-domain callers must use same-domain application services, not same-domain `api.*`.

Breaking API changes are allowed where they remove ambiguity or unsafe behavior.

## Auth And User Design

Password reset will no longer expose reset links through API responses or logs. `auth.password-reset.expose-reset-link` will be removed or default to false with no production path that returns a reset link. The disabled-mail adapter will log only recipient metadata and a masked token marker, not the usable URL.

Password reset confirmation will revoke every refresh session for the user after the password is changed. The user domain will publish a same-domain application capability for user-level refresh-session revocation, and auth will call it through the user owner-domain API because refresh sessions are owned by user persistence.

Password policy will be centralized and reused by registration, password reset, and credential update. The policy will enforce a bounded length and minimum strength. The implementation may use a pragmatic rule: minimum 8 characters, maximum existing `ValidationLimits.PASSWORD_MAX`, and at least two of lowercase, uppercase, digit, symbol. This is intentionally breaking for weak passwords.

Public profile responses will no longer include wallet data. A private/self profile or existing wallet endpoint will be used for wallet balance and wallet status. The public `GET /api/users/{userId}` remains public but returns only non-sensitive fields and social summary fields.

Refresh-token Redis storage will retain revoked/tombstone metadata until original expiry, allowing reuse detection equivalent to DB storage. Reuse outside the configured grace window revokes the family.

## Content And Social Design

Likes become relationship records independent of current content visibility for removal. Creating a like still requires the target to be active and not blocked. Removing a like uses the existing like relation, not a fresh content lookup, so users can clean up stale relationships.

Deleting a post or comment will clean associated social like records and update denormalized like counters. Since there is no historical data, only new behavior needs to be correct. The repository will expose explicit deletion operations by entity type and entity id.

Block will become a stronger isolation action. Blocking a user will remove both follow directions between blocker and blocked. Follow queries and counts will also filter blocked relations, so direct DB inconsistencies do not leak into user-facing lists.

Comment deletion will use one consistent policy: deleting a comment deletes its active descendant replies. The post `comment_count` will decrease by the number of active comments changed from visible to deleted. The post score refresh queue will be scheduled after deletion.

Moderation delete paths and author delete paths must both go through an application service that applies the same comment-count, reply, event, and score side effects.

Growth like events will use deterministic source event ids based on the like relationship: `like-created:{actorUserId}:{entityType}:{entityId}`. Re-liking the same entity after unlike will not advance the same receive-like task again for the same period/task idempotency key.

## Wallet And Market Design

Wallet account status will distinguish outgoing user-initiated spending from inbound or compensating system actions. Frozen wallets cannot initiate transfers, withdrawals, or market purchases. Frozen wallets can still receive refunds, releases, rewards, and administrative adjustments when the system must settle already-created obligations.

Market wallet actions remain saga-driven. Release and refund actions must not fail permanently because the recipient wallet is frozen. If a non-business transient error occurs, actions stay retryable. If a business validation error occurs, the order must retain an explicit recoverable failure state rather than being silently stuck.

Market order total amount will be calculated with overflow protection. Quantity and unit price will have explicit upper bounds. Any overflow or amount above the configured maximum is rejected before order creation or stock reservation.

## Frontend And API Contract Design

Notice mark-read will submit UUID strings as `ids`, matching the backend `List<UUID>` contract.

The user profile private-message action will navigate to a concrete canonical conversation route using the current user's UUID and target user's UUID. The conversation detail page already understands `uuid_uuid` ids.

Market address DTOs will use an unambiguous field name such as `defaultAddress` on both frontend and backend. Old `isDefault` JSON input is not preserved.

Wallet transfer target input will validate UUID format before sending. A later username-picker feature is out of scope.

## Error Handling

Security-sensitive flows will avoid distinguishable responses where possible:

- Password reset request keeps the anti-enumeration behavior for unknown or inactive emails.
- Registration may still reject duplicate username/email, but password quality errors are explicit.
- Reset token values and refresh token values are never logged.

Financial flows will prefer explicit recoverable state over silent no-op:

- Wallet action failures record reason and status.
- Orders that cannot settle due to unexpected business errors remain visible for recovery.

## Testing

Add or update tests for:

- Password reset response/log safety and user session revocation.
- Shared password policy for registration and reset.
- Public profile excluding wallet fields.
- Redis refresh-token reuse detection behavior.
- Unlike after content deletion.
- Post/comment deletion clearing likes and fixing counts.
- Blocking removing follows and filtering counts/lists.
- Deterministic growth like event idempotency.
- Market release/refund to frozen recipients.
- Market amount overflow rejection.
- Notice UUID mark-read frontend behavior.
- Profile private-message route behavior.
- Market address `defaultAddress` contract.
- Wallet transfer UUID validation.

Verification commands should include focused backend tests first, then the full relevant Maven module test suite if feasible, then frontend Vitest/build.

## Acceptance Criteria

- No password reset endpoint or disabled mail path exposes usable reset links or tokens.
- Resetting a password invalidates existing refresh sessions.
- Public user profiles do not contain wallet balance or wallet status.
- Deleted content does not leave user-visible stale likes, stale comment counts, or stale heat scores.
- Blocking users removes and hides follow relations in both directions.
- Repeat like/unlike cannot farm the same growth receive-like task.
- Market refunds/releases can settle to frozen recipients when the obligation already exists.
- Market amount overflow is rejected before state mutation.
- Frontend notice, private-message, address-default, and transfer validation flows match backend contracts.
- New and updated tests pass.
