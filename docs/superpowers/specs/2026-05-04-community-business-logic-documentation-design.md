# Community Business Logic Documentation Design

## 1. Purpose

This spec defines a maintainable documentation set for the current business logic in this repository. The goal is not to restate every method body, but to make each business domain understandable without requiring a reader to reverse-engineer controllers, application services, domain services, repositories, jobs, and event handlers from scratch.

The documentation must answer these questions for each domain:

- What business facts does the domain own?
- Which HTTP endpoints, internal endpoints, events, jobs, or WebSocket frames enter the domain?
- What are the main read and write flows?
- Which state machines, idempotency rules, compensation paths, and failure semantics matter?
- Which cross-domain APIs or contract events are used?
- Which code files should a maintainer read first?

## 2. Scope

The documentation covers current behavior in:

- `backend/community-app`
- `backend/community-im`
- `backend/community-im-gateway`
- `backend/community-gateway`, only where it affects business traffic routing, IM session routing, rate limiting, and edge behavior
- `frontend`, only as a client-facing surface index for the same business capabilities

The documentation does not redefine architecture rules, storage schemas, security policy, or reliability mechanisms. Those remain owned by:

- `docs/handbook/architecture.md`
- `docs/handbook/system-design.md`
- `docs/handbook/security.md`
- `docs/handbook/reliability.md`
- `docs/handbook/data-and-storage.md`
- `docs/handbook/integration-contracts.md`

## 3. Documentation Shape

Create a detailed business logic documentation set under:

```text
docs/handbook/business-logic/
```

The set uses one index plus one document per large domain group:

| File | Responsibility |
| --- | --- |
| `README.md` | Map business domains to detail pages and explain reading order. |
| `auth.md` | Login, registration, captcha, password reset, refresh session, cleanup jobs. |
| `user.md` | User profile, avatar, credentials, moderation state, admin role, points, refresh sessions. |
| `content.md` | Posts, comments, categories, tags, bookmarks, subscriptions, reports, moderation, score, content events. |
| `social.md` | Likes, follows, blocks, social events, IM policy effects, cleanup. |
| `growth.md` | Task progress, source event dedupe, rewards, user level rules. |
| `wallet.md` | Accounts, ledger, postings, recharge, withdraw, transfer, market/reward/admin actions. |
| `market.md` | Listings, inventory, addresses, orders, disputes, wallet action saga, recovery, auto confirm. |
| `notice-search-analytics-ops.md` | Notice read model, search projection/reindex, analytics ingest/query, ops entry. |
| `im.md` | IM gateway sessions, realtime WebSocket, private messages, room messages, membership, read/unread, policy projection. |
| `frontend-surfaces.md` | Frontend routes and service modules mapped to the business capabilities. |

This structure intentionally keeps `docs/handbook/business-flows.md` as a compact overview. The new documents are the deeper explanation layer.

## 4. Per-Document Template

Each domain document should use the same conceptual checklist, but it may vary the section names to fit the domain:

1. Owner / SSOT
2. Public and internal entry points
3. Core model and state
4. Main read flows
5. Main write flows
6. Idempotency, retries, and compensation
7. Cross-domain collaboration
8. Failure semantics
9. Key code map

The template is deliberately not a copy of `auth-login-session-flow.md`. The login flow document is a precise single-flow trace; these documents are domain maps that help a maintainer understand all business capabilities in that domain.

## 5. Maintenance Rules

When a backend business behavior changes:

1. Update the relevant `docs/handbook/business-logic/*.md` file.
2. If a new core `ApplicationService`, domain service, listener, handler, enqueuer, bridge, or job is introduced, update `docs/handbook/core-logic-index.md`.
3. If an API contract, event contract, storage key/table, or security rule changes, update the corresponding handbook SSOT document.
4. Do not leave current behavior documented only in `docs/superpowers/specs` or `docs/superpowers/plans`; those files are design and migration history.

## 6. Acceptance Criteria

The documentation set is acceptable when:

- Every active business domain is represented.
- Each document names real entry points and key implementation classes.
- Important state transitions and pending/saga semantics are documented.
- Cross-domain synchronous APIs and asynchronous events are described at the business level.
- The reader can start from `README.md` and know which document to open for any major business feature.
