# Playwright Single Runner and CI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (- [ ]) syntax for tracking.

**Goal:** 建立本地与 GitHub Actions 共用的 single Playwright runner，让所有业务 spec 显式参与 smoke/regression，并在任何未预期 API 或浏览器错误出现时失败且留下诊断证据。

**Architecture:** 用一个共享 Playwright fixture 绑定页面错误、console error 和 API 状态审计；用标题标签选择 smoke/regression，不再通过配置忽略 spec。CI 通过同一个 deployment.sh 入口创建带 run ID 的 Compose project、volume、网络和端口，失败时收集证据后清理。

**Tech Stack:** Playwright Test 1.44+、TypeScript、Node.js fetch、GitHub Actions、Docker Compose、Bash。

## Global Constraints

- tests/playwright-single/tests/ 的业务用例全部按成功语义执行；不得新增或保留 test.fail、test.skip、testIgnore、无限重试或 expected 503/403 通过条件。
- 单 Chromium、fullyParallel: false、默认 PW_WORKERS=1；业务测试不自动重试。
- 只允许精确的 method/path/status allowlist；匿名登录跳转和普通用户后台授权是合法业务结果，不得用全局 4xx 忽略其他错误。
- 保留 trace、截图、视频、list/HTML/JSON/Markdown 报告；CI 额外保留 Compose ps 和日志。
- 所有写入用例使用 SINGLE_TEST_RUN_ID，并保留当前用户对 02-community.spec.ts、README 和 test-results/ 的改动。

---

### Task 1: Replace File-Based Test Selection with Explicit Tags

**Files:**
- Modify: tests/playwright-single/package.json
- Modify: tests/playwright-single/playwright.config.ts
- Modify: tests/playwright-single/tests/00-smoke.spec.ts
- Modify: tests/playwright-single/tests/01-auth.spec.ts
- Modify: tests/playwright-single/tests/02-community.spec.ts
- Modify: tests/playwright-single/tests/03-wallet.spec.ts
- Modify: tests/playwright-single/tests/04-market.spec.ts
- Modify: tests/playwright-single/tests/05-drive.spec.ts
- Modify: tests/playwright-single/tests/06-admin.spec.ts
- Create: tests/playwright-single/tests/07-im.spec.ts
- Delete: tests/playwright-single/tests/99-known-issues.spec.ts

- [ ] **Step 1: Add a command-contract check**

Run the new command before adding it:

~~~bash
npm --prefix tests/playwright-single run test:regression
~~~

Expected: npm exits nonzero with a missing-script error. This proves the check is red before the runner contract is implemented.

- [ ] **Step 2: Define the package scripts**

Update package.json to contain these selection aliases in addition to the existing headed command:

~~~json
{
  "test:smoke": "playwright test --grep @smoke",
  "test:regression": "playwright test --grep @regression",
  "test": "npm run test:regression",
  "report": "node scripts/markdown-report.mjs",
  "show-report": "playwright show-report playwright-report"
}
~~~

Remove test:known and do not set an environment variable to re-enable a deleted known-issues suite.

- [ ] **Step 3: Tag every suite and migrate market state**

Append @smoke @regression to each smoke test title and @regression to every other business test title and parent describe. Delete 99-known-issues.spec.ts. In 04-market.spec.ts, replace the module-level listingUrl and orderUrl variables plus four independent tests with one test.step journey whose local variables are assigned and consumed in the same test:

~~~ts
test('seller publishes, buyer orders, and seller manages inventory @regression', async ({ page }) => {
  let listingUrl = ''
  let orderUrl = ''
  await test.step('seller publishes a listing', async () => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/market/publish')
    await page.getByRole('textbox', { name: '标题' }).fill(data.virtualListingTitle)
    await page.getByRole('textbox', { name: '描述' }).fill(data.virtualListingDescription)
    await page.getByRole('spinbutton', { name: '价格' }).fill('3')
    await page.getByRole('spinbutton', { name: '库存数量' }).fill('1')
    await page.getByRole('textbox', { name: '预存内容' }).fill(data.virtualListingInventory)
    await page.getByRole('button', { name: '确认发布' }).click()
    await expect(page.getByText('发布成功')).toBeVisible()
    await gotoHash(page, '/market')
    await page.getByRole('link', { name: new RegExp(data.virtualListingTitle) }).click()
    await expect(page.getByText(data.virtualListingDescription).first()).toBeVisible()
    await expect(page.getByRole('button', { name: '安全下单' })).toBeVisible()
    listingUrl = page.url()
  })
  await test.step('buyer orders the listing', async () => {
    await loginViaUi(page, accounts.bbb)
    await page.goto(listingUrl)
    await page.getByRole('button', { name: '安全下单' }).click()
    await expect(page).toHaveURL(/#\/market\/orders\//)
    orderUrl = page.url()
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await gotoHash(page, '/market/orders/buying')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
  })
  await test.step('buyer creates an address', async () => {
    await gotoHash(page, '/market/addresses')
    await page.getByRole('textbox', { name: '收货人' }).fill(data.addressReceiver)
    await page.getByRole('textbox', { name: '手机号' }).fill(data.addressPhone)
    await page.getByRole('textbox', { name: '省份' }).fill('北京')
    await page.getByRole('textbox', { name: '城市' }).fill('北京')
    await page.getByRole('textbox', { name: '区县' }).fill('海淀')
    await page.getByRole('textbox', { name: '详细地址' }).fill('中关村测试路 1 号')
    await page.getByRole('textbox', { name: '邮编' }).fill('100000')
    await page.getByRole('checkbox', { name: '设为默认地址' }).check()
    await page.getByRole('button', { name: '新增地址' }).click()
    await expect(page.getByText('地址已创建')).toBeVisible()
  })
  await test.step('seller manages the order and inventory', async () => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/market/orders/selling')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await page.goto(orderUrl)
    await expect(page.getByText('订单详情').first()).toBeVisible()
    await gotoHash(page, '/market/my-listings')
    await expect(page.getByRole('heading', { name: '先看商品状态，再决定进库存还是进卖单' })).toBeVisible()
    const listingRow = page.locator('.market-row').filter({ hasText: data.virtualListingTitle })
    await listingRow.getByRole('link', { name: '库存管理' }).click()
    await page.getByRole('textbox', { name: '追加库存' }).fill(data.virtualListingExtraInventory)
    await page.getByRole('button', { name: '追加库存' }).click()
    await expect(page.getByText(data.virtualListingExtraInventory)).toBeVisible()
    await page.getByRole('button', { name: '失效' }).click()
    await expect(page.getByText('库存已失效')).toBeVisible()
  })
})
~~~

Keep the existing exact UI labels and run-scoped values shown above; no module-level URL variable remains.

- [ ] **Step 4: Remove the config ignore**

Delete includeKnownIssues and testIgnore from playwright.config.ts. Retain this configuration shape:

~~~ts
export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: Number(process.env.PW_WORKERS || 1),
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'reports/latest-results.json' }]
  ],
  outputDir: 'test-results',
  use: {
    baseURL: webBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  }
})
~~~

Keep the existing single Chromium project in the final configuration.

- [ ] **Step 5: Verify selection**

~~~bash
npm --prefix tests/playwright-single run test:smoke -- --list
npm --prefix tests/playwright-single run test:regression -- --list
rg -n "99-known-issues|test:known|testIgnore|PW_INCLUDE_KNOWN_ISSUES" tests/playwright-single --glob '!node_modules/**' --glob '!reports/**' --glob '!playwright-report/**' --glob '!test-results/**'
~~~

Expected: smoke list contains only smoke-tagged tests, regression list contains all smoke and regression-tagged tests, and the final search returns no stale runner/known-issue symbol.

### Task 2: Add a Shared Error-Audit Fixture

**Files:**
- Create: tests/playwright-single/fixtures/audit.ts
- Create: tests/playwright-single/fixtures/test.ts
- Modify: tests/playwright-single/tests/00-smoke.spec.ts
- Modify: tests/playwright-single/tests/01-auth.spec.ts
- Modify: tests/playwright-single/tests/02-community.spec.ts
- Modify: tests/playwright-single/tests/03-wallet.spec.ts
- Modify: tests/playwright-single/tests/04-market.spec.ts
- Modify: tests/playwright-single/tests/05-drive.spec.ts
- Modify: tests/playwright-single/tests/06-admin.spec.ts
- Modify: tests/playwright-single/tests/07-im.spec.ts

- [ ] **Step 1: Define the audit contract**

Implement these exported types and functions in fixtures/audit.ts:

~~~ts
export type ExpectedHttpError = {
  method: string
  path: string
  status: number
}

export type AuditFailure = {
  kind: 'http' | 'pageerror' | 'console'
  method?: string
  url?: string
  status?: number
  message: string
}

export function createApiErrorAudit(apiBaseUrl: string): {
  attach(page: Page): void
  failures(expected: ExpectedHttpError[]): AuditFailure[]
}
~~~

Listen to page response, pageerror, and console. Only responses whose URL starts with SINGLE_API_BASE_URL or the browser frontend origin and whose pathname starts with /api/ are audited. A status at least 500 is always a failure; a 4xx is a failure unless method, pathname, and status exactly match one expected entry. console.error and page errors are always failures.

- [ ] **Step 2: Expose the fixture**

Create fixtures/test.ts with a test fixture extending Playwright's base test:

~~~ts
import { expect, test as base } from '@playwright/test'
import type { ExpectedHttpError } from './audit'
import { createApiErrorAudit } from './audit'
import { apiBaseUrl } from '../fixtures/helpers'

export const test = base.extend<{
  expectedHttpErrors: ExpectedHttpError[]
}>({
  expectedHttpErrors: [[], { option: true }],
  page: async ({ page, expectedHttpErrors }, use, testInfo) => {
    const audit = createApiErrorAudit(apiBaseUrl)
    audit.attach(page)
    await use(page)
    const failures = audit.failures(expectedHttpErrors)
    if (failures.length > 0) {
      await testInfo.attach('single-error-audit.json', {
        body: JSON.stringify(failures, null, 2),
        contentType: 'application/json'
      })
    }
    expect(failures, 'unexpected single page/API errors').toEqual([])
  }
})

export { expect }
~~~

Use the actual imported apiBaseUrl and ExpectedHttpError paths. No test may mutate a global allowlist.

- [ ] **Step 3: Migrate all specs to the fixture**

Change every Playwright spec import to:

~~~ts
import { expect, test } from '../fixtures/test'
~~~

Keep expectedHttpErrors empty for the current /wallet redirect and /admin/users role-guard tests because both legal outcomes are client-side route results. The fixture supports exact entries for a future auth probe, but no wildcard or status-range allowance is permitted.

- [ ] **Step 4: Run an audit failure check**

~~~bash
npm --prefix tests/playwright-single run test:regression -- tests/00-smoke.spec.ts
find tests/playwright-single/test-results -name 'single-error-audit.json' -print
~~~

Expected on a clean stack: the test passes and no audit attachment is generated. If an attachment exists, fix the captured product/config error before adding any allowlist entry.

### Task 3: Make Health Checks Bounded and Reports Diagnostic

**Files:**
- Modify: tests/playwright-single/scripts/health-check.mjs
- Modify: tests/playwright-single/scripts/markdown-report.mjs
- Modify: tests/playwright-single/playwright.config.ts

- [ ] **Step 1: Add the bounded health loop**

Implement a 60-attempt loop with a 1-second delay and a 3-second fetch timeout for both the frontend URL and Gateway health URL. Each retry must include the URL and last HTTP/body error; after attempt 60 throw a single error containing both targets. A successful response must still verify the frontend contains <div id="app"></div> and Gateway JSON body.status === 'UP'.

- [ ] **Step 2: Preserve passed/failed/skipped distinctions**

Keep markdown-report.mjs mapping Playwright passed, failed/timedOut, and skipped separately. Make missing reports/latest-results.json exit with a clear nonzero error instead of producing a false report. Do not turn a skipped test into passed.

- [ ] **Step 3: Verify the scripts**

~~~bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run report
~~~

Expected: health exits within its bound when a target is unavailable, Playwright writes list/HTML/JSON output, and report writes a timestamped Markdown file with explicit counts.

### Task 4: Create Isolated PR/Nightly/Manual CI

**Files:**
- Create: .github/workflows/playwright-single.yml
- Read: deploy/deployment.sh
- Read: deploy/.env.single.example
- Modify: tests/playwright-single/README.md after the workflow is verified
- Modify: docs/handbook/testing.md after the workflow is verified

- [ ] **Step 1: Add workflow triggers and job mode**

Use:

~~~yaml
on:
  pull_request:
  schedule:
    - cron: '17 2 * * *'
  workflow_dispatch:
~~~

Set a job environment variable PW_SUITE to smoke for pull requests and regression for scheduled/manual runs. Use one job with runs-on: ubuntu-latest, actions/checkout@v4, and actions/setup-node@v4 with Node 20.

- [ ] **Step 2: Generate the run-scoped deployment inputs**

In one setup step, compute:

~~~bash
run_suffix="$GITHUB_RUN_ID-$GITHUB_RUN_ATTEMPT"
net_octet=$((30 + (GITHUB_RUN_ID % 180)))
port_offset=$((GITHUB_RUN_ID % 500))
export PW_PROJECT="community-single-pw-$run_suffix"
export COMMUNITY_VOLUME_NAMESPACE="community_single_pw_$run_suffix"
export COMMUNITY_NETWORK_SUBNET="172.29.$net_octet.0/24"
export COMMUNITY_NETWORK_DYNAMIC_RANGE="172.29.$net_octet.128/25"
export NGINX_STATIC_IP="172.29.$net_octet.10"
export COMMUNITY_GATEWAY_STATIC_IP="172.29.$net_octet.20"
export GATEWAY_TRUSTED_PROXY_CIDRS="172.29.$net_octet.10/32"
export COMMUNITY_APP_TRUSTED_PROXY_CIDRS="172.29.$net_octet.20/32"
export NGINX_API_PORT=$((12880 + port_offset))
export FRONTEND_HOST_PORT=$((12881 + port_offset))
export NGINX_XXL_JOB_PORT=$((12887 + port_offset))
cp deploy/.env.single.example "$RUNNER_TEMP/community-single-$run_suffix.env"
~~~

Export these values through the step output/environment for all later steps. The seven topology variables must be overridden because deployment.sh rejects a custom project that reuses single defaults. Set SINGLE_TEST_RUN_ID to run_suffix, SINGLE_API_BASE_URL to http://127.0.0.1:$NGINX_API_PORT, and SINGLE_WEB_BASE_URL to http://127.0.0.1:$FRONTEND_HOST_PORT.

- [ ] **Step 3: Install and start the stack**

~~~bash
npm ci --prefix tests/playwright-single
npx --prefix tests/playwright-single playwright install --with-deps chromium
./deploy/deployment.sh up --topology single --no-observability +  --project-name "$PW_PROJECT" +  --env-file "$RUNNER_TEMP/community-single-$run_suffix.env"
npm --prefix tests/playwright-single run health
~~~

Use the same project and env-file values for ps, logs, and down. Do not run a second bespoke Compose command with a different project.

- [ ] **Step 4: Select smoke or regression**

~~~bash
if [ "$PW_SUITE" = "smoke" ]; then
  npm --prefix tests/playwright-single run test:smoke
else
  npm --prefix tests/playwright-single run test:regression
fi
~~~

Run npm --prefix tests/playwright-single run report in an if: always() step after the test command.

- [ ] **Step 5: Collect evidence and clean up**

Before cleanup, run:

~~~bash
./deploy/deployment.sh ps --topology single --no-observability --project-name "$PW_PROJECT" --env-file "$RUNNER_TEMP/community-single-$run_suffix.env" > "$RUNNER_TEMP/compose-ps.txt"
timeout 30s ./deploy/deployment.sh logs --topology single --no-observability --project-name "$PW_PROJECT" --env-file "$RUNNER_TEMP/community-single-$run_suffix.env" --no-color > "$RUNNER_TEMP/compose-logs.txt" || true
~~~

Upload tests/playwright-single/playwright-report/, test-results/, reports/, and the two RUNNER_TEMP files with actions/upload-artifact@v4 and if: always(). Finally run the matching down command with if: always().

### Task 5: Verify the Full Runner Contract

**Files:**
- Verify: all files listed above

- [ ] **Step 1: Run local command matrix**

~~~bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test:regression
npm --prefix tests/playwright-single run test
npm --prefix tests/playwright-single run report
~~~

- [ ] **Step 2: Scan for forbidden masking**

~~~bash
rg -n "test\.fail|test\.skip|testIgnore|test:known|PW_INCLUDE_KNOWN_ISSUES|toBe\(503\)|toBe\(403\)" tests/playwright-single --glob '!node_modules/**' --glob '!reports/**' --glob '!playwright-report/**' --glob '!test-results/**'
~~~

Expected: no result. The only 403 in the suite is a route/business assertion that uses the exact #/403 URL and 无权限 text, not an expected API failure.

- [ ] **Step 3: Check documentation consistency**

~~~bash
git diff --check -- tests/playwright-single/README.md docs/handbook/testing.md .github/workflows/playwright-single.yml
~~~

Expected: documentation names the same scripts, tags, addresses, artifacts, and PR/nightly/manual modes as the workflow.
