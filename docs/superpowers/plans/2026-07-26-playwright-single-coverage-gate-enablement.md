# Playwright Single Capability Coverage Gate Enablement Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enable an unconditional capability-matrix gate, one isolated local/CI runner, and documentation that make browser-only user-capability coverage enforceable for every change.

**Architecture:** The manifest is the single source of route, operation, capability, exclusion, and test-tag ownership. Its checker parses the router/SFC/module graph and lists Playwright titles instead of trusting filenames. The runner always performs health, the strict matrix gate, its selected suite, reporting, diagnostics, and volume cleanup in `try/finally`; no final-state bypass exists.

**Tech Stack:** Node.js ESM, TypeScript AST, `@vue/compiler-sfc`, Playwright Test, Docker Compose, GitHub Actions, Markdown/JSON reports.

## Global Constraints

- Completed `check:coverage` is strict and unconditional. It fails for unmapped route/service operation, missing or duplicate owner, stale tag, direct HTTP test client, skipped/fixme/fail business test, detached expected-error allowlist, expected `5xx`, or nonzero Playwright retry configuration.
- Browser specs and fixtures are UI-only. `scripts/health-check.mjs` is the sole direct HTTP exception and probes only frontend root plus Gateway `/actuator/health`.
- Permit `response.request()` only as observation of a UI-triggered response. Prohibit request fixtures, `page.request`, `APIRequestContext`, product clients, Node `fetch`, axios, and `page.evaluate(fetch)` in business tests/fixtures. Expected `4xx` cases must use the one-shot action-bound `allowExpectedHttpError` helper; static `expectedHttpErrors` declarations and direct array mutation are forbidden.
- The isolated runner generates unique Compose project, volume namespace, subnet, port set, origins, MailHog port, and `SINGLE_TEST_RUN_ID`, then always calls `deployment.sh down ... -- -v` after diagnostics.
- PR executes strict gate then `@smoke`; nightly/manual executes the same runner with strict gate then `@regression`. Use Chromium only, one worker, nonparallel, zero retries.
- Route exclusions are only redirect, editorial-preview, developer-diagnostic, authorization-result, and not-found surfaces. They must never hide a user-reachable business operation.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `tests/playwright-single/coverage/capability-manifest.mjs` | Complete capability, route, operation, exclusion, tag/spec ownership, and browser responsibility source. |
| `tests/playwright-single/coverage/analyze.mjs` | Router/SFC/import graph extraction and AST direct-client guard. |
| `tests/playwright-single/coverage/check-coverage.mjs` | Strict CLI validation and Markdown/JSON report writer. |
| `tests/playwright-single/coverage/check-coverage.test.mjs` | Unit tests for manifest/tag/report errors and a passing summary. |
| `tests/playwright-single/scripts/run-isolated.mjs` | One lifecycle with strict coverage before each suite. |
| `.github/workflows/playwright-single.yml` | Thin CI entrypoint invoking the shared runner. |
| `tests/playwright-single/README.md` | Local commands, browser boundary, isolated side effects, and reports. |
| `docs/handbook/testing.md` | Project test-layer responsibility and gate guidance. |

## Task 1: Specify and Test the Strict Manifest Contract

**Files:**
- Create: `tests/playwright-single/coverage/capability-manifest.mjs`
- Modify: `tests/playwright-single/coverage/analyze.mjs`
- Modify: `tests/playwright-single/coverage/analyze.test.mjs`
- Create: `tests/playwright-single/coverage/check-coverage.test.mjs`
- Modify: `tests/playwright-single/coverage/check-coverage.mjs`

**Interfaces:**
- Produces `capabilityManifest` with `version`, `complete: true`, `capabilities`, `routes`, `operations`, and `exclusions`.
- A capability has `{ id, domain, routes, operations, spec, tag, writes, browserResponsibility }`.
- A route and operation occur once and refer to one or more capability IDs. Shared operations use `capabilityIds`, never duplicate records.
- Produces `validateCapabilityManifest({ routes, operations, manifest, listedTitles })` and `writeCoverageReports(result, reportsDir)`.

- [ ] **Step 1: Write failing strict-manifest tests**

Add failing cases for a missing `/wallet` route, missing `walletService.createTransfer`, duplicate `authService.login`, a `@cap:market.order` tag absent from `@regression` titles, and an exclusion with no reason. Add alias and script-setup fixtures where `import { login as apiLogin }` is recorded as `authService.login`, not `apiLogin`. Add skip fixtures for `test.skip`, `test.fixme`, `test.fail`, `test.describe.skip`, and `test.describe.fixme`; each must fail. Add forbidden-client fixtures importing `APIRequestContext`, using `page.request.post`, `fetch`, axios, a frontend service client, and `page.evaluate(() => fetch(...))`. Add audit-exception fixtures that attempt a wildcard path, a `500` status, a direct `expectedHttpErrors` declaration, and a direct array mutation; each must fail. A passing dynamic-path fixture must use an awaited action-bound call:

~~~ts
const shareToken = 'token-a'
await allowExpectedHttpError(
  page,
  {
    method: 'POST',
    path: `/api/drive/shares/${shareToken}/verify`,
    status: 403
  },
  () => page.getByRole('button', { name: '访问分享' }).click(),
  () => expect(page.getByText('提取码错误')).toBeVisible()
)
~~~

Add a passing fixture which uses `response.request().method()` inside `page.waitForResponse`, proving UI-triggered observation remains legal.

- [ ] **Step 2: Run strict checker tests to verify failure**

Run:

~~~bash
npm --prefix tests/playwright-single run test:tools
~~~

Expected: FAIL because no complete manifest exists and the checker still permits foundation-only behavior.

- [ ] **Step 3: Implement strict validator behavior**

Load `capability-manifest.mjs` and reject `complete !== true`. Delete every foundation/bootstrap/partial-manifest argument and branch from checker and runner. Parse `frontend/src/router/index.js`, traverse each nonexcluded route component plus `frontend/src/App.vue`, parse both SFC script blocks, resolve imports recursively, and record every reachable named export imported from `frontend/src/api/services/`, preserving the exported symbol when it has a local alias.

Run `playwright test --list --reporter=json`, resolve titles containing both `@regression` and a manifest capability tag such as `@cap:wallet.recharge`, and fail for a missing tag or spec. Scan only `tests/` and `fixtures/` for forbidden direct clients, leaving the health script outside the scan. Reject `test.skip`, `test.fixme`, `test.fail`, and their `test.describe` variants. Reject every `expectedHttpErrors` declaration/mutation. Require a validated, awaited `allowExpectedHttpError(page, entry, performUiAction, assertVisible)` call for a dynamic expected path; reject wildcard paths, duplicate concurrent arms, `5xx`, statuses outside `400`-`499`, a call without the UI action callback, or a call without the visible assertion callback.

- [ ] **Step 4: Run strict checker tests**

Run:

~~~bash
npm --prefix tests/playwright-single run test:tools
cd tests/playwright-single
npm exec -- playwright test --list --reporter=json
cd ../..
~~~

Expected: PASS; the list is machine-readable and no product topology starts.

- [ ] **Step 5: Commit strict checker mechanics**

~~~bash
git add tests/playwright-single/coverage tests/playwright-single/scripts/run-isolated.mjs tests/playwright-single/scripts/run-isolated.test.mjs
git commit -m "test(e2e): make capability gate strict"
~~~

## Task 2: Populate the Complete Route and Capability Ownership Matrix

**Files:**
- Modify: `tests/playwright-single/coverage/capability-manifest.mjs`
- Modify: `tests/playwright-single/tests/00-smoke.spec.ts`
- Modify: `tests/playwright-single/tests/01-auth.spec.ts`
- Modify: `tests/playwright-single/tests/02-content-social-profile.spec.ts`
- Modify: `tests/playwright-single/tests/03-notice-search-growth.spec.ts`
- Modify: `tests/playwright-single/tests/04-wallet.spec.ts`
- Modify: `tests/playwright-single/tests/05-market.spec.ts`
- Modify: `tests/playwright-single/tests/06-drive.spec.ts`
- Modify: `tests/playwright-single/tests/07-governance.spec.ts`
- Modify: `tests/playwright-single/tests/08-im.spec.ts`

**Interfaces:**
- Produces one manifest capability tag, such as `@cap:wallet.recharge`, per covered capability in an `@regression` title.
- Produces `reports/capability-coverage.json` and `reports/capability-coverage.md` with coverage, exclusions, source operations, tags, and findings.

- [ ] **Step 1: Add the complete route map**

Add records for `/auth/login`, `/auth/register`, `/auth/password/reset`, `/posts`, `/posts/:postId`, `/search`, `/market`, `/market/listings/:listingId`, `/wallet`, `/market/publish`, `/market/my-listings`, `/market/my-listings/:listingId/inventory`, `/market/orders/buying`, `/market/orders/selling`, `/market/orders/:orderId`, `/market/addresses`, `/drive`, `/drive/s/:shareToken`, `/admin/wallet`, `/admin/market/disputes`, `/messages`, `/messages/:conversationId`, `/notices`, `/notices/:topic`, `/bookmarks`, `/analytics`, `/moderation`, `/admin/users`, `/settings`, `/users/:userId`, `/users/:userId/followees`, and `/users/:userId/followers`.

Add explicit reasons for `/`, `/preview/editorial`, all three editorial variants, `/dev`, `/403`, and `/:pathMatch(.*)*`.

- [ ] **Step 2: Add capability and operation ownership**

Run the extractor once and compare its named-import set with the following baseline before writing the manifest. Create exactly one `{ operation, capabilityIds }` record for every operation in the table; a shared operation keeps one record with multiple IDs rather than duplicate records. The spelling in the `operation` field is part of the contract.

| Module | Exact operation to capability ownership |
| --- | --- |
| `authService` | `login -> ['auth.login']`; `me -> ['auth.session-restore', 'profile.avatar']`; `logout -> ['auth.logout']`; `register`, `resendRegisterCode`, `verifyRegisterCode -> ['auth.registration']`; `issueCaptcha -> ['auth.login', 'auth.registration', 'auth.password-reset']`; `requestPasswordReset`, `confirmPasswordReset -> ['auth.password-reset']`. |
| `avatarService` | `prepareAvatarUpload`, `confirmAvatar -> ['profile.avatar']`. |
| `postService` | `listGlobalFeed`, `listBoardFeed -> ['content.feed', 'content.taxonomy-tags']`; `createPost -> ['content.publish']`; `getPostDetail -> ['content.detail']`; `updatePost`, `deletePostByAuthor -> ['content.edit-delete']`; `listComments`, `listReplies`, `addComment`, `updateComment -> ['content.comment-reply']`; `batchPostSummaries -> ['search.query']`; `moderationTop`, `moderationWonderful`, `moderationDelete -> ['governance.post-moderation']`. |
| `postMediaService` | `preparePostMediaUpload`, `uploadPostMediaFile -> ['content.media-upload']`. |
| `taxonomyService` | `listCategories`, `listHotTags`, `suggestTags -> ['content.taxonomy-tags']`. |
| `socialService` | `setLike`, `getLikeCounts`, `getLikeStatuses -> ['social.like']`; `followUser`, `unfollowUser`, `getFollowStatus -> ['social.follow']`; `listFollowees`, `listFollowers -> ['social.follow-lists']`. |
| `blockService` | `blockUser`, `unblockUser -> ['social.block', 'social.blocked-feed', 'im.block-policy']`; `listBlockedUsers -> ['social.block', 'social.blocked-feed']`. |
| `bookmarkService` | `bookmarkPost`, `unbookmarkPost`, `listBookmarks -> ['content.bookmark']`. |
| `userService` | `getUserProfile -> ['profile.read', 'growth.profile-projection']`; `listUserRecentPosts`, `listUserRecentComments -> ['profile.activity']`; `batchUserSummary -> ['content.feed', 'content.detail', 'search.query', 'profile.activity', 'social.follow-lists']`. |
| `searchService` | `searchPosts -> ['search.query']`. |
| `noticeService` | `topicSummary`, `listNotices`, `markRead -> ['notice.list-read']`. |
| `walletService` | `getWalletSummary`, `getWalletTransactions -> ['wallet.summary-history']`; `createRecharge -> ['wallet.recharge']`; `createWithdrawal -> ['wallet.withdrawal']`; `createTransfer -> ['wallet.transfer']`; `freezeWallet`, `reverseWalletTxn -> ['governance.wallet-admin']`. |
| `marketService` | `listMarketListings`, `getMarketListingDetail -> ['market.browse-detail']`; `createMarketListing -> ['market.publish']`; `listMyMarketListings -> ['market.publish', 'market.inventory']`; `listMarketInventory`, `addMarketInventory`, `invalidateMarketInventory -> ['market.inventory']`; `createMarketOrder -> ['market.order']`; `listBuyingMarketOrders`, `listSellingMarketOrders`, `getMarketOrderDetail -> ['market.order']`; `deliverMarketOrder`, `shipMarketOrder -> ['market.delivery-confirm']`; `confirmMarketOrder -> ['market.delivery-confirm']`; `cancelMarketOrder -> ['market.cancel']`; `openMarketOrderDispute -> ['governance.market-arbitration']`; `listAdminMarketDisputes`, `adminResolveMarketDispute -> ['governance.market-arbitration']`; `listMarketAddresses`, `createMarketAddress`, `updateMarketAddress`, `deleteMarketAddress -> ['market.address']`. |
| `reportService` | `createReport -> ['report.create']`. |
| `moderationService` | `listReports`, `takeAction`, `listActions -> ['governance.moderate-audit']`. |
| `adminUserService` | `adminSearchUser`, `adminUpdateUserRole -> ['governance.role-management']`. |
| `analyticsService` | `uv`, `dau -> ['governance.analytics']`. |
| `driveService` | `getDriveSpace`, `listDriveEntries -> ['drive.space-list']`; `createDriveFolder`, `searchDriveEntries`, `renameDriveEntry`, `moveDriveEntry -> ['drive.folder-move-search']`; `createDriveUploadSession`, `uploadDriveFile`, `getDriveDownloadUrl -> ['drive.upload-download']`; `listDriveTrash`, `trashDriveEntry`, `restoreDriveEntry`, `deleteDriveEntryPermanently -> ['drive.trash-recovery']`; `createDriveShare`, `getPublicDriveShare`, `verifyDriveShare`, `listDriveShareEntries`, `getDriveShareDownloadUrl -> ['drive.share-verify']`; `revokeDriveShare -> ['drive.share-revoke']`. |
| `imCoreChatService` | `createImSession -> ['im.reconnect']`; `listImConversationPage -> ['im.inbox']`; `listImConversationHistory -> ['im.send-receive', 'im.read-state']`; `markImConversationRead -> ['im.read-state']`. |

Create these route/UI-only capability records with `operations: []`; they still require their exact `@cap:` tag in a real browser test:

| Capability | Route/UI owner | Required test tag |
| --- | --- | --- |
| `smoke.frontend-reachability` | Anonymous `/posts` rendered in `00-smoke`. | `@cap:smoke.frontend-reachability` |
| `smoke.protected-route` | Anonymous navigation to `/wallet` redirects to the visible login page in `00-smoke`. | `@cap:smoke.protected-route` |
| `smoke.login-direct` | Direct `/auth/login` navigation renders the login form in `00-smoke`. | `@cap:smoke.login-direct` |
| `wallet.validation` | The visible zero-amount rejection in `/wallet` sends no request. | `@cap:wallet.validation` |
| `governance.role-guard` | Ordinary-user navigation to `/admin/users` visibly reaches `/403`. | `@cap:governance.role-guard` |

Add the following explicit exclusions rather than silently dropping source imports:

- `subscriptionService.listSubscribedCategories`: `socialPrefs.ensureSubscribedCategories` has no call from a rendered route or control, so this dormant read-side cache cannot claim a user-reachable capability. The exclusion must say that any future caller must either add a UI journey and capability or remove the unused service/store code.
- `postMediaService.inferMediaKind`: pure local MIME classification, with no request or independently observable business action; its unit test remains the owner while `content.media-upload` covers the resulting visible upload journey.

Do not create manifest records for exports not imported by the route-reachable graph, including `noticeService.unreadCount`, `imCoreChatService.listImConversations`, and `imCoreChatService.listImConversationMessages`. `auth/refreshTransport` is session infrastructure rather than an `api/services` operation; its browser outcome belongs to `auth.session-restore`, and it is never an HTTP exception for a business spec.

- [ ] **Step 3: Tag real browser journeys**

Preserve `@regression` and add exact manifest tags, for example:

~~~ts
test('anonymous posts page loads @smoke @regression @cap:smoke.frontend-reachability', async ({ page }) => {
  await gotoHash(page, '/posts')
  await expect(page.getByText('社区讨论').first()).toBeVisible()
})

test('buyer completes delivery @regression @cap:market.order @cap:market.delivery-confirm', async ({ page }) => {
  await page.getByRole('button', { name: '确认完成' }).click()
  await expect(page.getByText('已完成')).toBeVisible()
})
~~~

Do not add placeholder passing tests, page-load-only write tests, or tags on skipped tests.

- [ ] **Step 4: Run the complete capability contract**

Run:

~~~bash
npm --prefix tests/playwright-single run check:coverage
~~~

Expected: PASS with no unmapped route/operation, stale tag, duplicate owner, or forbidden direct client.

- [ ] **Step 5: Commit the complete matrix**

~~~bash
git add tests/playwright-single/coverage/capability-manifest.mjs tests/playwright-single/tests
git commit -m "test(e2e): map all user capabilities"
~~~

## Task 3: Make the Shared Isolated Runner and CI Strict

**Files:**
- Modify: `tests/playwright-single/scripts/isolated-environment.mjs`
- Modify: `tests/playwright-single/scripts/isolated-environment.test.mjs`
- Modify: `tests/playwright-single/scripts/run-isolated.mjs`
- Modify: `tests/playwright-single/scripts/run-isolated.test.mjs`
- Modify: `tests/playwright-single/package.json`
- Modify: `tests/playwright-single/package-lock.json`
- Modify: `.github/workflows/playwright-single.yml`

**Interfaces:**
- `run-isolated.mjs --suite smoke` and `run-isolated.mjs --suite regression` always execute health, `check:coverage`, the selected suite, report, diagnostics, then `down -- -v`, with generated `commandEnv` passed to every child.
- Scripts expose `typecheck`, `test:tools`, `check:coverage`, `test:isolated:smoke`, and `test:isolated:regression` without a bypass. Remove the public raw `test:smoke`, `test:regression`, and `test:headed` scripts; `test` delegates only to `test:isolated:regression`.

- [ ] **Step 1: Write failing runner lifecycle tests**

Assert a recorded smoke run orders deployment `up`, health, coverage, `playwright test --grep @smoke`, report, diagnostics, and teardown. Assert each recorded child receives the isolated `SINGLE_TEST_RUN_ID`, frontend/API/MailHog URLs, and `PW_WORKERS=1`. Assert rejection before `up` for `--grep`, `--grep-invert`, `--list`, `--shard`, `--project`, `--workers`, `--retries`, `--config`, unknown flags, and paths outside `tests/`. Add CIDR-range tests for exact, parent, and child overlaps plus an adjacent non-overlap, and a port-set test that includes `NGINX_XXL_JOB_PORT`. In a throwing suite case, assert diagnostics still run and teardown remains:

~~~js
['./deploy/deployment.sh', 'down', '--topology', 'single', '--no-observability', '--project-name', projectName, '--env-file', envFile, '--', '-v']
~~~

- [ ] **Step 2: Run red runner tests**

Run:

~~~bash
npm --prefix tests/playwright-single run test:tools
~~~

Expected: FAIL until incomplete-manifest branches and CI lifecycle duplication are removed.

- [ ] **Step 3: Implement the one lifecycle**

Give `createIsolatedEnvironment` a runner-selected `networkOctet` that writes an unoccupied `172.30.${networkOctet}.0/24` single topology, its `.10`/`.20` static peers, and matching trusted-proxy CIDRs. Give it run-specific `FRONTEND_HOST_PORT`, `NGINX_API_PORT`, `NGINX_XXL_JOB_PORT`, `NACOS_HOST_PORT`, `GARAGE_S3_HOST_PORT`, `GARAGE_ADMIN_HOST_PORT`, `MAILHOG_HOST_PORT`, and `MOCK_DATA_STUDIO_HOST_PORT`, alongside the unique volume namespace, origins, `SINGLE_MAILHOG_BASE_URL`, Nacos seed settings, and `commandEnv`. The runner converts every existing Docker network CIDR to an IPv4 range and rejects any overlap with the candidate, checks every generated host port, tries at most 32 candidates, and reports exhausted candidates rather than falling back to a shared topology. Every child command receives `{ ...process.env, ...commandEnv }`, allowing generated values to override inherited defaults. Use `try/finally`, retain the original failure after cleanup, and collect `deployment.sh ps` plus Compose logs under `reports/compose-${runId}/`.

Replace workflow environment/start/health/report/diagnostic/stop duplication with `npm --prefix tests/playwright-single run test:isolated:${PW_SUITE}`. Remove the public raw `test:smoke`, `test:regression`, and `test:headed` package scripts; keep Playwright invocation inside `run-isolated.mjs` and use `test` only as an alias to `test:isolated:regression`. Add a package-script contract test that fails if a public script runs `playwright test` without the isolated runner. CI retains `npm ci`, Chromium installation, artifact upload, and `PW_SUITE=smoke` only for PRs.

- [ ] **Step 4: Run tooling, smoke, and workflow-shape checks**

Run:

~~~bash
npm --prefix tests/playwright-single run typecheck
npm --prefix tests/playwright-single run test:tools
npm --prefix tests/playwright-single run check:coverage
npm --prefix tests/playwright-single run test:isolated:smoke
git diff --check
~~~

Expected: coverage runs before smoke, volumes are removed, and no whitespace error exists.

- [ ] **Step 5: Commit runner and CI enablement**

~~~bash
git add tests/playwright-single/scripts tests/playwright-single/package.json tests/playwright-single/package-lock.json .github/workflows/playwright-single.yml
git commit -m "test(e2e): run coverage gate in isolated CI"
~~~

## Task 4: Document the Contract and Verify Full Regression

**Files:**
- Modify: `tests/playwright-single/README.md`
- Modify: `docs/handbook/testing.md`
- Verify: `tests/playwright-single/coverage/capability-manifest.mjs`
- Verify: `.github/workflows/playwright-single.yml`

**Interfaces:**
- Documents `npm --prefix tests/playwright-single run test:isolated:smoke` and `npm --prefix tests/playwright-single run test:isolated:regression` as write-capable entrypoints.
- Documents `reports/capability-coverage.md`, `reports/capability-coverage.json`, and `reports/compose-${runId}/`.

- [ ] **Step 1: Write the documentation contract**

In the README, forbid direct backend calls in specs/fixtures, identify the health-only exception, list runner commands, explain isolated-volume cleanup, explain PR/nightly behavior, and list report paths. In the handbook, state Playwright owns user-reachable acceptance while domain/application, persistence/outbox, controller/gateway contract, and ArchUnit retain their roles.

- [ ] **Step 2: Run documentation-referenced commands**

Run:

~~~bash
npm --prefix tests/playwright-single run check:coverage
npm --prefix tests/playwright-single run test:isolated:regression
git diff --check
~~~

Expected: full regression passes through the documented lifecycle.

- [ ] **Step 3: Inspect generated reports**

Run:

~~~bash
rg -n "Unmapped|Forbidden|Excluded|@cap:" tests/playwright-single/reports/capability-coverage.md
rg -n '"complete"|"unmappedRoutes"|"forbiddenFindings"' tests/playwright-single/reports/capability-coverage.json
~~~

Expected: `complete` is true, `unmappedRoutes` and `forbiddenFindings` are empty arrays, and every exclusion has a readable reason.

- [ ] **Step 4: Commit docs and final evidence**

~~~bash
git add tests/playwright-single/README.md docs/handbook/testing.md
git commit -m "docs(testing): document capability coverage gate"
~~~
