# Playwright Single User Capability Coverage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make every user-reachable business capability in the single topology traceable to a browser-only Playwright regression scenario and enforce that mapping in CI.

**Architecture:** Build the browser boundary, isolated runner, AST coverage checker, stable journey filenames, and test identities first. Add domain journeys in independently reviewable vertical slices. Foundation uses a clearly named static-only guard while the manifest is incomplete; the final plan deletes that mode and makes the complete capability manifest and CI gate unconditional.

**Tech Stack:** Playwright Test, TypeScript AST, `@vue/compiler-sfc`, Node.js, Vue 3/Vitest, Docker Compose, GitHub Actions, Spring Boot configuration.

## Global Constraints

- Business specs and business fixtures must use only browser-visible interactions; they must not use `APIRequestContext`, the Playwright `request` fixture, `page.request`, Node `fetch`, axios, product API clients, or `page.evaluate(fetch)`.
- `scripts/health-check.mjs` is the sole direct HTTP exception and only probes the frontend root and Gateway `/actuator/health`.
- A `waitForResponse` result may be inspected only as evidence for a UI-triggered request; user-visible state remains the acceptance assertion.
- Every expected `4xx` must use the shared one-shot action-bound audit helper and a visible feedback assertion; static error allowlists are prohibited.
- No `test.skip`, `test.fail`, permanent ignore, expected `5xx`, broad `4xx` allowlist, or business-test retries. Keep Chromium only, `workers: 1`, `fullyParallel: false`, and `retries: 0`.
- Every write is produced through the product UI and named with `SINGLE_TEST_RUN_ID`; no direct API, DB, Redis, Kafka, or storage setup is permitted from Playwright.
- Isolated runs must use a unique Compose project, network, port set, and volume namespace, and always call `deployment.sh down ... -- -v` after diagnostics are collected.
- Backend configuration changes must preserve the strict DDD Tactical Layering in `/home/feng/code/project/community/AGENTS.md`; do not add test controllers or test-only business endpoints.

---

## Plan Map

| Order | Plan | Independently testable deliverable |
| --- | --- | --- |
| 1 | `2026-07-26-playwright-single-coverage-foundation.md` | Browser-only fixture boundary, isolated-run primitives, deterministic test configuration, transport normalization, and tested AST/guard utilities. |
| 2 | `2026-07-26-playwright-single-auth-content-social-coverage.md` | Browser journeys for auth, content, media, profile, social, notice, search, and growth projections. |
| 3 | `2026-07-26-playwright-single-wallet-market-governance-coverage.md` | Browser journeys for wallet, market, reports, moderation, admin user operations, and analytics. |
| 4 | `2026-07-26-playwright-single-drive-im-coverage.md` | Browser journeys for drive/OSS and IM, including public share and realtime behavior. |
| 5 | `2026-07-26-playwright-single-coverage-gate-enablement.md` | Complete capability manifest, CI/local runner integration, documentation, and end-to-end verification. |

## Execution Gates

### Task 1: Establish the Foundation Before Adding Capability Tags

**Files:**
- Execute: `docs/superpowers/plans/2026-07-26-playwright-single-coverage-foundation.md`

- [ ] **Step 1: Run the foundation unit checks**

```bash
npm --prefix tests/playwright-single run test:tools
npm --prefix frontend test -- src/api/services/avatarService.test.js src/api/services/imCoreChatService.test.js src/im/imRealtimeClient.test.js
```

Expected: the AST/guard/isolated-environment tests pass, and production UI transport changes retain their frontend unit contracts.

- [ ] **Step 2: Run browser-only smoke through the isolated runner**

```bash
npm --prefix tests/playwright-single run test:isolated:smoke
```

Expected: no Playwright business spec uses a direct HTTP client; the temporary foundation guard checks only browser-boundary rules, the runner creates a disposable topology, executes smoke, writes diagnostics, and removes named volumes. It does not claim that the incomplete matrix is complete.

- [ ] **Step 3: Commit the foundation slice**

```bash
git add tests/playwright-single frontend deploy
git commit -m "test(e2e): add browser-only coverage foundation"
```

### Task 2: Add Auth and Community Capability Journeys

**Files:**
- Execute: `docs/superpowers/plans/2026-07-26-playwright-single-auth-content-social-coverage.md`

- [ ] **Step 1: Run the domain-focused regression files**

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/01-auth.spec.ts tests/02-content-social-profile.spec.ts tests/03-notice-search-growth.spec.ts
```

Expected: all auth/content/social capability tags execute through visible UI flows and no unexpected audit record is attached.

- [ ] **Step 2: Commit the domain slice**

```bash
git add tests/playwright-single frontend deploy
git commit -m "test(e2e): cover auth and community capabilities"
```

### Task 3: Add Commerce and Governance Capability Journeys

**Files:**
- Execute: `docs/superpowers/plans/2026-07-26-playwright-single-wallet-market-governance-coverage.md`

- [ ] **Step 1: Run the commerce and governance regression files**

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/04-wallet.spec.ts tests/05-market.spec.ts tests/07-governance.spec.ts
```

Expected: balances, order states, dispute outcomes, role effects, and moderation states are asserted from browser-visible results.

- [ ] **Step 2: Commit the domain slice**

```bash
git add tests/playwright-single frontend deploy
git commit -m "test(e2e): cover commerce and governance capabilities"
```

### Task 4: Add Drive and IM Capability Journeys

**Files:**
- Execute: `docs/superpowers/plans/2026-07-26-playwright-single-drive-im-coverage.md`

- [ ] **Step 1: Run the drive and IM regression files**

```bash
npm --prefix tests/playwright-single run test:isolated:regression -- tests/06-drive.spec.ts tests/08-im.spec.ts
```

Expected: file state, share/revocation behavior, IM messages, read state, reconnect state, and block policy all have browser evidence.

- [ ] **Step 2: Commit the domain slice**

```bash
git add tests/playwright-single frontend deploy
git commit -m "test(e2e): cover drive and IM capabilities"
```

### Task 5: Turn On the Complete Matrix Gate

**Files:**
- Execute: `docs/superpowers/plans/2026-07-26-playwright-single-coverage-gate-enablement.md`

- [ ] **Step 1: Verify the capability contract**

```bash
npm --prefix tests/playwright-single run check:coverage
```

Expected: every product route and source-discovered user-reachable operation has exactly one manifest record or an explicit exclusion, shared operation records list their capability IDs without duplication, every capability tag is discoverable by Playwright, and no forbidden direct API call is found.

- [ ] **Step 2: Verify the complete isolated regression**

```bash
npm --prefix tests/playwright-single run test:isolated:regression
```

Expected: health, matrix gate, all `@regression` tests, Markdown reporting, Compose diagnostics, and volume cleanup succeed in one lifecycle.

- [ ] **Step 3: Commit the final gate**

```bash
git add tests/playwright-single .github/workflows docs/handbook
git commit -m "test(e2e): enforce user capability coverage"
```
