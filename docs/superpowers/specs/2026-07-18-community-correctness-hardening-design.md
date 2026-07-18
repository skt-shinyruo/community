# Community Correctness Hardening Design

- Date: 2026-07-18
- Status: Approved design
- Scope: the 15 correctness, security, concurrency, and frontend-session findings in the July 2026 code audit
- Chosen approach: targeted hardening inside the existing owner-domain boundaries

## 1. Context

The audited implementation has fifteen defects that can expose private OSS objects, break background OSS work, collapse distinct user actions into one idempotent replay, duplicate business side effects, lose quota or inventory correctness under concurrency, accept spoofed server facts, and make public frontend routes depend on private authenticated data.

The fixes span `community-app`, `community-oss`, `community-oss-client`, `community-gateway`, shared idempotency/outbox/web modules, IM outbox storage, the frontend, deployment configuration, and forward-only Flyway migrations. They must preserve the repository's strict DDD Tactical Layering rather than introducing a second application-entry style or allowing adapters to orchestrate owner domains directly.

## 2. Goals

1. Close every reported authorization and identity gap.
2. Make HTTP idempotency, outbox leases, inventory compensation, drive quota release, and moderation decisions correct under retries and concurrency.
3. Make wallet correction and like reward lifecycles reflect business identity rather than incidental request or relation keys.
4. Ensure terminal content deletion and comment reply targets are derived from authoritative server state.
5. Unify frontend refresh behavior and preserve anonymous market browsing.
6. Ship the changes with forward-only migrations, explicit compatibility order, deterministic tests, and operational visibility.

## 3. Non-goals

- Rewriting deployables or replacing the package-scoped monolith.
- Introducing distributed transactions or a new workflow engine.
- Changing the anonymous/public nature of market listing and detail routes.
- Changing wallet storage from signed `BIGINT` or preventing privileged corrections from creating debt.
- Editing frozen Flyway baselines to describe post-baseline changes.
- Refactoring unrelated legacy packages.

## 4. Architectural Constraints

All touched `community-app` business paths follow:

```text
Controller / Listener / Handler / Bridge / Enqueuer / Job
  -> same-domain *ApplicationService
      -> domain model / policy / repository interface
      -> foreign owner api.query / api.action when synchronous collaboration is required
      -> owner contracts.event when asynchronous collaboration is required
          -> infrastructure implementation
```

Inbound adapters only bind transport input, extract authenticated identity, and convert DTOs. Application services own transaction boundaries, commands, idempotency, authoritative lookups, cross-domain calls, and result assembly. Domain code remains independent of Spring, HTTP, persistence objects, infrastructure, and published owner APIs. MyBatis and Redis stay behind domain repositories or focused application-owned technical ports.

No fix may introduce a controller-to-repository path, an inbound-adapter-to-foreign-API path, a same-domain `api.*` detour, a mapper dependency in an application service, or HTTP types in application commands/results.

## 5. Identity and Access

### 5.1 OSS object authorization

The OSS service separates three security surfaces:

| Surface | Identity | Rule |
| --- | --- | --- |
| `/api/oss/**` | user JWT | actor and owner are taken only from the authentication context |
| `/internal/oss/**` | service JWT | token must have `aud=community-oss` and `scope=oss.internal` |
| `/files/**` | anonymous | only `PUBLIC + ACTIVE` objects are served |

User-facing request DTOs remove `ownerId` and `actorId`. These values do not enter an application command through any compatibility alias or fallback.

Each user-facing operation enters an OSS application service. The application service loads the object plus any active grant required for the caller and invokes a pure domain access policy. The policy has these minimum rules:

- object creation binds ownership to the authenticated user;
- private metadata and signed download access require ownership or an applicable active grant;
- grant creation/revocation and object deletion require ownership;
- public bytes are exposed only through `/files/**`, and only while the object is both `PUBLIC` and `ACTIVE`;
- nonexistent and concealed unauthorized objects produce the same external `404` result.

The policy receives identity, object facts, grant facts, and requested capability. It does not inspect security contexts, HTTP requests, mappers, or storage adapters.

### 5.2 Background OSS service identity

`community-oss-client` gains a service-token provider for internal operations. Background handlers, jobs, and application services use typed internal client methods that call `/internal/oss/**` with a short-lived service JWT. They do not depend on a Servlet request and do not forward an arbitrary browser bearer token.

The service JWT identifies the calling service in `sub`, is signed by configured service credentials, and is rejected unless its audience and scope match OSS. OSS records the service subject in audit context. Internal endpoints expose only the capabilities needed by reference management, recovery, and cleanup; service identity is not treated as a synthetic end user.

Key material and issuer/audience configuration are validated at startup. Missing production credentials fail closed.

### 5.3 Canonical client IP

The edge and application use one trust model:

1. The externally reachable NGINX removes client-supplied `Forwarded`, `X-Forwarded-For`, and `X-Real-IP`; it sets `X-Forwarded-For` to `$remote_addr` rather than using `$proxy_add_x_forwarded_for`.
2. The gateway accepts that header only when its immediate peer is a configured trusted NGINX address. It walks the peer plus forwarded chain from right to left, discards trusted proxy hops, and selects the first untrusted hop as the canonical client.
3. If the gateway's immediate peer is untrusted, it ignores forwarding headers and uses the socket peer address.
4. Before proxying, the gateway removes all incoming forwarding headers and emits a new `X-Forwarded-For` containing only the canonical client address.
5. An application accepts that canonical header only when its immediate peer is a configured gateway address; otherwise it uses its socket peer.

All consumers, including post-view collection, use the canonical value supplied by the shared web boundary. Controllers do not parse forwarding headers independently. Deployment defaults include the actual NGINX/gateway networks so normal traffic does not collapse to a shared container IP.

## 6. Reliability Infrastructure

### 6.1 Frontend idempotency identity

The frontend removes the global ten-second URL/body fingerprint cache. Every new business invocation receives a new idempotency key, even if its URL and body equal a recent invocation.

The key is attached to the Axios request config. A transport retry of that same config preserves the existing header, while a new API method call creates a fresh key. Tests distinguish these two cases explicitly:

- two sequential equal recharge/order/post/comment calls use different keys;
- retrying one failed request config uses the original key.

### 6.2 Transactional HTTP idempotency

`executeRequired` is the correctness entry for protected write endpoints. It requires both a JDBC-backed idempotency store and an active transaction synchronized with the same datasource. A missing transaction or incompatible store fails before business work starts.

Within one database transaction it:

1. claims `(operation, userId, idempotencyKey)` with the request fingerprint;
2. executes the business function;
3. serializes the successful result;
4. writes the serialized response and `SUCCESS` status;
5. commits the idempotency row and business changes atomically.

There is no `afterCommit` success write. Serialization failure throws and rolls back the entire transaction; it is never converted to the string `"null"`. A successful replay must deserialize a valid stored response or fail as an infrastructure error.

The behavior of existing rows is:

- same key and same fingerprint in `SUCCESS`: replay the stored result;
- same key and different fingerprint: `409 IDEMPOTENCY_REPLAY_CONFLICT`;
- live concurrent processing: `409 IDEMPOTENCY_IN_PROGRESS`;
- quarantined historical processing: `409 IDEMPOTENCY_OUTCOME_INDETERMINATE`.

Before deployment, old writers are stopped and drained. Any remaining legacy `PROCESSING` row has an unknowable outcome and is migrated to `INDETERMINATE`; it is never expired into permission to run the business action again. Clients must not be advised to generate a new key for an indeterminate action.

### 6.3 Outbox lease fencing

Every claim generates a new opaque lease token and stores it with a dedicated processing deadline. A claimed event carries that token through the worker to the store.

All `SUCCEEDED`, retry, and `DEAD` updates use a compare-and-set predicate containing at least event identity, `PROCESSING` status, and the lease token. Expired-row recovery also invalidates the previous token before an event can be reclaimed. Therefore an old worker cannot overwrite a result written by a newer owner.

A terminal update that affects zero rows means ownership was lost. The worker stops processing that result, emits a structured warning and counter, and does not perform an unconditional fallback update. Handlers remain idempotent because delivery is still at least once.

The same shared implementation and schema semantics apply to the Community and IM `outbox_event` tables. Old and fenced workers must never run concurrently.

### 6.4 One frontend refresh coordinator

Normal HTTP, IM HTTP, and authentication bootstrap share one refresh coordinator and one in-flight promise. They do not issue independent refresh requests.

Each authenticated request records the access-token generation it used. On `401`:

- if the auth store already holds a newer token, retry once with that token without refreshing;
- otherwise join or start the shared refresh;
- mark the retry so another `401` cannot loop.

After a successful refresh, the store installs the token and reloads the current user/roles once. Existing profile state may remain visible until replacement; it is not silently cleared with no reload. A terminal refresh failure clears frontend authentication state only if no newer token generation has appeared.

The backend does not attach a cookie-deletion response to an invalid refresh attempt, because a concurrent successful rotation may already have installed a newer cookie. Explicit logout and explicit session revocation remain the operations that clear the refresh cookie.

## 7. Business and Funds Consistency

### 7.1 Market escrow compensation

The market order state machine defines `ESCROW_FAILED` to mean that reserved stock has already been compensated. Recovery from that state may only transition the order to `CANCELLED`; it may not increment stock again.

Inventory restoration is attached to the single guarded transition that first records escrow failure. Replaying `completeEscrowNoop` after an action-status write failure performs only the terminal order transition. Repository updates remain conditional, and tests inject failure between the order compensation and action completion to prove stock never exceeds its pre-order amount.

### 7.2 Drive permanent deletion and quota

Permanent deletion uses one `REQUIRES_NEW` transaction per operation. The application service locks the owner's space row, re-reads the requested entry and its subtree, and conditionally transitions each eligible row from `TRASHED -> DELETED`. The repository returns the rows whose transition this transaction actually won; quota is reduced by the sum of file bytes in that returned set only.

The space update and file transition commit together. A concurrent loser releases zero bytes and returns the existing terminal result. The quota invariant is preserved even when the user has other files.

OSS byte deletion happens only after the database transaction commits, so no network call holds database locks. If deletion fails, the request reports storage unavailability; repeating permanent deletion selects the already-`DELETED` files for OSS cleanup but releases zero additional bytes.

### 7.3 Wallet privileged corrections

Wallet domain behavior distinguishes normal debits from privileged corrections:

| Operation | Freeze/minimum-balance checks | May create negative balance |
| --- | --- | --- |
| normal debit/spend/transfer | enforced | no |
| privileged reward reversal or administrator correction | bypassed by explicit policy | yes |
| ordinary credit | allowed and increases balance | repays existing debt first |

Privileged mode is not a client-controlled flag. Only dedicated, authorized application use cases can request a correction with an auditable correction reason and source identity. The domain model applies the policy; application services still own actor authorization and idempotency.

Double-entry invariants remain unchanged. The signed `BIGINT` balance column already supports debt and requires no type migration.

### 7.4 Moderation claim and decision

Moderation processing uses an atomic `PENDING -> PROCESSING` claim. A request that did not win the claim cannot execute penalties, notifications, or action writes.

For the winner, decision validation, owner-domain side effects, the unique `moderation_action`, final report status, and notification outbox row commit in one transaction. No external network side effect is placed inside this transaction. A rollback returns all durable state to its pre-claim condition.

`moderation_action.report_id` is unique when non-null. A replay whose normalized decision fields equal the existing action returns that action without repeating side effects. A different decision for the same report, or a stale attempt after another decision won, returns `409 MODERATION_DECISION_CONFLICT`. Direct administrative actions with a null report ID remain allowed.

## 8. Event Lifecycle and Server-Derived Facts

### 8.1 Like relation instances and wallet rewards

The stable relation key `(userId, entityType, entityId)` describes the current pair, but it does not identify separate like lifecycles. Each successful new like therefore receives a UUIDv7 `relationInstanceId`, persisted in `social_like` and carried through:

- `LikeRelation` and repository scans;
- like-created and like-removed domain events;
- the published `LikePayload` contract;
- content-deletion cleanup that removes likes in bulk.

Unlike removes by a compare-and-set containing the relation instance and publishes that exact instance. Re-liking the same target creates a new instance. The stable relation key remains available for notice grouping and existing notice semantics.

Wallet idempotency sources use `<relationInstanceId>:created` and `<relationInstanceId>:removed`. Duplicate delivery of either lifecycle event is harmless, while a later re-like is a distinct reward lifecycle. Consumers fall back to the legacy relation key only when reading an older event without `relationInstanceId`. This additive contract requires the compatible wallet consumer to deploy before the new social producer.

### 8.2 Terminal post deletion tombstones

Deleting a post writes both terminal status and `update_time = deleted_time`. The hot-feed projection command explicitly identifies terminal deletion instead of presenting it as an ordinary versioned update.

The Redis projection applies deletion with these rules:

1. deletion bypasses the ordinary stale-version rejection;
2. it evicts hot-feed and related cached projections;
3. one atomic operation writes the processed event identity, maximum observed version, and a permanent deletion tombstone;
4. the tombstone has no TTL and rejects every later non-delete projection regardless of version;
5. duplicate delete events remain idempotent.

The contract keeps the stable event ID `content:PostDeleted:<postId>`. This ensures a higher-version comment/like projection cannot preserve or resurrect a deleted post.

### 8.3 Comment reply targets

Clients no longer submit `replyToUserId` or `targetId` as trusted facts. Those fields are removed from the HTTP request, application command, idempotency fingerprint, and published synchronous action API.

`parentCommentId` means the comment directly replied to:

- a top-level comment has no parent;
- a reply to a root comment sends the root comment ID;
- a reply to a nested reply sends that reply's ID.

The application loads an `ACTIVE` direct parent. The domain derives the root comment, direct parent, target author, and notification recipient from stored facts. The repository locks and revalidates both root and direct parent before insert, including post membership and active state. Self-notification suppression remains a server policy.

The frontend changes only the submitted parent ID. Existing flat reply rendering remains compatible because stored root/thread fields are still derived and retained.

## 9. Anonymous Market Detail

Market detail keeps public listing state separate from private address state. Loading the listing never depends on the address request.

Only an authenticated viewer of a physical listing requests the private address book. Address `401`, empty data, or transient failure cannot replace a successfully loaded public listing with a page-level error. Authenticated empty and error states provide the existing address-management and retry actions.

The address loader watches both listing identity/type and authentication-token generation. It cancels or sequence-guards requests and discards responses that belong to a previous listing or auth state.

When buying:

1. check authentication first;
2. an anonymous viewer is redirected to login with `route.fullPath` as the return target;
3. only an authenticated physical-item buyer is required to select a valid address;
4. virtual-item purchases remain independent of address state.

The refresh-coordinator and anonymous-market changes ship in one frontend bundle.

## 10. Error Semantics

| Condition | HTTP status | External behavior |
| --- | --- | --- |
| missing/invalid authentication | `401` | authentication required |
| authenticated but globally unauthorized | `403` | access denied |
| concealed object-level denial | `404` | indistinguishable from absence |
| validation failure | `400` | stable validation code/details |
| idempotency fingerprint conflict | `409` | do not execute business action |
| indeterminate legacy idempotency outcome | `409` | do not suggest a new key |
| conflicting/stale moderation decision | `409` | return existing outcome when identical |
| transient dependency/infrastructure failure | `5xx`, normally `503` | retryable according to endpoint policy |
| lost outbox lease | no HTTP mapping | benign CAS miss plus metric/log |

All HTTP failures continue to use the repository's unified `Result<T>` envelope and trace propagation.

## 11. Forward-only Migrations

### 11.1 Community schema

Migrations after the current `V007` are:

- `V008__add_outbox_lease_fencing.sql`: add nullable `lease_token BINARY(16)`, nullable `processing_lease_until`, and the processing-lease scan index to `outbox_event`.
- `V009__quarantine_indeterminate_http_idempotency.sql`: after the mandatory writer drain, convert remaining legacy processing rows to the explicit indeterminate status and clear obsolete processing expiry data.
- `V010__enforce_unique_moderation_action_report.sql`: fail if duplicate non-null report IDs exist, then replace the non-unique report index with a unique report constraint that still permits multiple null report IDs.
- `V011__add_social_like_relation_instance.sql`: add a nullable binary UUID column, backfill a distinct value per existing row, add a unique index, then make the column non-null.

New application-created relation instances are UUIDv7. Historical backfill values need uniqueness and stability after migration; they do not need to pretend to carry creation-time ordering.

### 11.2 IM schema

After the current IM `V001`:

- `V002__add_outbox_lease_fencing.sql`: apply the same lease-token, processing-deadline, and scan-index shape to IM `outbox_event`.

OSS remains at `V003` because these fixes require no OSS schema change.

### 11.3 Migration rules

- Do not edit `V001` or any already-applied migration.
- The schema manifests describe the frozen version-one baseline used to validate legacy baselining; do not update them merely to represent later Flyway migrations.
- Update migration-count assertions, post-migration column/index assertions, upgrade fixtures, and H2/test schemas that model current runtime tables.
- Run migrations against real MySQL with Testcontainers, including empty-schema and version-one upgrade paths.
- The moderation migration performs a duplicate preflight and aborts release rather than deleting or coalescing audit history.

## 12. Compatibility and Deployment Order

Two drains are mandatory correctness gates:

1. Stop and drain old HTTP idempotency writers before quarantining legacy `PROCESSING` rows. Their outcomes cannot be inferred safely.
2. Stop and drain all old outbox workers before enabling token fencing in Community and IM. Mixed old/new workers are unsafe because old terminal updates are unfenced.

The complete order is:

1. Configure service-JWT signing/verification keys and expected issuer/audience/scope.
2. Deploy gateway/NGINX forwarding-header sanitation, then enable the matching trusted-proxy CIDRs in applications.
3. Deploy OSS support for separated user/internal identities and internal endpoints.
4. Deploy background OSS callers and `community-oss-client` service identity.
5. Run a read-only moderation duplicate preflight. Any duplicate aborts the release for explicit data review before the maintenance window.
6. Stop and drain every old Community application instance, including protected HTTP writers and outbox workers, and stop/drain IM outbox workers. This simultaneously satisfies the idempotency and fencing gates and prevents old social writers from inserting rows without a relation instance.
7. Apply Community `V008` through `V011` and IM `V002`. The moderation migration repeats the duplicate assertion inside Flyway.
8. Deploy the transactional idempotency implementation, every fenced-outbox worker, and the schema-compatible Community/IM applications. Verify that no old worker or Community writer remains, then resume traffic and workers.
9. In this first Community rollout, the wallet consumer accepts optional `relationInstanceId`, while emission of new relation-instance events remains disabled by a rollout gate.
10. After all wallet consumer instances are compatible, enable relation-instance emission in the social producer and content-deletion cleanup. A later release may remove the temporary rollout gate after the compatibility window.
11. Ship the refresh coordinator and anonymous market behavior as one frontend bundle.

Rollback is application-forward, not migration-down: disable or redeploy application code that remains compatible with the additive schema, correct the fault, and release a new migration if schema repair is required. Do not undo an applied Flyway file.

## 13. Observability

Add or preserve structured signals for:

- OSS authorization denial by surface, capability, and caller type without exposing object secrets;
- service-JWT validation failure by reason;
- indeterminate idempotency responses and replay conflicts;
- outbox lease-loss CAS misses by topic/handler;
- market compensation attempts and affected-row counts;
- drive delete winners/losers and released bytes;
- moderation claim conflicts and replay classification;
- refresh attempts joined, stale-token retries, success, and terminal failure;
- discarded stale address responses.

Logs keep trace context and omit JWTs, cookies, signed URLs, addresses, and raw private payloads.

## 14. Verification Strategy

Implementation follows red-green-refactor for every defect. The minimum matrix is:

| Area | Required proof |
| --- | --- |
| OSS | owner, active grant, unrelated user, public-active, public-deleted/private, user JWT vs service JWT, background call without Servlet context |
| Client IP | untrusted direct peer, trusted one/multi-hop chain, spoofed left prefix, malformed address, post-view path using canonical resolver |
| Frontend idempotency | equal new actions get different keys; retry of the same config keeps one key |
| Backend idempotency | atomic business/success commit, serialization rollback, conflicting fingerprint, concurrent key, indeterminate legacy row, missing transaction/store |
| Market | injected action-finalization failure followed by recovery never restores stock twice |
| Drive | two concurrent permanent deletes produce one state transition and one quota release; OSS failure does not release again |
| Outbox | expired worker cannot mark success/retry/dead after a new token claim; Community and IM schemas both support fencing |
| Wallet | normal overdraft rejected; frozen/minimum checks enforced normally; privileged correction can create debt; later credit repays debt; ledger balances |
| Likes | create/remove duplicate delivery, remove-before-create delivery, unlike/re-like instances, legacy payload fallback, content-deletion bulk removal |
| Post deletion | lower/equal/higher projection versions cannot survive or resurrect after terminal tombstone; duplicate delete is idempotent |
| Moderation | two concurrent administrators yield one action/side-effect/notice; identical replay returns existing; conflicting replay returns `409` |
| Comments | spoof fields absent, direct parent determines recipient, nested reply derives root, inactive/mismatched parent rejected under lock |
| Refresh | HTTP/IM/bootstrap share one request, stale `401` retries newer token, failed old refresh does not clear new token/cookie, profile reloads |
| Market detail | anonymous physical/virtual detail, authenticated address success/empty/error, auth/listing switch discards stale responses, login return path |
| Migrations | empty MySQL, V001 upgrade, idempotent rerun, duplicate moderation preflight failure, unique relation backfill, frozen-baseline validation |

Architecture guardrails must pass after the backend boundary changes:

```bash
cd backend
mvn test -pl :community-app -Dtest='*ArchTest'
```

The broader backend verification target is:

```bash
cd backend
mvn -pl :community-common-idempotency,:community-common-outbox,:community-common-web,:community-gateway,:community-oss-client,:community-oss,:community-app,:im-core -am test
mvn -pl :community-db-migrations,:community-im-db-migrations,:community-oss-db-migrations -am test
```

Migration tests require Docker for MySQL Testcontainers. Frontend verification is:

```bash
cd frontend
npm test
npm run build
```

## 15. Finding Traceability

| Finding | Design section | Completion criterion |
| --- | --- | --- |
| 1. OSS object-level authorization | 5.1 | no user-controlled identity; policy covers metadata, signed access, grants, deletion, public bytes |
| 2. Background OSS identity | 5.2 | internal jobs succeed with scoped service identity and no request context |
| 3. Frontend key reuse | 6.1 | distinct equal actions have distinct keys |
| 4. Post-commit idempotency gap | 6.2 | business result, serialized response, and success status commit atomically |
| 5. Duplicate market stock compensation | 7.1 | recovery from `ESCROW_FAILED` cannot add stock |
| 6. Duplicate drive quota release | 7.2 | exactly one concurrent delete releases bytes |
| 7. Unfenced outbox lease | 6.3 | every processing terminal update is token-CAS protected |
| 8. Unreliable client IP | 5.3 | sanitized right-to-left trust-chain resolution is used everywhere |
| 9. Reward/admin correction failure | 7.3 | privileged corrections bypass spend restrictions and may create debt |
| 10. Like lifecycle reward collision | 8.1 | each like lifecycle has a durable relation instance |
| 11. Deleted post surviving projections | 8.2 | permanent tombstone dominates every non-delete version |
| 12. Concurrent moderation | 7.4 | one report produces at most one durable action and one side-effect set |
| 13. Spoofed reply recipient | 8.3 | recipient and thread facts are derived only from locked stored comments |
| 14. Refresh race | 6.4 | all clients coordinate refresh and stale failures cannot clear newer auth |
| 15. Physical detail not anonymous | 9 | public detail succeeds independently of private address state |

The design is complete only when all fifteen criteria and the verification matrix pass without weakening the DDD architecture tests.
