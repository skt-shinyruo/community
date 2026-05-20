# Community Social Controller Boundary Validation Design

## Summary

Move social follow and like business validation out of HTTP controllers and into
the social application/domain boundary. The fix removes duplicated
`entityType` rule checks from `FollowController` and `LikeController`, makes
application service behavior explicit for unsupported entity types, and
replaces silent batch UUID filtering with a clear request contract.

The external endpoint paths stay unchanged.

## Context

The repository mandates strict DDD tactical layering for backend business code:
controllers bind HTTP input, extract authentication, hand off validation, and
convert DTOs. They must enter the same-domain `*ApplicationService` and avoid
business collaboration before that boundary.

The current social controllers violate that spirit in several places:

- `FollowController` rejects non-`USER` follow/unfollow/status requests before
  entering `FollowApplicationService`.
- `FollowDomainService` already owns the write-side rule that follow and
  unfollow only support user relations.
- `FollowApplicationService` query methods currently return `false`, `0`, or
  empty lists for non-`USER` entity types, while some controller methods reject
  the same inputs with `INVALID_ARGUMENT`.
- `LikeController` validates `EntityTypes.isValid(...)` before entering
  `LikeApplicationService`.
- `LikeController.parseEntityIds(...)` silently ignores invalid UUID tokens,
  deduplicates IDs, and truncates to a limit. That makes malformed request
  behavior a controller-private policy instead of an application contract.

This creates two risks:

- The same rule can diverge between controller, application, and domain code.
- Invalid input behavior is inconsistent across HTTP, same-domain application
  calls, and published owner APIs.

## Goals

- Keep controllers limited to HTTP binding, authentication extraction, and
  DTO/result conversion.
- Make follow entity-type behavior consistent across write and read use cases.
- Make like entity-type support explicit at the application/domain boundary.
- Make batch like ID handling explicit and testable.
- Preserve endpoint paths and normal frontend behavior.
- Add focused tests so future changes do not reintroduce controller-owned
  business rules for these paths.

## Non-Goals

- Do not change database schema, Redis keys, Kafka contracts, or event payloads.
- Do not change public endpoint paths.
- Do not add new social features.
- Do not split `community-app` or change domain ownership.
- Do not rewrite all controller boundary guardrails beyond the focused social
  validation problem.

## Target Behavior

### Follow

Follow relations support `EntityTypes.USER` only.

All `FollowApplicationService` entry points that receive `entityType` must
apply the same contract:

- `follow(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `unfollow(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `hasFollowed(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `followeeCount(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `followerCount(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `listFollowees(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.
- `listFollowers(...)`: non-`USER` rejects with `BusinessException(INVALID_ARGUMENT)`.

Defaulting absent optional HTTP `entityType` to `USER` is HTTP request
normalization and may remain in the controller. Deciding whether any supplied
entity type is supported must happen after entering `FollowApplicationService`.

### Like

Like relations support `EntityTypes.USER`, `EntityTypes.POST`, and
`EntityTypes.COMMENT`.

All `LikeApplicationService` entry points that receive `entityType` must reject
unsupported values with `BusinessException(INVALID_ARGUMENT)` before repository
calls or foreign owner API calls:

- `setLike(...)`
- `isLiked(...)`
- `count(...)`
- `counts(...)`
- `statuses(...)`
- `cleanupEntityLikes(...)`

`LikeDomainService.validateLike(...)` should validate actor, entity ID, and the
supported like entity-type set for write use cases. Query-only validation can
reuse a small application-level helper if that avoids pretending all query
checks are domain behavior.

### Batch Like IDs

The HTTP contract for `/api/likes/counts` and `/api/likes/statuses` should be
strict:

- Invalid UUID syntax returns `INVALID_ARGUMENT`.
- Empty or missing `entityIds` produces an empty result map only when binding
  succeeds and the normalized list is empty.
- Duplicate IDs are deduplicated while preserving first-seen order.
- More than 200 IDs rejects with `BusinessException(INVALID_ARGUMENT)` instead
  of silently truncating.

Prefer Spring MVC binding with `@RequestParam List<UUID> entityIds` so invalid
UUIDs are handled by the shared `GlobalExceptionHandler` request-parameter
path. If existing clients require comma-separated `entityIds`, keep accepting
comma-separated values but move parsing into an application command/result
helper or a controller DTO converter that throws on invalid UUIDs and does not
silently filter values.

The current frontend sends `ids.join(',')`; the implementation must preserve
that normal path.

## Design

### Controller Changes

`FollowController`:

- Remove imports and direct uses of `BusinessException` and
  `CommonErrorCode.INVALID_ARGUMENT`.
- Remove `entityType != USER` checks from `follow`, `unfollow`, and `status`.
- Remove `EntityTypes.isValid(...)` checks from follow list/count endpoints.
- Keep authentication extraction via `CurrentUser.requireUserUuid(...)`.
- Keep DTO conversion to `FollowCommand`, `UnfollowCommand`, and
  `FollowItem`.
- Keep defaulting optional query `entityType` to `USER` as request
  normalization.

`LikeController`:

- Remove imports and direct uses of `BusinessException`,
  `CommonErrorCode.INVALID_ARGUMENT`, and `EntityTypes`.
- Remove `EntityTypes.isValid(...)` checks from all endpoints.
- Keep authentication extraction and `SetLikeCommand` construction.
- Replace `parseEntityIds(...)` with strict request normalization. The preferred
  implementation is an HTTP binding method accepting `List<UUID>` while
  preserving comma-separated frontend calls. If Spring MVC does not split the
  existing comma-separated form as needed, add a private converter that throws
  `IllegalArgumentException` for invalid UUIDs and lets the global handler map
  the request to 400, or call an application-owned normalization method that
  raises `BusinessException(INVALID_ARGUMENT)`.

### Application Changes

`FollowApplicationService`:

- Continue using `FollowDomainService.validateFollow(...)` and
  `validateUnfollow(...)` for write rules.
- Add a private read-side validation helper for actor/entity arguments and
  `USER`-only support, or add explicit read validation methods to
  `FollowDomainService` if the implementation keeps all follow relation rules
  there.
- Replace non-`USER` `false`, `0`, and `List.of()` behavior with
  `BusinessException(INVALID_ARGUMENT)`.
- Keep existing pagination clamping for page and size.
- Keep block filtering behavior unchanged.

`LikeApplicationService`:

- Ensure `setLike(...)` rejects unsupported entity types before
  `likeRepository.isLiked(...)`.
- Add a private query/action validation helper for `isLiked`, `count`,
  `cleanupEntityLikes`, `counts`, and `statuses`.
- Deduplicate batch IDs and enforce a 200 ID maximum before repository calls.
- Return empty maps for empty batch ID lists after validation.
- Keep content owner resolution in `resolveEntity(...)` unchanged for POST and
  COMMENT, and keep USER as direct target user resolution.

### Domain Changes

`FollowDomainService`:

- Keep write-side follow/unfollow validation as the source of truth for
  self-follow and `USER`-only write behavior.
- Optionally expose a read-side validation method only if it makes the
  application service clearer and does not add HTTP semantics to the domain.

`LikeDomainService`:

- Extend `validateLike(...)` so write use cases reject unsupported entity
  types, not just non-positive values.
- Keep target-state resolution and event creation unchanged.

### Documentation

Update `docs/handbook/business-logic/social.md` to state:

- Follow supports only user relations for both reads and writes.
- Like supports USER, POST, and COMMENT.
- Batch like IDs are strict: invalid UUIDs and over-limit batches are rejected.

No changes are required to architecture overview documents unless the
implementation introduces a new guardrail.

## Testing

Add focused unit tests before implementation changes.

`FollowApplicationServiceTest`:

- `follow` rejects non-`USER`.
- `unfollow` rejects non-`USER`.
- `hasFollowed`, `followeeCount`, `followerCount`, `listFollowees`, and
  `listFollowers` reject non-`USER`.
- Existing idempotency, block filtering, and compensation tests continue to
  pass.

`LikeDomainServiceTest`:

- `validateLike` accepts USER, POST, and COMMENT.
- `validateLike` rejects unsupported positive values and non-positive values.

`LikeApplicationServiceTest`:

- `setLike` rejects unsupported entity type before repository or resolver
  collaboration.
- `isLiked`, `count`, `cleanupEntityLikes`, `counts`, and `statuses` reject
  unsupported entity types.
- `counts` and `statuses` deduplicate IDs, return empty maps for empty lists,
  and reject batches over 200 IDs.

`FollowControllerTest`:

- Controller delegates non-`USER` follow/status inputs to
  `FollowApplicationService` instead of throwing before delegation.

`LikeControllerTest`:

- Controller delegates unsupported entity types to `LikeApplicationService`
  instead of throwing before delegation.
- Batch endpoints preserve normal comma-separated frontend calls.
- Invalid UUID tokens return 400 if covered through MVC tests, or throw the
  selected strict parsing exception in direct controller tests.

Run:

```bash
cd backend
mvn test -pl :community-app -Dtest='FollowControllerTest,LikeControllerTest,FollowApplicationServiceTest,LikeApplicationServiceTest,FollowDomainServiceTest,LikeDomainServiceTest'
mvn test -pl :community-app -Dtest='*ArchTest'
```

## Acceptance Criteria

- `FollowController` and `LikeController` no longer throw `BusinessException`
  for entity-type support rules.
- Social controllers no longer duplicate `EntityTypes.isValid(...)` business
  checks.
- Unsupported follow entity types are rejected consistently by
  `FollowApplicationService`.
- Unsupported like entity types are rejected consistently by
  `LikeApplicationService` or `LikeDomainService` before persistence or foreign
  owner API calls.
- Batch like ID handling is strict and documented; invalid UUID values are not
  silently dropped.
- Normal frontend follow and like flows continue to work.
- Focused social tests and architecture tests pass.

## Risks

- Changing follow query behavior from empty responses to 400 may affect hidden
  clients that relied on non-`USER` no-op reads. The known frontend defaults
  follow reads to `USER`, so the expected product path is unaffected.
- Spring MVC `List<UUID>` binding may not preserve the current comma-separated
  query format without a converter. Implementation must verify the existing
  frontend `entityIds=uuid1,uuid2` request shape.
- Adding stricter batch limits can surface bad callers that were previously
  hidden by silent truncation.
