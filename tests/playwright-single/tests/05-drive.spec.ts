import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash, webBaseUrl } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

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
