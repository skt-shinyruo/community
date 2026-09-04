import { fileURLToPath } from 'node:url'
import { expect, type Page, type TestInfo } from '@playwright/test'
import { accounts } from './accounts'
import { loginViaUi } from './auth'
import { gotoHash } from './helpers'

const visualStylePath = fileURLToPath(new URL('./visual.css', import.meta.url))

export const visualIds = {
  post: '11111111-1111-7111-8111-111111111111',
  listing: '22222222-2222-7222-8222-222222222222',
  order: '33333333-3333-7333-8333-333333333333',
  conversation: `${accounts.aaa.userId}_${accounts.bbb.userId}`,
  share: 'visual-baseline'
}

export async function prepareVisualPage(page: Page, testInfo: TestInfo): Promise<void> {
  const theme = testInfo.project.name === 'chromium-dark' ? 'dark' : 'light'
  await page.addInitScript((value) => {
    localStorage.setItem('community.ui', JSON.stringify({
      theme: value,
      density: 'compact',
      sidebarCollapsed: false
    }))
  }, theme)
  await mockResult(page, '/api/categories', [])
  await mockResult(page, '/api/tags/hot*', [])
}

export async function authenticateVisualPage(
  page: Page,
  overrides: { noticeSummary?: unknown; imUnreadSummary?: unknown } = {}
): Promise<void> {
  await mockResult(page, '/api/auth/login', { accessToken: 'visual-access-token' })
  await mockResult(page, '/api/auth/me', {
    userId: accounts.aaa.userId,
    username: accounts.aaa.username,
    headerUrl: '',
    authorities: []
  })
  await mockResult(page, '/api/blocks', [])
  await mockResult(page, '/api/feed/global*', { items: [], nextCursor: '', rankVersion: 'visual' })
  await mockResult(page, '/api/im/sessions', {})
  // 壳层未读角标的固定数据源：默认零未读，需要角标覆盖的用例通过 overrides 固定计数。
  await mockResult(page, '/api/notices/summary', overrides.noticeSummary ?? [])
  await mockResult(page, '/api/im/unread/summary*', overrides.imUnreadSummary ?? { rooms: [], conversations: [] })
  await loginViaUi(page, accounts.aaa)
}

export async function openVisualPage(page: Page, route: string, visibleText: string): Promise<void> {
  await gotoHash(page, route)
  await expect(page.getByText(visibleText, { exact: false }).first()).toBeVisible()
  await page.evaluate(() => document.fonts.ready)
}

export async function expectVisualSnapshot(page: Page, name: string): Promise<void> {
  await expect(page).toHaveScreenshot(`${name}.png`, {
    animations: 'disabled',
    caret: 'hide',
    fullPage: true,
    mask: [page.locator('img')],
    maskColor: '#7f7f7f',
    stylePath: visualStylePath
  })
}

export async function mockResult(page: Page, pathPattern: string, data: unknown): Promise<void> {
  await page.route(`**${pathPattern}`, (route) => route.fulfill({
    status: 200,
    contentType: 'application/json',
    body: JSON.stringify({ code: 0, message: '', data, traceId: 'visual-baseline' })
  }))
}
