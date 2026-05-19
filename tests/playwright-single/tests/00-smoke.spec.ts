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
