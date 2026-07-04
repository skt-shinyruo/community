# Core Logic Documentation Closure Plan

## Goal

Close the documentation loop for core runtime logic: every production-code core entry and key behavior should be traceable from `docs/handbook/core-logic-index.md` to handbook documentation that explains owner, entry, main path, state, idempotency, consistency, failure semantics, and key code.

Frontend coverage is scoped to business state, API orchestration, and route-to-page capability mapping. Display-only components, styling, and helpers with no business semantics are out of scope.

## Confirmed Decisions

- Use source scanning as the source of truth; `core-logic-index.md` is the navigation result, not the discovery mechanism.
- Classify candidates as `Covered`, `IndexOnly`, `Excluded`, or `Missing`.
- Keep `Excluded` out of the core index; document only debated or repeat-prone exclusions in `docs/handbook/core-logic-coverage-audit.md`.
- Do not add a CI-failing guard until historical gaps are classified and remediated.
- Add topic documents under `docs/handbook/core-logic/`.
- Topic documents may expand beyond the first gap list, but must cover a stable runtime theme rather than one class.
- Handbook behavior documentation is current-state only; future work stays in audit or plan documents.
- Handbook prose should be Chinese, while code identifiers, state names, topics, config keys, and commands remain in English.

## Execution Steps

1. Re-run the code graph scan in `docs/handbook/core-logic-coverage-audit.md` and refresh the current classification baseline.
2. Review each candidate and classify it as:
   - add to `core-logic-index.md` as `Covered`;
   - add to `core-logic-index.md` as `IndexOnly`;
   - document in a new or existing `docs/handbook/core-logic/*.md` topic;
   - mark as `Excluded` in the audit when the exclusion is likely to be rediscovered.
3. Start remediation with security and identity freshness:
   - `TokenFreshnessApplicationService`
   - `TokenFreshnessFilter`
4. Remediate async consistency backbone:
   - content/social/user/growth/search/notice/IM policy Kafka listeners;
   - outbox handlers;
   - enqueuers;
   - dispatch application services.
5. Fix IM core / realtime naming drift:
   - update index entries from old `*Service` names to current `*ApplicationService` and domain service names;
   - add topic coverage when domain docs alone do not explain runtime semantics.
6. Remediate recovery and compensation jobs:
   - start with `DriveUploadRecoveryJob`;
   - add or link recovery topic coverage as needed.
7. Review runtime, observability, gateway, and frontend narrowed-scope candidates.
8. After the baseline is clean, add a lightweight non-invasive check that detects new unclassified core candidates.

## Review Standard

An entry is `Covered` only when the linked handbook text explains current behavior sufficiently for a maintainer to understand the class's role in owner boundaries, state transitions, failure semantics, idempotency, consistency, or compensation. A class name appearing in a page is not enough.

`IndexOnly` is acceptable for thin adapters and binding classes that do not carry non-obvious security, idempotency, routing, or failure semantics.

## Verification

- `docs/handbook/core-logic-coverage-audit.md` contains the repeatable scan query and current classification table.
- `docs/handbook/core-logic-index.md` links every retained core candidate to an adequate handbook section.
- New topic pages under `docs/handbook/core-logic/` describe current implemented behavior only.
- No current behavior depends only on `docs/superpowers/specs` or `docs/superpowers/plans`.
