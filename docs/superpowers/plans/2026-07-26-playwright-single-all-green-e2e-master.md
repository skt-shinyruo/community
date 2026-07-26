# Playwright Single 全绿 E2E Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复 single 部署中被 Playwright 暴露的真实产品/配置错误，并让 `tests/playwright-single` 的 smoke 与完整 regression 都按成功语义通过。

**Architecture:** 先加固统一 runner、错误审计和 CI 生命周期，再按收藏、IM、后台、网盘四条垂直链路分别修复。后端改动沿 Controller -> ApplicationService -> domain/repository -> infrastructure 进入；Playwright 只通过前端和 Gateway 验证，不把状态码改写为空成功。

**Tech Stack:** Playwright Test、TypeScript、Vue 3/Vitest、Spring Boot、Spring Security、MyBatis、JUnit 5、GitHub Actions、Docker Compose。

## Global Constraints

- `tests/playwright-single/tests/` 的业务用例全部使用成功语义；不得新增或保留 `test.fail`、`test.skip`、永久忽略 spec、无限重试或“预期 503/403”通过条件。
- 合法结果必须保留：匿名访问 `/wallet` 跳转登录，普通用户访问 `/admin/users` 精确得到 `403`。
- Playwright 只运行单 Chromium、单 worker、关闭完全并行；业务测试不自动重试。
- 所有写入测试数据携带 `SINGLE_TEST_RUN_ID`；CI 使用隔离 Compose project、volume namespace、网络和临时配置。
- 失败必须保留 trace、截图、视频、list/HTML/JSON/Markdown 报告、Compose 状态和服务日志。
- backend 业务代码必须遵守 `/home/feng/code/project/community/AGENTS.md` 的严格 DDD Tactical Layering；不让 Controller 吞掉持久化异常，也不放宽认证或授权。
- 不覆盖用户现有改动：`LikeApplicationService.java`、`LikeApplicationServiceTest.java`、`tests/k6/README.md`、`tests/playwright-single/README.md`、`tests/playwright-single/tests/02-community.spec.ts` 和未跟踪的 `test-results/`。

---

## Plan Map

| 子计划 | 交付物 |
| --- | --- |
| `2026-07-26-playwright-single-runner-ci.md` | 统一标签、错误审计、健康轮询、报告和 PR/nightly workflow |
| `2026-07-26-playwright-single-bookmarks.md` | 收藏 SQL、应用组装测试和社区收藏 E2E |
| `2026-07-26-playwright-single-im.md` | IM CORS 绑定、Gateway route 覆盖和 `07-im.spec.ts` |
| `2026-07-26-playwright-single-admin.md` | 后台主体/空状态、管理员成功页面和普通用户精确 403 |
| `2026-07-26-playwright-single-drive.md` | 公开分享校验、ticket/目录读取和 Drive E2E |

## Execution Order

### Task 1: Preserve the Existing Worktree and Establish the Baseline

**Files:**
- Read: `backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java`
- Read: `backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java`
- Read: `tests/playwright-single/tests/02-community.spec.ts`
- Preserve: `test-results/`

- [ ] **Step 1: Record the current diff without reverting it**

```bash
git status --short
git diff -- backend/community-app/src/main/java/com/nowcoder/community/social/application/LikeApplicationService.java backend/community-app/src/test/java/com/nowcoder/community/social/application/LikeApplicationServiceTest.java tests/playwright-single/tests/02-community.spec.ts
```

Expected: the three user-owned edits and `test-results/` remain present before any implementation edit.

- [ ] **Step 2: Run the current health probe**

```bash
npm --prefix tests/playwright-single run health
```

Expected: frontend returns HTTP 200 and Gateway health reports `UP`. A failure here is an environment blocker and must be diagnosed before interpreting E2E failures.

### Task 2: Implement the Shared Runner and CI Contract

**Files:**
- Modify: `tests/playwright-single/package.json`
- Modify: `tests/playwright-single/playwright.config.ts`
- Modify: `tests/playwright-single/scripts/health-check.mjs`
- Create: `tests/playwright-single/fixtures/audit.ts`
- Create: `tests/playwright-single/fixtures/test.ts`
- Modify: `tests/playwright-single/tests/00-smoke.spec.ts`
- Modify: `tests/playwright-single/tests/01-auth.spec.ts`
- Modify: `tests/playwright-single/tests/02-community.spec.ts`
- Modify: `tests/playwright-single/tests/03-wallet.spec.ts`
- Modify: `tests/playwright-single/tests/04-market.spec.ts`
- Modify: `tests/playwright-single/tests/05-drive.spec.ts`
- Modify: `tests/playwright-single/tests/06-admin.spec.ts`
- Create: `tests/playwright-single/tests/07-im.spec.ts`
- Delete: `tests/playwright-single/tests/99-known-issues.spec.ts`
- Create: `.github/workflows/playwright-single.yml`

- [ ] **Step 1: Make the new command contract fail before implementation**

```bash
npm --prefix tests/playwright-single run test:regression
```

Expected before the change: npm reports that `test:regression` does not exist. This is the red check for the runner migration.

- [ ] **Step 2: Add the exact runner behavior**

The implementation must make these scripts available:

```json
{
  "test:smoke": "playwright test --grep @smoke",
  "test:regression": "playwright test --grep @regression",
  "test": "npm run test:regression",
  "report": "node scripts/markdown-report.mjs",
  "show-report": "playwright show-report playwright-report"
}
```

Remove `test:known` and remove `testIgnore`/`PW_INCLUDE_KNOWN_ISSUES` handling from `playwright.config.ts`. Keep `fullyParallel: false`, one Chromium project, `workers` defaulting to `1`, `trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`, and `video: 'retain-on-failure'`.

- [ ] **Step 3: Add the bounded network-error audit**

`fixtures/audit.ts` must expose an `ApiErrorAudit` that records `{ method, url, status }` for the configured single API origin, records every `pageerror`, and records application `console.error`. Its allowlist type must require all three fields below:

```ts
export type ExpectedHttpError = {
  method: string
  path: string
  status: number
}
```

At test end, fail when any API response has status `>= 500` or status `400..499` not matched by the test-scoped allowlist, or when a page error/application console error was recorded. The only permitted HTTP allowlist entries are exact method/path/status triples for the anonymous auth probe and the ordinary-user admin authorization probe; do not add a wildcard path or a status range.

- [ ] **Step 4: Mark every test and migrate imports**

Every `describe` and `test` title must contain `@smoke` or `@regression`; smoke tests also contain both tags. All specs import `test` from `../fixtures/test` and `expect` from `@playwright/test` or the shared fixture module consistently. The community composer locator remains the user-approved `.posts-feed-compose-strip` locator and the market flow keeps no module-level URL state.

- [ ] **Step 5: Add and validate the new IM spec**

Create `07-im.spec.ts` with an authenticated `bbb` test that waits for `GET /api/im/conversations/page`, asserts HTTP `200`, asserts the JSON `data.items` array, opens `/messages`, and asserts either `暂无会话` or a rendered conversation link. Assert that no `.error`/UiState error text is visible after the request completes.

- [ ] **Step 6: Run the runner checks**

```bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test:regression
npm --prefix tests/playwright-single run report
```

Expected: no spec is ignored, no test is skipped, `reports/latest-results.json` is produced, and the Markdown report counts any failure instead of converting it to passed.

- [ ] **Step 7: Add the CI lifecycle**

`.github/workflows/playwright-single.yml` must have `pull_request`, a nightly cron, and `workflow_dispatch`. PR runs `test:smoke`; nightly/manual runs `test:regression`. Both paths use `npm ci`, `npx playwright install --with-deps chromium`, the same `health` command, the same artifact paths, and `deployment.sh up/down --topology single --no-observability` with the same project and env file. Use `github.run_id` in `SINGLE_TEST_RUN_ID`, Compose project, volume namespace, and generated network/static IP values. Upload `playwright-report/`, `test-results/`, `reports/`, and collected Compose logs with `if: always()` before the matching `down` command.

### Task 3: Fix the Bookmark Vertical Slice

**Files:**
- Modify: `backend/community-app/src/main/resources/mapper/bookmark-mapper.xml`
- Create: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/mapper/BookmarkMapperPersistenceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/application/BookmarkApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/content/infrastructure/persistence/BookmarkServiceTest.java`
- Modify: `tests/playwright-single/tests/02-community.spec.ts`

The failing SQL is known: `discuss_post` in `deploy/mysql/community/040_schema_content_core.sql` has no `content` column, while the bookmark projection selects `p.content`. Remove that projection field; keep `title` and the persisted status/timestamp/counter/deletion columns used by `DiscussPost`.

- [ ] **Step 1: Add the red mapper integration assertion**

Insert an active row into `discuss_post`, insert the corresponding `post_bookmark`, call `BookmarkMapper.selectBookmarkedPosts(userId, 0, 10)`, and assert one row with the expected id/title/status. Run:

```bash
cd backend
mvn -pl :community-app -Dtest=BookmarkMapperPersistenceTest test
```

Expected before the SQL fix: the test fails with an H2/MySQL-compatible column error for `p.content`.

- [ ] **Step 2: Fix the mapper and retain application assembly semantics**

Keep the repository call `BookmarkRepository.listBookmarkedPosts(UUID userId, int page, int size)` and the application entry `BookmarkApplicationService.listBookmarkedPostSummaries(UUID userId, int page, int size)`. Do not catch `DataAccessException` in `BookmarkController`; optional activity, tags, and content-block projections must still be passed as empty values to `PostSummaryAssembler` when absent.

- [ ] **Step 3: Verify the backend slice**

```bash
cd backend
mvn -pl :community-app -Dtest=BookmarkApplicationServiceTest,BookmarkServiceTest,BookmarkMapperPersistenceTest test
```

Expected: application assembly, repository pagination, and real mapper projection all pass.

- [ ] **Step 4: Move the success assertion into community E2E**

After the existing bookmark click in `02-community.spec.ts`, navigate to `/bookmarks`, wait for `GET /api/bookmarks?page=0&size=10`, assert status `200`, and assert `data.postTitle` is visible. Do not assert `503` or convert an error response into an empty list.

### Task 4: Fix IM CORS and Verify the Cursor Contract

**Files:**
- Create: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security/ImCoreCorsProperties.java`
- Modify: `backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security/ImCoreSecurityConfig.java`
- Modify: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/config/NacosImCoreBindingTest.java`
- Create: `backend/community-im/im-core/src/test/java/com/nowcoder/community/im/core/security/ImCoreCorsSecurityTest.java`
- Modify: `backend/community-gateway/src/test/java/com/nowcoder/community/gateway/im/GatewayImEdgeRouteIntegrationTest.java`
- Create: `tests/playwright-single/tests/07-im.spec.ts`

Bind `im.cors.allowed-origins` as a `List<String>` through `@ConfigurationProperties(prefix = "im.cors")`; do not parse the YAML list with `String.split`, and do not change JWT/resource-server authorization.

- [ ] **Step 1: Add the red configuration/preflight tests**

Assert that `deploy/nacos/config/im-core.yaml` binds `allowed-origins[2]` to `http://localhost:12881`, and that an OPTIONS preflight from that origin to `/api/im/conversations/page` receives the configured CORS origin/method/header response instead of `403 Invalid CORS request`. Run:

```bash
cd backend
mvn -pl :im-core -Dtest=NacosImCoreBindingTest,ImCoreCorsSecurityTest test
```

Expected before the fix: the list property is not accepted by the String-based CORS configuration and the preflight is rejected.

- [ ] **Step 2: Implement list binding and keep auth strict**

Register `ImCoreCorsProperties` in the IM core application context, copy its nonblank `allowedOrigins` to `CorsConfiguration.setAllowedOrigins`, keep credentials, methods, and headers unchanged, and keep `.anyRequest().authenticated()` plus the internal projection scope rule unchanged.

- [ ] **Step 3: Verify Gateway forwarding and IM backend behavior**

```bash
cd backend
mvn -pl :im-core -am -Dtest=NacosImCoreBindingTest,ImCoreCorsSecurityTest,ImCoreApiControllerTest test
mvn -pl :community-gateway -am -Dtest=GatewayImEdgeRouteIntegrationTest test
```

The route test must cover `/api/im/conversations/page`, preserve the Authorization header, and assert the upstream path remains `/api/im/conversations/page`.

- [ ] **Step 4: Verify the deployed page**

`07-im.spec.ts` must assert the page and endpoint success for an empty inbox as well as a nonempty inbox when seed data exists. No old `/api/im/conversations` `403` expectation may remain.

### Task 5: Make Admin Body Pages Explicitly Successful

**Files:**
- Modify: `frontend/src/views/AdminMarketDisputesView.vue`
- Modify: `frontend/src/views/AdminMarketDisputesView.test.js`
- Modify: `frontend/src/views/UserManagementView.test.js`
- Read: `frontend/src/views/HomeView.vue`
- Modify: `tests/playwright-single/tests/06-admin.spec.ts`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/market/controller/AdminMarketControllerTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/user/controller/AdminUserControllerUnitTest.java` only for missing response/empty-search coverage

- [ ] **Step 1: Add red UI assertions for empty and populated states**

In `AdminMarketDisputesView.test.js`, add a `listAdminMarketDisputes.mockResolvedValue({ data: [], traceId: '' })` case and assert the rendered text `暂无待处理争议`. In the populated case retain assertions for `实物商品`, `待管理员裁定`, and `需要管理员裁定`. In `UserManagementView.test.js`, assert that a successful search renders `搜索用户` and the target username.

```bash
cd frontend
npm test -- src/views/AdminMarketDisputesView.test.js src/views/UserManagementView.test.js
```

Expected before the UI change: an empty dispute response renders an empty container with no user-visible empty state.

- [ ] **Step 2: Implement the explicit empty state**

Keep `UiState variant="error"` for request failures and `loading` for loading. When `state.disputes.length === 0` after a successful request, render `UiState` with the literal text `暂无待处理争议`; otherwise render `.market-admin-row` items. Do not treat a failed API response as an empty list.

- [ ] **Step 3: Verify admin security and API contracts**

```bash
cd backend
mvn -pl :community-app -Dtest=AdminMarketControllerTest,AdminUserControllerUnitTest test
mvn test -pl :community-app -Dtest='*ArchTest'
```

The non-admin market test must remain `403`; admin list and resolution remain `200`; no Controller may bypass its ApplicationService.

- [ ] **Step 4: Add real admin E2E body checks**

As `accounts.admin`, open `/admin/users`, assert `搜索用户`, fill `input[name="user-search-username"]` with `accounts.aaa.username`, click `搜索`, and assert the returned `aaa` identity. Open `/admin/market/disputes` and assert either `暂无待处理争议` or a `.market-admin-row`. Open `/dev` and assert `开发检查台` plus `本地联调数据`. As `accounts.aaa`, open `/admin/users`, assert URL `#\\/403`, visible `无权限`, and no broad status allowance.

### Task 6: Make Public Drive Share Verification a Real Success Flow

**Files:**
- Modify: `tests/playwright-single/tests/05-drive.spec.ts`
- Modify: `tests/playwright-single/fixtures/test-data.ts`
- Modify: `frontend/src/views/DriveShareView.test.js`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/application/DriveShareApplicationServiceTest.java`
- Modify: `backend/community-app/src/test/java/com/nowcoder/community/drive/controller/DrivePublicShareControllerUnitTest.java`
- Read: `deploy/.env.single.example`
- Read: `deploy/nacos/config/community-app.yaml`
- Read: `deploy/compose.runtime.services.single.yml`

- [ ] **Step 1: Add the red browser/API success assertion**

After generating a folder share, navigate to the extracted `/#/drive/s/<token>` URL without relying on the logged-in page, enter `data.shareCode`, wait for `POST /api/drive/shares/<token>/verify`, assert HTTP `200`, assert a nonblank `ticket`, and assert `验证成功` plus either the folder entry list or `此文件夹为空`.

- [ ] **Step 2: Verify application and controller contract**

Run:

```bash
cd backend
mvn -pl :community-app -Dtest=DriveShareApplicationServiceTest,DrivePublicShareControllerUnitTest test
```

The application assertion must cover `verifyShare(VerifyDriveShareCommand)` issuing a ticket, recording a successful access, and allowing `listShareEntries(shareToken, ticket, parentId)`. The controller assertion must cover `POST /api/drive/shares/{shareToken}/verify` returning `200` and `data.ticket`.

- [ ] **Step 3: Check single deployment secrets and storage boundary**

Confirm the same nonblank `DRIVE_SHARE_TICKET_SECRET` reaches `community-app`, `deploy/nacos/config/community-app.yaml` resolves `drive.share.ticket-secret`, and the Garage/OSS endpoint used by download URL generation is reachable from `community-app`. Do not change the Controller response on storage failures. If the folder verify path fails before download URL generation, fix the share/ticket persistence or config boundary identified by the response and service log.

- [ ] **Step 4: Remove stale known-issue data and run the frontend contract**

Remove `knownIssueShareFolder` and all `99-known-issues.spec.ts` references. Keep `DriveShareView` behavior for invalid password as an error, but assert valid password renders the ticket-backed verified state.

```bash
cd frontend
npm test -- src/api/services/driveService.test.js src/views/DriveShareView.test.js
```

### Task 7: Synchronize Documentation and Run the Full Acceptance Matrix

**Files:**
- Modify: `tests/playwright-single/README.md`
- Modify: `docs/handbook/testing.md`
- Read: `docs/handbook/architecture.md`
- Read: `docs/superpowers/specs/2026-07-26-playwright-single-all-green-e2e-design.md`

- [ ] **Step 1: Remove stale runner/known-issue documentation**

Document `test:smoke`, `test:regression`, `test`, `report`, and `show-report`; document `07-im.spec.ts`, `@smoke`/`@regression`, failure artifacts, CI triggers, and run-scoped data side effects. Delete the `test:known` command and the list of expected `503`/old IM `403` outcomes.

- [ ] **Step 2: Run the repository verification matrix**

```bash
cd frontend
npm test
npm run build
cd ../backend
mvn test -pl :community-app -Dtest='*ArchTest'
cd ..
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test:regression
npm --prefix tests/playwright-single run report
git diff --check
```

Expected: all commands exit zero, no Playwright business spec is ignored or skipped, and the Markdown report contains no `expected 503`, old IM `403`, or shell-only success semantics.

- [ ] **Step 3: Inspect the final diff without touching unrelated work**

```bash
git status --short
git diff --stat
git diff -- tests/playwright-single docs/handbook .github/workflows backend/community-app/src/main/resources/mapper/bookmark-mapper.xml backend/community-im/im-core/src/main/java/com/nowcoder/community/im/core/security
```

Expected: only the planned files plus the pre-existing user edits are present.
