# Community Single Playwright Suite Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build an independent reusable Playwright E2E suite under `tests/playwright-single` for locally deployed `single` topology testing.

**Architecture:** The suite is a standalone npm package that targets the already-running frontend nginx and gateway URLs. Shared fixtures own URL resolution, account constants, login/storage state, unique run ids, and common UI helpers; spec files cover smoke, auth, community, wallet, market, drive, admin, and known current failures.

**Tech Stack:** Playwright Test, TypeScript, Node.js 20-compatible scripts, Markdown reporting.

---

## File Structure

- Create `tests/playwright-single/package.json`: standalone npm scripts and dev dependencies.
- Create `tests/playwright-single/playwright.config.ts`: Playwright config, reporters, projects, timeouts, output directories.
- Create `tests/playwright-single/.env.example`: optional URL and account overrides.
- Create `tests/playwright-single/README.md`: setup, run commands, scope, known caveats.
- Create `tests/playwright-single/fixtures/accounts.ts`: fixed dev account metadata and user ids.
- Create `tests/playwright-single/fixtures/test-data.ts`: run id and unique test-data builders.
- Create `tests/playwright-single/fixtures/helpers.ts`: URL helpers, health assertions, UI helpers, request helpers.
- Create `tests/playwright-single/fixtures/auth.ts`: UI login helper and storage-state bootstrap.
- Create `tests/playwright-single/scripts/health-check.mjs`: fail-fast frontend/gateway health check.
- Create `tests/playwright-single/scripts/markdown-report.mjs`: convert Playwright JSON report to Chinese Markdown.
- Create `tests/playwright-single/tests/*.spec.ts`: module-oriented Playwright specs.
- Create `tests/playwright-single/reports/.gitkeep`: keep report directory.
- Modify `.gitignore`: ignore generated Playwright artifacts under `tests/playwright-single`.
- Modify `docs/handbook/testing.md`: add the new local single Playwright suite to the testing strategy.

## Task 1: Standalone Package And Configuration

**Files:**
- Create: `tests/playwright-single/package.json`
- Create: `tests/playwright-single/playwright.config.ts`
- Create: `tests/playwright-single/.env.example`
- Create: `tests/playwright-single/reports/.gitkeep`
- Modify: `.gitignore`

- [ ] **Step 1: Create `package.json`**

Create `tests/playwright-single/package.json`:

```json
{
  "name": "community-playwright-single",
  "private": true,
  "version": "0.0.0",
  "type": "module",
  "scripts": {
    "health": "node scripts/health-check.mjs",
    "test": "playwright test",
    "test:smoke": "playwright test tests/00-smoke.spec.ts",
    "test:known": "PW_INCLUDE_KNOWN_ISSUES=1 playwright test tests/99-known-issues.spec.ts",
    "test:headed": "playwright test --headed",
    "report": "node scripts/markdown-report.mjs",
    "show-report": "playwright show-report playwright-report"
  },
  "devDependencies": {
    "@playwright/test": "^1.44.0",
    "typescript": "^5.4.0"
  }
}
```

- [ ] **Step 2: Create Playwright config**

Create `tests/playwright-single/playwright.config.ts`:

```ts
import { defineConfig, devices } from '@playwright/test'

const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const includeKnownIssues = process.env.PW_INCLUDE_KNOWN_ISSUES === '1'

export default defineConfig({
  testDir: './tests',
  testIgnore: includeKnownIssues ? [] : ['**/99-known-issues.spec.ts'],
  timeout: 60_000,
  expect: {
    timeout: 15_000
  },
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
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
```

- [ ] **Step 3: Create environment example**

Create `tests/playwright-single/.env.example`:

```bash
SINGLE_WEB_BASE_URL=http://localhost:12881
SINGLE_API_BASE_URL=http://localhost:12880
PW_WORKERS=1
SINGLE_USER_A_USERNAME=aaa
SINGLE_USER_A_PASSWORD=aaa
SINGLE_USER_B_USERNAME=bbb
SINGLE_USER_B_PASSWORD=aaa
SINGLE_ADMIN_USERNAME=admin
SINGLE_ADMIN_PASSWORD=aaa
```

- [ ] **Step 4: Keep reports directory**

Create empty file `tests/playwright-single/reports/.gitkeep`.

- [ ] **Step 5: Ignore generated artifacts**

Add these entries to `.gitignore`:

```gitignore
### Playwright single E2E ###
tests/playwright-single/node_modules/
tests/playwright-single/.auth/
tests/playwright-single/test-results/
tests/playwright-single/playwright-report/
tests/playwright-single/reports/*.json
tests/playwright-single/reports/*.md
```

- [ ] **Step 6: Run package config checks**

Run:

```bash
git diff --check -- .gitignore tests/playwright-single/package.json tests/playwright-single/playwright.config.ts tests/playwright-single/.env.example
```

Expected: exit 0 with no output.

## Task 2: Shared Fixtures And Helpers

**Files:**
- Create: `tests/playwright-single/fixtures/accounts.ts`
- Create: `tests/playwright-single/fixtures/test-data.ts`
- Create: `tests/playwright-single/fixtures/helpers.ts`
- Create: `tests/playwright-single/fixtures/auth.ts`

- [ ] **Step 1: Create account constants**

Create `tests/playwright-single/fixtures/accounts.ts`:

```ts
export type TestAccount = {
  key: 'aaa' | 'bbb' | 'admin'
  username: string
  password: string
  userId: string
  storageStatePath: string
}

export const accounts: Record<TestAccount['key'], TestAccount> = {
  aaa: {
    key: 'aaa',
    username: process.env.SINGLE_USER_A_USERNAME || 'aaa',
    password: process.env.SINGLE_USER_A_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000001',
    storageStatePath: '.auth/aaa.json'
  },
  bbb: {
    key: 'bbb',
    username: process.env.SINGLE_USER_B_USERNAME || 'bbb',
    password: process.env.SINGLE_USER_B_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000002',
    storageStatePath: '.auth/bbb.json'
  },
  admin: {
    key: 'admin',
    username: process.env.SINGLE_ADMIN_USERNAME || 'admin',
    password: process.env.SINGLE_ADMIN_PASSWORD || 'aaa',
    userId: '00000000-0000-7000-8000-000000000003',
    storageStatePath: '.auth/admin.json'
  }
}
```

- [ ] **Step 2: Create test-data builders**

Create `tests/playwright-single/fixtures/test-data.ts`:

```ts
export const runId = process.env.SINGLE_TEST_RUN_ID || new Date().toISOString().replace(/[-:.TZ]/g, '').slice(0, 14)

export function uniqueName(prefix: string): string {
  return `${prefix} ${runId}`
}

export const data = {
  postTitle: uniqueName('Playwright single 功能测试帖'),
  postBody: `这是一条由 Playwright single 本地测试创建的测试内容，run=${runId}，用于验证帖子、评论和收藏链路。`,
  postComment: `Playwright 评论 ${runId}：详情页评论提交链路正常。`,
  postTag: 'playwright',
  virtualListingTitle: uniqueName('Playwright 自动交付兑换码'),
  virtualListingDescription: 'single 环境 Playwright 发布的虚拟商品，用于验证市场发布、列表、详情和订单。',
  virtualListingInventory: `CODE-PW-${runId}`,
  virtualListingExtraInventory: `CODE-PW-EXTRA-${runId}`,
  driveFolder: uniqueName('Playwright 文件夹'),
  driveFolderRenamed: uniqueName('Playwright 文件夹 renamed'),
  retainedShareFolder: uniqueName('Playwright 分享保留'),
  knownIssueShareFolder: uniqueName('Playwright 已知问题分享'),
  shareCode: '1234',
  addressReceiver: '测试收件人',
  addressPhone: '13800000000'
}
```

- [ ] **Step 3: Create generic helpers**

Create `tests/playwright-single/fixtures/helpers.ts`:

```ts
import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

export const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
export const apiBaseUrl = (process.env.SINGLE_API_BASE_URL || 'http://localhost:12880').replace(/\/$/, '')

export function appUrl(hashPath: string): string {
  const normalized = hashPath.startsWith('/') ? hashPath : `/${hashPath}`
  return `${webBaseUrl}/#${normalized}`
}

export async function expectGatewayHealthy(request: APIRequestContext): Promise<void> {
  const response = await request.get(`${apiBaseUrl}/actuator/health`)
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.status).toBe('UP')
}

export async function expectFrontendReachable(request: APIRequestContext): Promise<void> {
  const response = await request.get(webBaseUrl)
  expect(response.status()).toBe(200)
  await expect(response.text()).resolves.toContain('<div id="app"></div>')
}

export async function gotoHash(page: Page, hashPath: string): Promise<void> {
  await page.goto(appUrl(hashPath))
}

export async function fillByLabelOrPlaceholder(page: Page, name: string, value: string): Promise<void> {
  const byRole = page.getByRole('textbox', { name }).first()
  if (await byRole.count()) {
    await byRole.fill(value)
    return
  }
  await page.getByPlaceholder(name).fill(value)
}

export async function clickFirst(locator: Locator): Promise<void> {
  await locator.first().click()
}

export async function expectText(page: Page, text: string | RegExp): Promise<void> {
  await expect(page.getByText(text).first()).toBeVisible()
}
```

- [ ] **Step 4: Create auth helpers**

Create `tests/playwright-single/fixtures/auth.ts`:

```ts
import fs from 'node:fs/promises'
import path from 'node:path'
import { expect, type Browser, type Page } from '@playwright/test'
import { accounts, type TestAccount } from './accounts'
import { appUrl } from './helpers'

async function ensureAuthDir(): Promise<void> {
  await fs.mkdir('.auth', { recursive: true })
}

export async function loginViaUi(page: Page, account: TestAccount): Promise<void> {
  await page.goto(appUrl('/auth/login'))
  await page.getByRole('textbox', { name: '请输入用户名' }).fill(account.username)
  await page.getByRole('textbox', { name: '请输入密码' }).fill(account.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/#\/posts/)
  await expect(page.getByText(account.username).first()).toBeVisible()
}

export async function saveStorageState(browser: Browser, account: TestAccount): Promise<void> {
  await ensureAuthDir()
  const context = await browser.newContext()
  const page = await context.newPage()
  await loginViaUi(page, account)
  await context.storageState({ path: account.storageStatePath })
  await context.close()
}

export async function ensureStorageState(browser: Browser, account: TestAccount): Promise<string> {
  try {
    await fs.access(account.storageStatePath)
    return path.resolve(account.storageStatePath)
  } catch {
    await saveStorageState(browser, account)
    return path.resolve(account.storageStatePath)
  }
}

export async function ensureAllStorageStates(browser: Browser): Promise<void> {
  await ensureStorageState(browser, accounts.aaa)
  await ensureStorageState(browser, accounts.bbb)
  await ensureStorageState(browser, accounts.admin)
}
```

- [ ] **Step 5: Run TypeScript syntax check through Playwright install later**

This task does not run `tsc` yet because dependencies are installed in the verification task.

## Task 3: Health Check And Markdown Report Scripts

**Files:**
- Create: `tests/playwright-single/scripts/health-check.mjs`
- Create: `tests/playwright-single/scripts/markdown-report.mjs`

- [ ] **Step 1: Create health check script**

Create `tests/playwright-single/scripts/health-check.mjs`:

```js
const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const apiBaseUrl = (process.env.SINGLE_API_BASE_URL || 'http://localhost:12880').replace(/\/$/, '')

async function assertOk(url, description) {
  const response = await fetch(url)
  if (!response.ok) {
    throw new Error(`${description} returned HTTP ${response.status}: ${url}`)
  }
  console.log(`${description}: HTTP ${response.status}`)
}

await assertOk(webBaseUrl, 'frontend')

const healthResponse = await fetch(`${apiBaseUrl}/actuator/health`)
if (!healthResponse.ok) {
  throw new Error(`gateway health returned HTTP ${healthResponse.status}`)
}
const body = await healthResponse.json()
if (body.status !== 'UP') {
  throw new Error(`gateway health status is ${JSON.stringify(body)}`)
}
console.log('gateway health: UP')
```

- [ ] **Step 2: Create Markdown report script**

Create `tests/playwright-single/scripts/markdown-report.mjs`:

```js
import fs from 'node:fs/promises'
import path from 'node:path'

const reportPath = path.resolve('reports/latest-results.json')
const outputDir = path.resolve('reports')

function collectSpecs(suite, prefix = []) {
  const currentPrefix = suite.title ? [...prefix, suite.title] : prefix
  const ownSpecs = (suite.specs || []).flatMap((spec) =>
    spec.tests.map((test) => {
      const latest = test.results?.[test.results.length - 1]
      return {
        name: [...currentPrefix, spec.title].filter(Boolean).join(' > '),
        status: latest?.status || test.status || 'unknown',
        error: latest?.error?.message || ''
      }
    })
  )
  const childSpecs = (suite.suites || []).flatMap((child) => collectSpecs(child, currentPrefix))
  return [...ownSpecs, ...childSpecs]
}

const raw = await fs.readFile(reportPath, 'utf8')
const json = JSON.parse(raw)
const specs = collectSpecs(json)
const passed = specs.filter((item) => item.status === 'passed').length
const failed = specs.filter((item) => item.status === 'failed' || item.status === 'timedOut').length
const skipped = specs.filter((item) => item.status === 'skipped').length
const timestamp = new Date().toISOString().replace(/[:.]/g, '-')

const lines = [
  '# single Playwright 本地测试报告',
  '',
  `- 生成时间：${new Date().toISOString()}`,
  `- 前端入口：${process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881'}`,
  `- Gateway：${process.env.SINGLE_API_BASE_URL || 'http://localhost:12880'}`,
  `- 通过：${passed}`,
  `- 失败：${failed}`,
  `- 跳过：${skipped}`,
  '',
  '## 用例明细',
  '',
  '| 状态 | 用例 | 错误摘要 |',
  '| --- | --- | --- |',
  ...specs.map((item) => {
    const error = item.error.replace(/\s+/g, ' ').slice(0, 180)
    return `| ${item.status} | ${item.name} | ${error} |`
  }),
  ''
]

await fs.mkdir(outputDir, { recursive: true })
const outputPath = path.join(outputDir, `single-playwright-report-${timestamp}.md`)
await fs.writeFile(outputPath, lines.join('\n'), 'utf8')
console.log(outputPath)
```

- [ ] **Step 3: Run script syntax check after dependency install**

The scripts use native Node ESM; run them in the final verification after `npm install`.

## Task 4: Smoke And Auth Specs

**Files:**
- Create: `tests/playwright-single/tests/00-smoke.spec.ts`
- Create: `tests/playwright-single/tests/01-auth.spec.ts`

- [ ] **Step 1: Create smoke spec**

Create `tests/playwright-single/tests/00-smoke.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { ensureStorageState, loginViaUi } from '../fixtures/auth'
import { appUrl, expectFrontendReachable, expectGatewayHealthy, gotoHash } from '../fixtures/helpers'

test.describe('single smoke', () => {
  test('frontend and gateway are reachable', async ({ request }) => {
    await expectFrontendReachable(request)
    await expectGatewayHealthy(request)
  })

  test('anonymous posts page loads', async ({ page }) => {
    await gotoHash(page, '/posts')
    await expect(page.getByText('社区讨论').first()).toBeVisible()
  })

  test('protected route redirects anonymous user to login', async ({ page }) => {
    await gotoHash(page, '/wallet')
    await expect(page).toHaveURL(/#\/auth\/login\?redirect=\/wallet/)
    await expect(page.getByText('登录').first()).toBeVisible()
  })

  test('aaa can login and storage state can be created', async ({ browser, page }) => {
    await loginViaUi(page, accounts.aaa)
    await ensureStorageState(browser, accounts.aaa)
  })

  test('login page is directly reachable', async ({ page }) => {
    await page.goto(appUrl('/auth/login'))
    await expect(page.getByText('登录').first()).toBeVisible()
    await expect(page.getByRole('textbox', { name: '请输入用户名' })).toBeVisible()
  })
})
```

- [ ] **Step 2: Create auth spec**

Create `tests/playwright-single/tests/01-auth.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('auth pages and dev account login', () => {
  test('dev accounts can login through the UI', async ({ page }) => {
    for (const account of [accounts.aaa, accounts.bbb, accounts.admin]) {
      await loginViaUi(page, account)
      await page.getByRole('button', { name: '登出' }).click()
      await expect(page).toHaveURL(/#\/auth\/login/)
    }
  })

  test('register page renders and validates empty submit', async ({ page }) => {
    await gotoHash(page, '/auth/register')
    await expect(page.getByText('注册').first()).toBeVisible()
    await expect(page.getByRole('textbox', { name: '请输入用户名' })).toBeVisible()
    await expect(page.getByRole('img', { name: '验证码' })).toBeVisible()
    await page.getByRole('button', { name: '注册' }).click()
    await expect(page.getByText('请填写完整信息')).toBeVisible()
  })

  test('password reset page renders and validates empty submit', async ({ page }) => {
    await gotoHash(page, '/auth/password/reset')
    await expect(page.getByText('找回密码').first()).toBeVisible()
    await expect(page.getByRole('textbox', { name: 'name@example.com' })).toBeVisible()
    await page.getByRole('button', { name: '发送重置链接' }).click()
    await expect(page.getByText('请输入邮箱/验证码')).toBeVisible()
  })
})
```

- [ ] **Step 3: Run smoke after dependencies are installed**

Run in final verification:

```bash
npm --prefix tests/playwright-single run test:smoke
```

Expected: pass when single topology is running.

## Task 5: Community And Wallet Specs

**Files:**
- Create: `tests/playwright-single/tests/02-community.spec.ts`
- Create: `tests/playwright-single/tests/03-wallet.spec.ts`

- [ ] **Step 1: Create community spec**

Create `tests/playwright-single/tests/02-community.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { data } from '../fixtures/test-data'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('community product flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
  })

  test('post creation, detail, like, bookmark, and comment work', async ({ page }) => {
    await gotoHash(page, '/posts')
    await page.getByText('开始一个讨论').click()
    const composer = page.locator('.posts-composer')
    await composer.getByRole('textbox', { name: '标题' }).fill(data.postTitle)
    await composer.getByPlaceholder('正文内容...').fill(data.postBody)
    const tagDraft = composer.locator('input[name="post-tag-draft"]')
    await tagDraft.fill(data.postTag)
    await tagDraft.press('Enter')
    await composer.getByRole('button', { name: '发布' }).click()
    await page.getByRole('button', { name: '立即查看帖子' }).click()
    await expect(page).toHaveURL(/#\/posts\/[^/]+$/)
    await expect(page.getByRole('heading', { name: data.postTitle })).toBeVisible()
    await expect(page.getByText(data.postBody).first()).toBeVisible()
    await page.getByRole('button', { name: '点赞' }).first().click()
    await page.getByRole('button', { name: '收藏' }).click()
    await page.getByPlaceholder('写下你的观点…（支持 Markdown）').fill(data.postComment)
    await page.getByRole('button', { name: '提交' }).click()
    await expect(page.getByText(data.postComment)).toBeVisible()
  })

  test('profile, social lists, notices, and settings pages load', async ({ page }) => {
    await gotoHash(page, `/users/${accounts.aaa.userId}`)
    await expect(page.getByText('公开身份').first()).toBeVisible()
    await gotoHash(page, `/users/${accounts.aaa.userId}/followees`)
    await expect(page.getByText('关注').first()).toBeVisible()
    await gotoHash(page, `/users/${accounts.aaa.userId}/followers`)
    await expect(page.getByText('粉丝').first()).toBeVisible()
    await gotoHash(page, '/notices')
    await expect(page.getByText('通知').first()).toBeVisible()
    await gotoHash(page, '/notices/comment')
    await expect(page.getByText('通知').first()).toBeVisible()
    await gotoHash(page, '/settings')
    await expect(page.getByText('头像上传').first()).toBeVisible()
  })
})
```

- [ ] **Step 2: Create wallet spec**

Create `tests/playwright-single/tests/03-wallet.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('wallet product flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
  })

  test('wallet page loads, recharge succeeds, and transfer succeeds', async ({ page }) => {
    await gotoHash(page, '/wallet')
    await expect(page.getByText('积分钱包').first()).toBeVisible()
    await page.getByRole('spinbutton', { name: '输入充值金额' }).fill('1')
    await page.getByRole('button', { name: '确认充值' }).click()
    await expect(page.getByText(/充值/).first()).toBeVisible()
    await page.getByRole('textbox', { name: '目标用户 ID' }).fill(accounts.bbb.userId)
    await page.getByRole('spinbutton', { name: '输入转账金额' }).fill('1')
    await page.getByRole('button', { name: '发起转账' }).click()
    await expect(page.getByText(/转账/).first()).toBeVisible()
  })
})
```

## Task 6: Market And Drive Specs

**Files:**
- Create: `tests/playwright-single/tests/04-market.spec.ts`
- Create: `tests/playwright-single/tests/05-drive.spec.ts`

- [ ] **Step 1: Create market spec**

Create `tests/playwright-single/tests/04-market.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { data } from '../fixtures/test-data'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('market product flow', () => {
  let listingUrl = ''
  let orderUrl = ''

  test('seller publishes a virtual preloaded listing', async ({ page }) => {
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
    await page.getByText(data.virtualListingTitle).first().click()
    await expect(page.getByText(data.virtualListingDescription).first()).toBeVisible()
    listingUrl = page.url()
  })

  test('buyer creates order and sees it in buying orders', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    await page.goto(listingUrl)
    await page.getByRole('button', { name: '安全下单' }).click()
    await expect(page).toHaveURL(/#\/market\/orders\//)
    orderUrl = page.url()
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await gotoHash(page, '/market/orders/buying')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
  })

  test('buyer can create an address', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
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

  test('seller sees order and can manage inventory', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/market/orders/selling')
    await expect(page.getByText(data.virtualListingTitle).first()).toBeVisible()
    await page.goto(orderUrl)
    await expect(page.getByText('订单详情').first()).toBeVisible()
    await gotoHash(page, '/market/my-listings')
    await page.getByText(data.virtualListingTitle).first().click()
    await page.getByRole('link', { name: '库存管理' }).click()
    await page.getByRole('textbox', { name: '追加库存' }).fill(data.virtualListingExtraInventory)
    await page.getByRole('button', { name: '追加库存' }).click()
    await expect(page.getByText(data.virtualListingExtraInventory)).toBeVisible()
    await page.getByRole('button', { name: '失效' }).click()
    await expect(page.getByText('库存已失效')).toBeVisible()
  })
})
```

- [ ] **Step 2: Create drive spec**

Create `tests/playwright-single/tests/05-drive.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { data } from '../fixtures/test-data'
import { gotoHash, webBaseUrl } from '../fixtures/helpers'

test.describe.serial('drive product flow', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
  })

  test('folder create, rename, delete, and retained share link creation work', async ({ page }) => {
    await gotoHash(page, '/drive')
    await page.getByRole('button', { name: '新建文件夹' }).click()
    await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.driveFolder)
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText(data.driveFolder).first()).toBeVisible()
    await page.getByRole('textbox', { name: '重命名' }).fill(data.driveFolderRenamed)
    await page.getByRole('button', { name: '重命名' }).click()
    await expect(page.getByText(data.driveFolderRenamed).first()).toBeVisible()
    await page.getByRole('button', { name: '删除' }).click()
    await expect(page.getByText('条目已移至回收站')).toBeVisible()

    await page.getByRole('button', { name: '新建文件夹' }).click()
    await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.retainedShareFolder)
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText(data.retainedShareFolder).first()).toBeVisible()
    await page.locator('.drive-entry-row').filter({ hasText: data.retainedShareFolder }).click()
    await expect(page.locator('.drive-detail-panel').getByText(data.retainedShareFolder).first()).toBeVisible()
    await page.getByRole('button', { name: '分享', exact: true }).click()
    await page.getByRole('textbox', { name: '提取码' }).fill(data.shareCode)
    await page.getByRole('button', { name: '生成分享链接' }).click()
    await expect(page.getByText('分享链接已生成')).toBeVisible()
    const shareUrl = await page.evaluate((baseUrl) => {
      const escapedBaseUrl = baseUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const match = document.body.innerText.match(new RegExp(`${escapedBaseUrl}/#/drive/s/[A-Za-z0-9_-]+`))
      return match?.[0] || ''
    }, webBaseUrl.replace(/\/$/, ''))
    expect(shareUrl).toContain('/#/drive/s/')
  })
})
```

## Task 7: Admin And Known Issues Specs

**Files:**
- Create: `tests/playwright-single/tests/06-admin.spec.ts`
- Create: `tests/playwright-single/tests/99-known-issues.spec.ts`

- [ ] **Step 1: Create admin spec**

Create `tests/playwright-single/tests/06-admin.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('admin and role guard flow', () => {
  test('ordinary user is forbidden from admin user management', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/admin/users')
    await expect(page).toHaveURL(/#\/403/)
    await expect(page.getByText('无权限').first()).toBeVisible()
  })

  test('admin menu and readable operation pages load', async ({ page }) => {
    await loginViaUi(page, accounts.admin)
    await expect(page.getByText('治理后台')).toBeVisible()
    await expect(page.getByText('统计')).toBeVisible()
    await expect(page.getByText('用户管理')).toBeVisible()
    await expect(page.getByText('钱包后台')).toBeVisible()
    await expect(page.getByText('争议裁定')).toBeVisible()
    await gotoHash(page, '/analytics')
    await expect(page.getByText('统计').first()).toBeVisible()
    await gotoHash(page, '/moderation')
    await expect(page.getByText('治理后台').first()).toBeVisible()
  })
})
```

- [ ] **Step 2: Create known issues spec**

Create `tests/playwright-single/tests/99-known-issues.spec.ts`:

```ts
import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { apiBaseUrl, gotoHash } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

test.describe.serial('known current single issues', () => {
  test('bookmarks endpoint currently returns 503', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    const responsePromise = page.waitForResponse((response) =>
      response.url().includes('/api/bookmarks?page=0&size=10')
    )
    await gotoHash(page, '/bookmarks')
    const response = await responsePromise
    expect(response.status()).toBe(503)
  })

  test('im conversations endpoint currently returns 403', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    const responsePromise = page.waitForResponse((response) => response.url().includes('/api/im/conversations'))
    await gotoHash(page, '/messages')
    const response = await responsePromise
    expect(response.status()).toBe(403)
  })

  test('some admin body routes currently render only shell content', async ({ page }) => {
    await loginViaUi(page, accounts.admin)
    await gotoHash(page, '/admin/users')
    await expect(page.getByText('用户管理').first()).toBeVisible()
    await expect(page.getByText('搜索用户')).toHaveCount(0)
    await gotoHash(page, '/admin/market/disputes')
    await expect(page.getByText('争议裁定').first()).toBeVisible()
    await expect(page.getByText(/争议 #/)).toHaveCount(0)
    await gotoHash(page, '/dev')
    await expect(page.getByText('联调').first()).toBeVisible()
    await expect(page.getByText('开发检查台')).toHaveCount(0)
  })

  test('gateway remains available while known issues are reproduced', async ({ request }) => {
    const response = await request.get(`${apiBaseUrl}/actuator/health`)
    expect(response.status()).toBe(200)
  })

  test('public drive share verification currently returns 503', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    await gotoHash(page, '/drive')
    await page.getByRole('button', { name: '新建文件夹' }).click()
    await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.knownIssueShareFolder)
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText(data.knownIssueShareFolder).first()).toBeVisible()
    await page.locator('.drive-entry-row').filter({ hasText: data.knownIssueShareFolder }).click()
    await page.getByRole('button', { name: '分享', exact: true }).click()
    await page.getByRole('textbox', { name: '提取码' }).fill(data.shareCode)
    await page.getByRole('button', { name: '生成分享链接' }).click()
    await expect(page.getByText('分享链接已生成')).toBeVisible()
    const shareToken = await page.evaluate(() => {
      const match = document.body.innerText.match(/#\/drive\/s\/([A-Za-z0-9_-]+)/)
      return match?.[1] || ''
    })
    expect(shareToken).not.toBe('')

    const response = await page.request.post(`${apiBaseUrl}/api/drive/shares/${encodeURIComponent(shareToken)}/verify`, {
      data: { password: data.shareCode }
    })
    expect(response.status()).toBe(503)
  })
})
```

## Task 8: README And Handbook Documentation

**Files:**
- Create: `tests/playwright-single/README.md`
- Modify: `docs/handbook/testing.md`

- [ ] **Step 1: Create README**

Create `tests/playwright-single/README.md`:

```markdown
# tests/playwright-single

This folder contains reusable Playwright E2E tests for a locally deployed
`single` topology. It is intentionally independent from `frontend` because it
tests the deployed browser surface through frontend nginx and the gateway.

## Prerequisite

From the repository root:

```bash
./deploy/deployment.sh up --topology single --no-observability
```

Default targets:

- Frontend: `http://localhost:12881`
- Gateway: `http://localhost:12880`

## Install

```bash
npm --prefix tests/playwright-single install
npx --prefix tests/playwright-single playwright install chromium
```

## Run

```bash
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test
npm --prefix tests/playwright-single run test:known
npm --prefix tests/playwright-single run report
```

## Scope

- Smoke checks verify frontend, gateway, anonymous posts, protected route
  redirect, and login.
- Product checks cover community, wallet, market, drive, and admin read flows.
- `99-known-issues.spec.ts` tracks current local single failures without hiding
  them in normal product assertions.

State-changing tests create unique names using a timestamp. The first version
does not reset local data automatically.
```

- [ ] **Step 2: Update testing handbook**

In `docs/handbook/testing.md`, add a new section after the frontend testing
section and before Mock Data Studio:

```markdown
## single Playwright 本地 E2E

独立浏览器验收套件位于：

```text
tests/playwright-single
```

它面向已经启动的 `single` 拓扑，默认访问 `http://localhost:12881` 和
`http://localhost:12880`。常用命令：

```bash
./deploy/deployment.sh up --topology single --no-observability
npm --prefix tests/playwright-single install
npm --prefix tests/playwright-single run health
npm --prefix tests/playwright-single run test:smoke
npm --prefix tests/playwright-single run test
npm --prefix tests/playwright-single run report
```

状态会变化的用例会创建带时间戳的本地测试数据。当前套件不自动清空
single 数据库、Redis、对象存储或 Elasticsearch。
```

- [ ] **Step 3: Run docs diff check**

Run:

```bash
git diff --check -- tests/playwright-single/README.md docs/handbook/testing.md
```

Expected: exit 0 with no output.

## Task 9: Install And Verification

**Files:**
- All files under `tests/playwright-single`
- `docs/superpowers/specs/2026-05-19-community-single-playwright-suite-design.md`
- `docs/superpowers/plans/2026-05-19-community-single-playwright-suite.md`
- `docs/handbook/testing.md`
- `.gitignore`

- [ ] **Step 1: Install dependencies**

Run:

```bash
npm --prefix tests/playwright-single install
```

Expected: dependencies are installed and `tests/playwright-single/package-lock.json`
is created.

- [ ] **Step 2: Install Chromium browser if missing**

Run:

```bash
npx --prefix tests/playwright-single playwright install chromium
```

Expected: Chromium is installed or already present.

- [ ] **Step 3: Run health check**

Run:

```bash
npm --prefix tests/playwright-single run health
```

Expected output includes:

```text
frontend: HTTP 200
gateway health: UP
```

- [ ] **Step 4: Run smoke tests**

Run:

```bash
npm --prefix tests/playwright-single run test:smoke
```

Expected: smoke tests pass when single is running.

- [ ] **Step 5: Generate Markdown report**

Run:

```bash
npm --prefix tests/playwright-single run report
```

Expected: command prints a path under `tests/playwright-single/reports/`.

- [ ] **Step 6: Run full diff check**

Run:

```bash
git diff --check -- .gitignore docs/handbook/testing.md docs/superpowers/specs/2026-05-19-community-single-playwright-suite-design.md docs/superpowers/plans/2026-05-19-community-single-playwright-suite.md tests/playwright-single
```

Expected: exit 0 with no output.

- [ ] **Step 7: Inspect git status**

Run:

```bash
git status --short
```

Expected: only intentional files from this plan are listed, plus generated
`tests/playwright-single/package-lock.json` if dependency installation occurred.
