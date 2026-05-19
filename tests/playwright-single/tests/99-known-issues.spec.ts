import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { apiBaseUrl, gotoHash } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

test.describe.serial('known current single issues', () => {
  test('bookmarks endpoint currently returns 503', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    const responsePromise = page.waitForResponse((response) => response.url().includes('/api/bookmarks?page=0&size=10'))
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
