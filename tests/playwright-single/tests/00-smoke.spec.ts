import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { ensureStorageState, loginViaUi } from '../fixtures/auth'
import { appUrl, expectFrontendReachable, expectGatewayHealthy, gotoHash } from '../fixtures/helpers'

test.describe('single smoke @smoke @regression', () => {
  test('frontend and gateway are reachable @smoke @regression', async ({ request }) => {
    await expectFrontendReachable(request)
    await expectGatewayHealthy(request)
  })

  test('anonymous posts page loads @smoke @regression', async ({ page }) => {
    await gotoHash(page, '/posts')
    await expect(page.getByText('社区讨论').first()).toBeVisible()
  })

  test('protected route redirects anonymous user to login @smoke @regression', async ({ page }) => {
    await gotoHash(page, '/wallet')
    await expect(page).toHaveURL(/#\/auth\/login\?redirect=\/wallet/)
    await expect(page.getByText('登录').first()).toBeVisible()
  })

  test('aaa can login and storage state can be created @smoke @regression', async ({ browser, page }) => {
    await loginViaUi(page, accounts.aaa)
    await ensureStorageState(browser, accounts.aaa)
  })

  test('login page is directly reachable @smoke @regression', async ({ page }) => {
    await page.goto(appUrl('/auth/login'))
    await expect(page.getByText('登录').first()).toBeVisible()
    await expect(page.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible()
  })
})
