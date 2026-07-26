# Playwright Single Coverage Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish browser-only Playwright primitives, deterministic isolated test configuration, and tested source-analysis utilities needed by every later capability journey.

**Architecture:** Keep health probing outside Playwright specs, move raw user-facing frontend transport calls behind named services, and give the test package a pure Node runner plus AST/SFC analysis layer. The runner creates a temporary Compose environment that exposes fixed captcha and registration code only in the disposable dev topology; tests still enter those values through visible UI controls.

**Tech Stack:** Playwright Test, TypeScript, `@vue/compiler-sfc`, Node built-in test runner, Vue 3/Vitest, Docker Compose, Spring/Nacos configuration.

## Global Constraints

- Do not use direct HTTP clients in `tests/playwright-single/tests/` or `fixtures/`; only `scripts/health-check.mjs` may use `fetch`.
- Do not add a test controller, direct DB fixture, or product API call to create scenario data.
- `response.request()` remains legal only after `page.waitForResponse`; it is an observation of a user-triggered request, not a client.
- Every expected `4xx` is a one-shot `allowExpectedHttpError` wrapper around a visible UI action and its visible feedback assertion; a teardown-only `expectedHttpErrors` set is not permitted.
- Every additional actor context comes from `newAuditedContext`; all of its pages participate in the same HTTP, console, and page-error audit as the default page.
- Keep test execution single-worker, non-parallel, with zero retries.
- Test-only captcha/code settings must exist only in a temporary single env file and remain rejected by the production startup validator.
- The temporary foundation gate may validate only static browser-boundary rules. The final enablement plan deletes that mode; the completed runner always invokes strict `check:coverage` before every suite.

---

## File Structure

| File | Responsibility |
| --- | --- |
| `tests/playwright-single/scripts/isolated-environment.mjs` | Pure run-ID, complete host-port set, env-file, and Compose identity construction. |
| `tests/playwright-single/scripts/run-isolated.mjs` | Lifecycle orchestration: start, health, tool checks, suite, report, diagnostics, `down -v`, and safe Playwright argument forwarding. |
| `tests/playwright-single/tsconfig.json` | Strict typecheck configuration for Playwright config, fixtures, and browser specs. |
| `tests/playwright-single/coverage/analyze.mjs` | Parse router/SFC/module graph and detect forbidden test calls. |
| `tests/playwright-single/coverage/check-coverage.mjs` | CLI entrypoint; final manifest binding is enabled in the last plan. |
| `tests/playwright-single/fixtures/mailhog.ts` | Browser-only interaction with the visible MailHog inbox for reset links. |
| `frontend/src/api/services/avatarService.js` | Avatar upload session and confirmation transport owned as a frontend API service. |
| `frontend/src/api/services/imCoreChatService.js` | IM session bootstrap alongside conversation functions. |

## Task 1: Remove Direct HTTP From Browser Specs

**Files:**
- Modify: `tests/playwright-single/fixtures/helpers.ts`
- Modify: `tests/playwright-single/fixtures/audit.ts`
- Modify: `tests/playwright-single/fixtures/test.ts`
- Create: `tests/playwright-single/fixtures/audit.test.ts`
- Modify: `tests/playwright-single/tests/00-smoke.spec.ts`
- Modify: `tests/playwright-single/tests/01-auth.spec.ts`
- Modify: `tests/playwright-single/package.json`
- Modify: `tests/playwright-single/package-lock.json`
- Create: `tests/playwright-single/coverage/analyze.mjs`
- Create: `tests/playwright-single/coverage/analyze.test.mjs`

**Interfaces:**
- Produces: `gotoHash(page: Page, hashPath: string): Promise<void>` and `appUrl(hashPath: string): string`; neither imports an HTTP request type.
- Produces: `findForbiddenTestCalls(source: string, fileName: string): Finding[]`, where `Finding` includes `fileName`, `line`, and `rule`.
- Produces `allowExpectedHttpError(page, entry, performUiAction, assertVisible): Promise<void>`. It normalizes the method, accepts only a concrete `/api/...` path and a `400`-`499` status, rejects wildcard paths and all `5xx` statuses, arms one exact response before `performUiAction`, consumes exactly that one post-registration response, and requires `assertVisible` to pass before completing.
- Produces `newAuditedContext(): Promise<BrowserContext>`; it audits every initial and subsequently opened page in the returned context, registers the context with the current test, and closes it after the test while merging its failures into the test audit.

- [ ] **Step 1: Write the failing direct-client guard test**

Create `coverage/analyze.test.mjs` with a test that parses this fixture text and expects four findings:

```js
const source = `
  test('bad', async ({ page, request }) => {
    await request.get('/api/posts')
    await page.request.post('/api/posts')
    await fetch('/api/posts')
    await page.evaluate(() => fetch('/api/posts'))
  })
`
assert.deepEqual(
  findForbiddenTestCalls(source, 'tests/bad.spec.ts').map((item) => item.rule),
  ['playwright-request-fixture', 'page-request', 'node-fetch', 'page-evaluate-fetch']
)
```

Add source fixtures for `test.skip`, `test.fixme`, `test.describe.skip`, and `test.describe.fixme`, each of which must be rejected. Add a route-reachable Vue fixture with both `<script setup>` and ordinary `<script>` imports, including `import { login as apiLogin } from '@/api/services/authService'`; assert the recorded operation is `authService.login`, not the local alias.

Create `fixtures/audit.test.ts` using fake `BrowserContext`/`Page` event emitters. Verify an action-wrapped exact `403` is consumed once only after its visible assertion, an unused registration fails, a second matching response fails, and a `401` emitted by a page opened after `newAuditedContext()` is reported at test teardown. Add `tsx` as a direct dev dependency and run this TypeScript test through `tsx --test`.

- [ ] **Step 2: Run the test to verify it fails**

Run:

```bash
node --test tests/playwright-single/coverage/analyze.test.mjs
```

Expected: FAIL because `coverage/analyze.mjs` and `findForbiddenTestCalls` do not exist.

- [ ] **Step 3: Implement the AST guard and remove the current violation**

Implement `findForbiddenTestCalls` with `typescript.createSourceFile`. Match the destructured Playwright `request` parameter only when it invokes `get`, `post`, `put`, `patch`, `delete`, or `fetch`; do not flag `response.request()`. Match `page.request.*`, identifier `fetch`, axios imports/calls, imports from `frontend/src/api`, and `page.evaluate` callbacks containing `fetch` or `XMLHttpRequest`. Also reject every skip/fixme/fail form on `test` or `test.describe`. When collecting service operations, parse both ordinary and setup SFC scripts, resolve aliases recursively, and record the imported export name rather than the local binding name.

Replace the set-based `expectedHttpErrors` fixture with an audit owned by the test context. `createApiErrorAudit` attaches to a context, attaches to every current/future page, records all API responses and browser errors, and tracks each armed expected response by registration sequence. `allowExpectedHttpError(page, entry, performUiAction, assertVisible)` validates `entry`, installs `page.waitForResponse` before calling `performUiAction`, waits for exactly one matching post-registration response, runs `assertVisible`, and marks that response consumed. At teardown, fail for an unused arm, duplicate match, any unarmed `4xx`/`5xx`, request failure, console error, or page error. Reject `'*'`, `?`, non-`/api/` paths, non-`4xx` statuses, and concurrent duplicate arms. Do not expose `expectedHttpErrors`, `test.use({ expectedHttpErrors })`, or a direct mutation API.

Extend `fixtures/test.ts` with `allowExpectedHttpError` and `newAuditedContext`. The latter is the only approved way for a spec to create another logged-in actor; close each returned context in fixture teardown after collecting its audit failures. Keep the audit helper's callbacks limited to browser UI actions and visible assertions.

Remove `APIRequestContext`, `expectGatewayHealthy`, and `expectFrontendReachable` from `fixtures/helpers.ts`. Change `00-smoke.spec.ts` so its first test opens `/posts` in a browser and asserts `社区讨论`, while `health-check.mjs` remains the health command. Keep response observation in later specs only after a UI action.

- [ ] **Step 4: Run the guard and browser-only smoke list**

Run:

```bash
node --test tests/playwright-single/coverage/analyze.test.mjs
npm --prefix tests/playwright-single exec -- tsx --test fixtures/audit.test.ts
npm --prefix tests/playwright-single run test:smoke -- --list
```

Expected: the AST guard passes and the smoke list includes no test using the Playwright `request` fixture.

- [ ] **Step 5: Commit the browser boundary**

```bash
git add tests/playwright-single/fixtures/helpers.ts tests/playwright-single/fixtures/audit.ts tests/playwright-single/fixtures/audit.test.ts tests/playwright-single/fixtures/test.ts tests/playwright-single/tests/00-smoke.spec.ts tests/playwright-single/coverage/analyze.mjs tests/playwright-single/coverage/analyze.test.mjs tests/playwright-single/package.json tests/playwright-single/package-lock.json
git commit -m "test(e2e): prohibit direct browser test APIs"
```

## Task 2: Add Disposable Accounts and Test-Only Deployment Inputs

**Files:**
- Modify: `deploy/mysql/community/090_seed_identity.sql`
- Modify: `deploy/nacos/config/community-app.yaml`
- Modify: `deploy/nacos/config/community-gateway.yaml`
- Modify: `deploy/nacos/config/community-im-gateway.yaml`
- Modify: `deploy/nacos/config/im-core.yaml`
- Modify: `deploy/nacos/config/community-frontend-runtime.yaml`
- Modify: `deploy/nacos/seed-configs.sh`
- Modify: `deploy/compose.infra.nacos.single.yml`
- Modify: `deploy/compose.infra.mailhog.yml`
- Modify: `deploy/.env.single.example`
- Modify: `deploy/tests/nacos_config_seed.sh`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/config/NacosPolicyBindingTest.java`
- Modify: `tests/playwright-single/fixtures/accounts.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Create: `tests/playwright-single/tsconfig.json`
- Modify: `tests/playwright-single/package.json`
- Create: `tests/playwright-single/fixtures/mailhog.ts`
- Test: `tests/playwright-single/scripts/isolated-environment.test.mjs`

**Interfaces:**
- Produces `accounts.candidate` with UUID `00000000-0000-7000-8000-000000000004`, username `ccc`, password `aaa`, and `.auth/ccc.json` storage path.
- Produces `mailhogBaseUrl` from `SINGLE_MAILHOG_BASE_URL` and `openResetLinkFromMailbox(page, recipient): Promise<string>` using only visible browser UI.
- Produces `createIsolatedEnvironment({ repoRoot, runId, tempRoot, portOffset, networkOctet }): Promise<IsolatedEnvironment>` with `projectName`, `envFile`, `webBaseUrl`, `apiBaseUrl`, `mailhogBaseUrl`, `commandEnv`, the complete unique host-port set, and an independent single-topology network. `commandEnv` contains the generated `SINGLE_TEST_RUN_ID`, `SINGLE_WEB_BASE_URL`, `SINGLE_API_BASE_URL`, `SINGLE_MAILHOG_BASE_URL`, and forced `PW_WORKERS=1` values used by every runner child process.
- Produces a Nacos seed render contract: only the Nacos bootstrap service receives `NACOS_SEED_WEB_ORIGIN`, `NACOS_SEED_GATEWAY_ORIGIN`, `NACOS_SEED_PUBLIC_WS_URL`, `NACOS_SEED_AUTH_CAPTCHA_FIXED_CODE`, `NACOS_SEED_AUTH_REGISTRATION_EXPOSE_CODE`, and `NACOS_SEED_AUTH_PASSWORD_RESET_BASE_URL`; product runtime services receive none of those settings directly.
- Produces `npm --prefix tests/playwright-single run typecheck` through a strict `tsconfig.json` with `moduleResolution: "Bundler"`, `module: "ESNext"`, `target: "ES2022"`, `strict: true`, and `noEmit: true`.

- [ ] **Step 1: Write red configuration and environment tests**

Add a `NacosPolicyBindingTest` case using its existing `environmentFrom` helper:

```java
StandardEnvironment environment = environmentFrom("community-app.yaml", Map.of(
        "AUTH_CAPTCHA_FIXED_CODE", "2468",
        "AUTH_REGISTRATION_EXPOSE_CODE", "true",
        "AUTH_PASSWORD_RESET_BASE_URL", "http://localhost:13981"
));
assertThat(environment.getProperty("auth.captcha.fixed-code")).isEqualTo("2468");
assertThat(environment.getProperty("auth.registration.code.expose-code", Boolean.class)).isTrue();
assertThat(environment.getProperty("auth.password-reset.reset-base-url")).isEqualTo("http://localhost:13981");
```

Add a Node test that calls `createIsolatedEnvironment` with `runId: 'pw-42'`, `portOffset: 42`, and `networkOctet: 42`, and asserts a non-default project/volume namespace plus `NACOS_SEED_AUTH_CAPTCHA_FIXED_CODE=2468`, `NACOS_SEED_AUTH_REGISTRATION_EXPOSE_CODE=true`, and a run-specific MailHog host port in the written env file. Assert that `FRONTEND_HOST_PORT`, `NGINX_API_PORT`, `NGINX_XXL_JOB_PORT`, `NACOS_HOST_PORT`, `GARAGE_S3_HOST_PORT`, `GARAGE_ADMIN_HOST_PORT`, `MAILHOG_HOST_PORT`, and `MOCK_DATA_STUDIO_HOST_PORT` are all non-default and pairwise distinct. Assert the exact independent topology values `COMMUNITY_NETWORK_SUBNET=172.30.42.0/24`, `COMMUNITY_NETWORK_DYNAMIC_RANGE=172.30.42.128/25`, `NGINX_STATIC_IP=172.30.42.10`, `COMMUNITY_GATEWAY_STATIC_IP=172.30.42.20`, `GATEWAY_TRUSTED_PROXY_CIDRS=172.30.42.10/32`, and `COMMUNITY_APP_TRUSTED_PROXY_CIDRS=172.30.42.20/32`. Assert `FRONTEND_PUBLIC_ORIGIN`, `GATEWAY_PUBLIC_BASE_URL`, `NACOS_SEED_WEB_ORIGIN`, `NACOS_SEED_GATEWAY_ORIGIN`, `NACOS_SEED_PUBLIC_WS_URL`, and `NACOS_SEED_AUTH_PASSWORD_RESET_BASE_URL` use the generated ports. Assert `commandEnv` contains the same `SINGLE_*` URLs/run ID and forces `PW_WORKERS=1` after inherited process environment values.

Extend `deploy/tests/nacos_config_seed.sh` to call `seed-configs.sh --render-only` with a generated frontend origin, Gateway origin, public WebSocket URL, captcha, registration setting, and reset URL. Assert its rendered `community-app.yaml`, `community-gateway.yaml`, `community-im-gateway.yaml`, `im-core.yaml`, and `community-frontend-runtime.yaml` contain those exact generated values at the origin/CORS/WebSocket/auth keys; inspect the rendered single Compose config to prove the values are supplied to `nacos-config-bootstrap`, not `community-app`, Gateway, IM Gateway, or IM Core.

- [ ] **Step 2: Run the red tests**

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=NacosPolicyBindingTest test
cd ..
bash deploy/tests/nacos_config_seed.sh
node --test tests/playwright-single/scripts/isolated-environment.test.mjs
```

Expected: FAIL because the Nacos values are currently hard-coded/missing and the isolated environment module does not exist.

- [ ] **Step 3: Implement controlled dev configuration and identity baseline**

In `community-app.yaml`, make the three settings environment-resolved so the existing Java binding test can verify them before seed rendering:

```yaml
auth:
  captcha:
    fixed-code: ${AUTH_CAPTCHA_FIXED_CODE:}
  registration:
    code:
      expose-code: ${AUTH_REGISTRATION_EXPOSE_CODE:false}
  password-reset:
    reset-base-url: ${AUTH_PASSWORD_RESET_BASE_URL:http://localhost:12881}
```

Keep the Nacos source files as templates, then make `seed-configs.sh` copy them to a writable temporary directory and render only explicit seed markers before publishing. Keep the historical localhost CORS entries and append a `__SINGLE_WEB_ORIGIN__` marker to `community-app.yaml`, `community-gateway.yaml`, `community-im-gateway.yaml`, and `im-core.yaml`; render it from `NACOS_SEED_WEB_ORIGIN`. Render `community-im-gateway.yaml`'s public WebSocket URL and `community-frontend-runtime.yaml`'s Gateway/WebSocket URLs from `NACOS_SEED_GATEWAY_ORIGIN` and `NACOS_SEED_PUBLIC_WS_URL`. Before publishing `community-app.yaml`, replace only its three auth placeholders with the three `NACOS_SEED_AUTH_*` values. Add `--render-only` to the seed script for deterministic contract tests; it writes the rendered directory and performs no HTTP call. Pass the `NACOS_SEED_*` variables only to `nacos-config-bootstrap` in `compose.infra.nacos.single.yml`; do not pass `AUTH_*`, CORS, origin-guard, or IM endpoint variables to any product runtime service. Change the MailHog host binding to `127.0.0.1:${MAILHOG_HOST_PORT:-8025}:8025`. Add `ccc` to the dev seed with the same known bcrypt hash as `aaa`, ordinary user type, active status, and the fixed UUID above. Do not add an HTTP test endpoint.

Implement `isolated-environment.mjs` with `fs.mkdtemp`, a copied `deploy/.env.single.example`, and run-scoped values for project, volumes, network, origins, `SINGLE_TEST_RUN_ID`, and the Nacos seed inputs. For a validated `networkOctet` in `1..254`, write `172.30.${networkOctet}.0/24`, `172.30.${networkOctet}.128/25`, `.10`, `.20`, and their matching `/32` trusted CIDRs into the six single-topology variables. Derive every host binding used by the no-observability single topology from the same `portOffset`: `FRONTEND_HOST_PORT`, `NGINX_API_PORT`, `NGINX_XXL_JOB_PORT`, `NACOS_HOST_PORT`, `GARAGE_S3_HOST_PORT`, `GARAGE_ADMIN_HOST_PORT`, `MAILHOG_HOST_PORT`, and `MOCK_DATA_STUDIO_HOST_PORT`. Keep internal container ports unchanged. Set `FRONTEND_PUBLIC_ORIGIN`, `GATEWAY_PUBLIC_BASE_URL`, `NACOS_SEED_WEB_ORIGIN`, `NACOS_SEED_GATEWAY_ORIGIN`, `NACOS_SEED_PUBLIC_WS_URL`, and `NACOS_SEED_AUTH_PASSWORD_RESET_BASE_URL` from the generated frontend/API ports. Return a `commandEnv` object with those generated `SINGLE_*` values, run ID, and enforced `PW_WORKERS=1`; every runner child receives it as overrides after `process.env`. Implement `mailhog.ts` by opening `SINGLE_MAILHOG_BASE_URL`, selecting the visible recipient message, and reading the visible reset hyperlink; it must not call the MailHog API.

Create `tsconfig.json` with `target: "ES2022"`, `module: "ESNext"`, `moduleResolution: "Bundler"`, `strict: true`, `noEmit: true`, `skipLibCheck: true`, and includes for `playwright.config.ts`, `fixtures/**/*.ts`, and `tests/**/*.ts`. Add `"typecheck": "tsc -p tsconfig.json"` to the test package scripts.

- [ ] **Step 4: Run configuration and fixture checks**

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=NacosPolicyBindingTest test
cd ..
node --test tests/playwright-single/scripts/isolated-environment.test.mjs
bash deploy/tests/nacos_config_seed.sh
npm --prefix tests/playwright-single run typecheck
```

Expected: configuration placeholders bind under controlled overrides, the generated env is isolated, and new TypeScript fixtures type-check.

- [ ] **Step 5: Commit the isolated baseline**

```bash
git add deploy backend/community-app/src/test/java/com/nowcoder/community/config/NacosPolicyBindingTest.java tests/playwright-single/fixtures tests/playwright-single/scripts/isolated-environment.mjs tests/playwright-single/scripts/isolated-environment.test.mjs tests/playwright-single/tsconfig.json tests/playwright-single/package.json tests/playwright-single/package-lock.json
git commit -m "test(e2e): add isolated single test identities"
```

## Task 3: Normalize User-Facing Frontend Transport Services

**Files:**
- Create: `frontend/src/api/services/avatarService.js`
- Create: `frontend/src/api/services/avatarService.test.js`
- Modify: `frontend/src/views/SettingsView.vue`
- Modify: `frontend/src/views/SettingsView.test.js`
- Modify: `frontend/src/api/services/imCoreChatService.js`
- Modify: `frontend/src/api/services/imCoreChatService.test.js`
- Modify: `frontend/src/im/imRealtimeClient.js`
- Modify: `frontend/src/im/imRealtimeClient.test.js`
- Modify: `frontend/src/components/layout/Topbar.vue`
- Create: `frontend/src/components/layout/Topbar.test.js`

**Interfaces:**
- Produces `prepareAvatarUpload(userId, file): Promise<{ data, traceId }>` and `confirmAvatar(userId, objectId): Promise<{ traceId }>`.
- Produces `createImSession(accessToken): Promise<{ wsUrl: string, ticket: string }>`.
- Consumes the existing `logout(): Promise<{ traceId }>` from `authService.js`.

- [ ] **Step 1: Write failing service tests**

Add tests with mocked `http`/`imCoreHttp` that require these exact calls:

```js
await prepareAvatarUpload(USER_ID, file)
expect(http.post).toHaveBeenCalledWith(`/api/users/${USER_ID}/avatar/upload-sessions`, expect.objectContaining({
  fileName: file.name,
  contentType: file.type,
  contentLength: file.size
}))

await createImSession('access-token')
expect(imCoreHttp.post).toHaveBeenCalledWith('/api/im/sessions', null, {
  headers: { Authorization: 'Bearer access-token' }
})
```

- [ ] **Step 2: Run the red frontend tests**

Run:

```bash
npm --prefix frontend test -- src/api/services/avatarService.test.js src/api/services/imCoreChatService.test.js src/im/imRealtimeClient.test.js src/views/SettingsView.test.js src/components/layout/Topbar.test.js
```

Expected: FAIL because the avatar/session APIs and Topbar contract have not yet been extracted.

- [ ] **Step 3: Implement named service ownership**

Move the two avatar HTTP calls out of `SettingsView.vue` into `avatarService.js`; keep `executeUploadSession` in the view because the browser owns the selected `File`. Add `createImSession` to `imCoreChatService.js` and replace the direct `imCoreHttp.post('/api/im/sessions', ...)` in `imRealtimeClient.js`. Replace Topbar's direct logout post with `authService.logout`. Preserve access-token/header semantics, result unwrapping, trace propagation, and the existing UI error behavior.

- [ ] **Step 4: Run frontend regression tests**

Run:

```bash
npm --prefix frontend test -- src/api/services/avatarService.test.js src/api/services/imCoreChatService.test.js src/im/imRealtimeClient.test.js src/views/SettingsView.test.js src/components/layout/Topbar.test.js
```

Expected: all named service contracts pass and no non-dev route-reachable module imports raw `api/http` or `api/imCoreHttp` directly.

- [ ] **Step 5: Commit transport normalization**

```bash
git add frontend/src/api/services frontend/src/views/SettingsView.vue frontend/src/im/imRealtimeClient.js frontend/src/components/layout/Topbar.vue frontend/src/components/layout/Topbar.test.js
git commit -m "refactor(frontend): name user-facing API operations"
```

## Task 4: Rename the Numbered Browser Suite Before Adding New Journeys

**Files:**
- Move: `tests/playwright-single/tests/02-community.spec.ts` to `tests/playwright-single/tests/02-content-social-profile.spec.ts`
- Move: `tests/playwright-single/tests/03-wallet.spec.ts` to `tests/playwright-single/tests/04-wallet.spec.ts`
- Move: `tests/playwright-single/tests/04-market.spec.ts` to `tests/playwright-single/tests/05-market.spec.ts`
- Move: `tests/playwright-single/tests/05-drive.spec.ts` to `tests/playwright-single/tests/06-drive.spec.ts`
- Move: `tests/playwright-single/tests/06-admin.spec.ts` to `tests/playwright-single/tests/07-governance.spec.ts`
- Move: `tests/playwright-single/tests/07-im.spec.ts` to `tests/playwright-single/tests/08-im.spec.ts`
- Modify: `tests/playwright-single/README.md`

**Interfaces:**
- Produces the stable final journey sequence `00-smoke`, `01-auth`, `02-content-social-profile`, `03-notice-search-growth`, `04-wallet`, `05-market`, `06-drive`, `07-governance`, and `08-im`.

- [ ] **Step 1: Write the expected final discovery list**

Add a `coverage/analyze.test.mjs` assertion that the final `tests/` directory contains the nine stable basenames above and contains none of `02-community.spec.ts`, `03-wallet.spec.ts`, `04-market.spec.ts`, `05-drive.spec.ts`, `06-admin.spec.ts`, or `07-im.spec.ts`.

- [ ] **Step 2: Run the discovery assertion to verify failure**

Run:

```bash
node --test tests/playwright-single/coverage/analyze.test.mjs
```

Expected: FAIL because the legacy numbered names still exist.

- [ ] **Step 3: Move names in collision-safe descending order**

Run exactly these commands so no destination is overwritten:

```bash
git mv tests/playwright-single/tests/07-im.spec.ts tests/playwright-single/tests/08-im.spec.ts
git mv tests/playwright-single/tests/06-admin.spec.ts tests/playwright-single/tests/07-governance.spec.ts
git mv tests/playwright-single/tests/05-drive.spec.ts tests/playwright-single/tests/06-drive.spec.ts
git mv tests/playwright-single/tests/04-market.spec.ts tests/playwright-single/tests/05-market.spec.ts
git mv tests/playwright-single/tests/03-wallet.spec.ts tests/playwright-single/tests/04-wallet.spec.ts
git mv tests/playwright-single/tests/02-community.spec.ts tests/playwright-single/tests/02-content-social-profile.spec.ts
```

Update any README filename references in the same change; do not edit test behavior during this rename task.

- [ ] **Step 4: Run discovery and Playwright list checks**

Run:

```bash
node --test tests/playwright-single/coverage/analyze.test.mjs
cd tests/playwright-single
npm exec -- playwright test --list
cd ../..
```

Expected: the stable names are found and all tests remain discoverable without a running topology.

- [ ] **Step 5: Commit the stable suite names**

```bash
git add tests/playwright-single/tests tests/playwright-single/README.md tests/playwright-single/coverage/analyze.test.mjs
git commit -m "test(e2e): name browser journeys by capability"
```

## Task 5: Build the Coverage Analysis and Isolated Runner

**Files:**
- Create: `tests/playwright-single/coverage/check-coverage.mjs`
- Create: `tests/playwright-single/scripts/run-isolated.mjs`
- Create: `tests/playwright-single/scripts/run-isolated.test.mjs`
- Modify: `tests/playwright-single/coverage/analyze.mjs`
- Modify: `tests/playwright-single/coverage/analyze.test.mjs`
- Modify: `tests/playwright-single/package.json`
- Modify: `tests/playwright-single/package-lock.json`

**Interfaces:**
- Produces `collectRouteEntries(routerFile)`, `collectReachableServiceOperations(entryFiles)`, `findForbiddenTestCalls`, and `validateCapabilityManifest({ routes, operations, capabilities, exclusions, listedTitles })`.
- Produces CLI commands `typecheck`, `test:tools`, `check:coverage:foundation`, `test:isolated:smoke`, and `test:isolated:regression`; the two isolated commands accept only normalized relative `tests/**/*.spec.ts` positional filters after `--` and always retain their mandatory `@smoke` or `@regression` grep.

- [ ] **Step 1: Write red AST and runner tests**

Add `@vue/compiler-sfc` as a direct Playwright dev dependency. In `analyze.test.mjs`, create temporary Vue files where `A.vue` imports `Child.vue`, `Child.vue` imports `{ listDriveEntries }` from `api/services/driveService`, and assert the recursively collected operation equals `driveService.listDriveEntries`. Add a `<script setup>` fixture that aliases `login as apiLogin`, plus an ordinary `<script>` fixture, and assert the collector records `authService.login` by exported symbol. Add skip/fixme source fixtures for `test.skip`, `test.fixme`, `test.describe.skip`, and `test.describe.fixme`; each must produce a forbidden-test finding. Add a route fixture with a product route and an excluded preview route, then assert `validateCapabilityManifest` rejects the product route when it has no capability.

In `run-isolated.test.mjs`, inject a recording `runCommand` into `runIsolated` and assert that `runIsolated({ suite: 'regression', playwrightArgs: ['tests/04-wallet.spec.ts'] })` invokes `playwright test --grep @regression tests/04-wallet.spec.ts` rather than dropping the file filter. Assert every child command, including health, coverage, Playwright, report, diagnostics, and teardown, receives `isolated.commandEnv` with the generated `SINGLE_TEST_RUN_ID`, URLs, MailHog URL, and `PW_WORKERS=1`. Reject `--grep`, `--grep-invert`, `--list`, `--shard`, `--project`, `--workers`, `--retries`, `--config`, a path outside `tests/`, and an unrecognized option before `up`. Add CIDR tests proving the candidate rejects an exact `/24`, a containing `/16`, and a contained `/25`, while accepting an adjacent non-overlapping `/24`. Also assert the command order ends with:

```js
['./deploy/deployment.sh', 'down', '--topology', 'single', '--no-observability', '--project-name', projectName, '--env-file', envFile, '--', '-v']
```

even when the injected Playwright command throws.

- [ ] **Step 2: Run the red tool tests**

Run:

```bash
npm --prefix tests/playwright-single install
npm --prefix tests/playwright-single run test:tools
```

Expected: FAIL until SFC parsing, recursive traversal, manifest validation, and `finally` cleanup are implemented.

- [ ] **Step 3: Implement source analysis and lifecycle orchestration**

Use `@vue/compiler-sfc.parse` to extract each SFC's ordinary and setup scripts and TypeScript AST to resolve import declarations recursively from route view modules and `frontend/src/App.vue`. Record only imports under `frontend/src/api/services/`, using the original named export rather than its local alias; fail route-reachable raw `api/http` or `api/imCoreHttp` imports outside the explicitly excluded `/dev` graph. Scan test source for `test.skip`, `test.fixme`, `test.fail`, and their `test.describe` variants as well as direct HTTP clients.

Implement `run-isolated.mjs` with `--suite smoke` or `--suite regression`; it accepts optional normalized test-file paths after `--`, such as `-- tests/04-wallet.spec.ts`, and rejects every forwarded flag. Define `test:tools` to run the AST/SFC guard, `fixtures/audit.test.ts` through `tsx --test`, and runner/environment unit tests; make `check:coverage:foundation` invoke that command. In `try/finally`, select a `networkOctet` from a hash of the run ID and try up to 32 successive octets before creating the env. Convert every Docker network CIDR and each `172.30.${networkOctet}.0/24` candidate to inclusive IPv4 integer ranges, rejecting when `candidateStart <= existingEnd && existingStart <= candidateEnd`; equality alone is insufficient. Reserve every generated host binding, including `NGINX_XXL_JOB_PORT`, before proceeding. Pass the selected `networkOctet` and `portOffset` to `createIsolatedEnvironment`, then invoke `deployment.sh up`, health, coverage, Playwright, report, diagnostics, and `down -- -v` with `{ env: { ...process.env, ...isolated.commandEnv } }`, so generated values override inherited defaults. Run `playwright test --grep @smoke` for smoke or `playwright test --grep @regression` for regression followed only by validated spec paths. Generate the Markdown report, collect `ps`/logs under `reports/compose-${runId}/`, and call `down -- -v` in `finally`. If 32 candidates collide, fail before `up` with the candidate ranges and ports in the diagnostic report. `check:coverage:foundation` runs the direct-client guard and source-analysis unit checks only; it does not claim complete capability mapping. The final enablement plan removes `--coverage foundation`, deletes `check:coverage:foundation`, and makes `check:coverage` unconditional before every suite.

- [ ] **Step 4: Run tool checks**

Run:

```bash
npm --prefix tests/playwright-single run test:tools
npm --prefix tests/playwright-single run typecheck
cd tests/playwright-single
npm exec -- playwright test --list
cd ../..
```

Expected: source parsing and cleanup tests pass; Playwright still discovers the current browser tests without starting a topology.

- [ ] **Step 5: Commit the tool foundation**

```bash
git add tests/playwright-single/package.json tests/playwright-single/package-lock.json tests/playwright-single/tsconfig.json tests/playwright-single/coverage tests/playwright-single/scripts
git commit -m "test(e2e): add capability analysis tools"
```
