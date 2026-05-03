# Login Rate Limit Redis Cluster Optimization Design

Date: 2026-05-03

## Context

The login flow currently calls `LoginRateLimitApplicationService` before credential validation and after failed attempts. The service stores failure counters in Redis through `LoginRateLimitRepository`.

The current repository contract is low-level:

- `count(String key)` reads one counter.
- `increment(String key, int windowSeconds)` increments one counter with TTL.
- `delete(String key)` clears one counter.

For one login request, this can create multiple Redis round trips:

- `assertNotBlocked` reads the IP counter and the username counter.
- `isCaptchaRequired` reads the IP counter and the username counter again.
- `recordFailure` increments the IP counter and the username counter after a failed request.

This is too chatty for a high-concurrency, high-traffic login endpoint. Production Redis is Redis Cluster, so multi-key Lua scripts and multi-key operations must avoid cross-slot keys.

## Goals

- Reduce Redis round trips on the login hot path.
- Replace the separate block check and captcha-required check with one login precheck.
- Keep Redis Cluster compatibility without relying on cross-slot multi-key scripts.
- Preserve current fail-closed behavior.
- Preserve current security semantics for IP and username dimensions.
- Move Redis result interpretation closer to the repository boundary so application code does less repeated count handling.

## Non-Goals

- Do not remove either rate-limit dimension.
- Do not redesign login captcha UX.
- Do not change public API responses unless existing behavior already requires it.
- Do not introduce local in-memory caches for login counters in this change.

## Current Semantics To Preserve

- If login rate limiting is disabled, all rate-limit checks are skipped.
- If Redis fails during the combined login precheck, login fails closed with `SERVICE_UNAVAILABLE`.
- If Redis fails during the compatibility `isCaptchaRequired` wrapper, captcha is required.
- If Redis fails during `recordFailure`, login fails closed with `SERVICE_UNAVAILABLE`.
- A counter at or above `maxFailuresPerIp` blocks by IP.
- A counter at or above `maxFailuresPerUser` blocks by username.
- A counter at or above `captchaRequiredFailuresPerIp` requires captcha by IP.
- A counter at or above `captchaRequiredFailuresPerUser` requires captcha by username.
- A captcha threshold `<= 0` means captcha is required when the corresponding dimension exists. In the combined precheck, Redis may still be read for that dimension to preserve block-limit enforcement.
- Successful login clears both IP and username failure counters on a best-effort basis.

## Recommended Approach

Use single-key semantic repository operations backed by Lua scripts. Do not force IP and username keys into the same Redis hash slot.

The repository should expose login-rate-limit operations rather than raw counters:

```java
LoginRateLimitProbe probe(String key, int blockLimit, int captchaThreshold);

LoginRateLimitIncrement incrementAndProbe(String key, int windowSeconds, int blockLimit);

void delete(String key);
```

Suggested result shapes:

```java
record LoginRateLimitProbe(int count, boolean blocked, boolean captchaRequired) {}

record LoginRateLimitIncrement(int count, boolean blocked) {}
```

The Redis implementation should execute single-key scripts only. That keeps each operation safe in Redis Cluster regardless of whether the IP key and username key map to different slots.

The application service should expose a single pre-credential check for the login flow:

```java
LoginRateLimitPrecheckResult precheck(String username, String ip, String ipSource);
```

Suggested result shape:

```java
record LoginRateLimitPrecheckResult(boolean captchaRequired) {}
```

`LoginApplicationService` should call this once before credential validation. The precheck method throws the existing too-many-requests errors when a dimension is blocked and otherwise returns whether captcha is required. This replaces the current sequential `assertNotBlocked(...)` plus `isCaptchaRequired(...)` calls in the login hot path.

## Redis Scripts

### Probe Script

Inputs:

- `KEYS[1]`: one failure counter key.
- `ARGV[1]`: block limit, normalized to at least `1` by application code.
- `ARGV[2]`: captcha threshold. If `<= 0`, the script should return captcha-required while still evaluating the block limit for the key.

Behavior:

- Read the key once.
- Parse missing or invalid values as `0`.
- Return count, blocked flag, captcha-required flag.

Expected return can be a small list such as `[count, blocked, captchaRequired]`.

### Increment-And-Probe Script

Inputs:

- `KEYS[1]`: one failure counter key.
- `ARGV[1]`: TTL window seconds.
- `ARGV[2]`: block limit, normalized to at least `1` by application code.

Behavior:

- `INCR` the counter.
- Set `EXPIRE` only when the incremented count is `1`.
- Return incremented count and blocked flag.

This preserves the existing fixed-window behavior.

## Application Flow

### `precheck`

- Normalize username and IP with `LoginRateLimitDomainService`.
- If the IP captcha threshold is `<= 0` and IP exists, mark captcha as required for this precheck while still probing IP for block status.
- If the username captcha threshold is `<= 0` and username exists, mark captcha as required for this precheck while still probing username for block status.
- If IP exists, probe the IP key with `maxFailuresPerIp` and its captcha threshold.
- If the IP probe is blocked, record `blocked` and throw the existing IP too-many-requests error.
- If username exists, probe the username key with `maxFailuresPerUser` and its captcha threshold.
- If the username probe is blocked, record `blocked` and throw the existing username too-many-requests error.
- Record `allowed` when neither dimension is blocked.
- Return `captchaRequired = true` if either existing dimension has threshold `<= 0` or either probe reports captcha required.
- Fail closed with `SERVICE_UNAVAILABLE` on Redis failure.

### Compatibility Methods

- Existing `assertNotBlocked` and `isCaptchaRequired` may remain as thin wrappers for tests or internal callers.
- `LoginApplicationService` must not call both methods sequentially after this change.
- If wrappers remain, document that they are not the optimized hot-path API.

### `recordFailure`

- Normalize username and IP.
- For each existing dimension, call `incrementAndProbe` once.
- If IP increment result is blocked, record `blocked` and throw the existing IP too-many-requests error.
- If username increment result is blocked, record `blocked` and throw the existing username too-many-requests error.
- Otherwise record `allowed`.

### `reset`

- Continue deleting IP and username keys best-effort.
- A later optimization can pipeline deletes, but success login is not the immediate bottleneck.

## Redis Cluster Notes

- Multi-key Lua scripts are intentionally avoided.
- `MGET` is not the preferred fix because the final goal is not only combined reads; the write path also needs atomic increment plus limit evaluation.
- Hash tags are not recommended here because IP and username are independent dimensions. Forcing them into a request-level slot would weaken or complicate cross-user IP and cross-IP username semantics.

## Error Handling

- The application service keeps the same catch behavior and logging.
- Repository methods should throw on Redis execution failures or malformed script responses.
- Missing counters are treated as count `0`, not an error.
- Non-numeric counter values are treated as count `0` by the probe script to match current tolerant behavior.

## Testing

Add or update unit tests for `LoginRateLimitApplicationService`:

- `precheck` returns captcha required when a threshold `<= 0` for an existing dimension, while still enforcing block limits for that dimension.
- `precheck` returns captcha required when the IP probe requires captcha.
- `precheck` returns captcha required when the username probe requires captcha.
- `precheck` throws the IP too-many-requests error when IP probe is blocked.
- `precheck` throws the username too-many-requests error when username probe is blocked.
- `recordFailure` throws when IP increment result is blocked.
- `recordFailure` throws when username increment result is blocked.
- Existing fail-closed behavior remains covered.
- `LoginApplicationService` calls one precheck method instead of sequential `assertNotBlocked` and `isCaptchaRequired`.

Add or update unit tests for `RedisLoginRateLimitRepository`:

- `probe` uses a single-key script and parses `[count, blocked, captchaRequired]`.
- `incrementAndProbe` uses a single-key atomic script containing `incr` and `expire`.
- Null or malformed script results throw a runtime exception.

## Implementation Boundaries

- Changes remain inside the auth application and auth infrastructure persistence layers.
- Controllers keep calling `AuthApplicationService` only.
- Domain services remain free of Redis and Spring dependencies.
- The repository interface stays in `auth.domain.repository`; Redis scripts stay in `auth.infrastructure.persistence`.

## Rollout

- Keep existing Redis key names:
  - `auth:login:fail:ip:{ip}`
  - `auth:login:fail:user:{username}`
- Existing counters continue to work because the value remains a plain integer.
- No Redis data migration is required.

## Success Criteria

- Each existing-dimension precheck uses one semantic Redis operation instead of raw count interpretation in application code.
- The login flow performs one combined precheck phase instead of separate block and captcha-required phases.
- Failed-login writes use one semantic Redis operation per existing dimension.
- Threshold `<= 0` captcha policy avoids extra captcha-only Redis reads while preserving block-limit probes.
- Redis Cluster compatibility is preserved.
- Existing fail-closed behavior is preserved.
