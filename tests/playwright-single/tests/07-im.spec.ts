import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe('IM product flow @regression', () => {
  test('authenticated user can load the cursor conversation inbox @regression', async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
    const responsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'GET'
        && url.pathname === '/api/im/conversations/page'
    })
    await gotoHash(page, '/messages')
    const response = await responsePromise
    expect(response.status()).toBe(200)
    const body = await response.json()
    expect(Array.isArray(body.data?.items)).toBe(true)
    expect(typeof body.data?.hasMore).toBe('boolean')

    const items = body.data.items
    if (items.length === 0) {
      await expect(page.getByText('暂无会话')).toBeVisible()
    } else {
      await expect(page.locator('a[href^="/messages/"]').first()).toBeVisible()
    }
    await expect(page.locator('.conversations-empty.ui-state--error')).toHaveCount(0)
  })
})
