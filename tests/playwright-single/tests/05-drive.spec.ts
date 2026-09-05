import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash, webBaseUrl } from '../fixtures/helpers'
import { data } from '../fixtures/test-data'

test.describe.serial('drive product flow @regression', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.bbb)
  })

  test('folder create, rename, delete, and retained share verification work @regression', async ({ page }) => {
    await gotoHash(page, '/drive')
    await page.getByRole('button', { name: '新建文件夹' }).click()
    await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.driveFolder)
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText(data.driveFolder).first()).toBeVisible()
    await page.getByRole('textbox', { name: '重命名' }).fill(data.driveFolderRenamed)
    await page.getByRole('button', { name: '重命名' }).click()
    await expect(page.getByText(data.driveFolderRenamed).first()).toBeVisible()
    // 删除经 UiModalConfirm 二次确认；成功后条目从当前列表消失（静默更新），回收站可见。
    await page.getByRole('button', { name: '删除' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '移至回收站' }).click()
    await expect(page.locator('.drive-entry-row').filter({ hasText: data.driveFolderRenamed })).toHaveCount(0)
    await page.getByRole('tab', { name: '回收站' }).click()
    await expect(page.locator('.drive-entry-row').filter({ hasText: data.driveFolderRenamed })).toBeVisible()
    await page.getByRole('tab', { name: '我的文件' }).click()

    await page.getByRole('button', { name: '新建文件夹' }).click()
    await page.getByRole('textbox', { name: '文件夹名称' }).fill(data.retainedShareFolder)
    await page.getByRole('button', { name: '确认' }).click()
    await expect(page.getByText(data.retainedShareFolder).first()).toBeVisible()
    await page.locator('.drive-entry-row').filter({ hasText: data.retainedShareFolder }).click()
    await expect(page.locator('.drive-detail-panel').getByText(data.retainedShareFolder).first()).toBeVisible()
    await page.getByRole('button', { name: '分享', exact: true }).click()
    await page.getByRole('textbox', { name: '提取码' }).fill(data.shareCode)
    await page.getByRole('button', { name: '生成分享链接' }).click()
    // 生成成功后静默切到分享管理 tab，新分享出现在列表顶部。
    await expect(page.locator('.drive-share-item').filter({ hasText: data.retainedShareFolder })).toBeVisible()
    const shareUrl = await page.evaluate((baseUrl) => {
      const escapedBaseUrl = baseUrl.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
      const match = document.body.innerText.match(new RegExp(`${escapedBaseUrl}/#/drive/s/[A-Za-z0-9_-]+`))
      return match?.[0] || ''
    }, webBaseUrl.replace(/\/$/, ''))
    expect(shareUrl).toContain('/#/drive/s/')

    const shareToken = shareUrl.match(/#\/drive\/s\/([A-Za-z0-9_-]+)/)?.[1] || ''
    expect(shareToken).not.toBe('')
    const verifyResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'POST'
        && url.pathname === `/api/drive/shares/${encodeURIComponent(shareToken)}/verify`
    })
    const entriesResponsePromise = page.waitForResponse((response) => {
      const url = new URL(response.url())
      return response.request().method() === 'GET'
        && url.pathname === `/api/drive/shares/${encodeURIComponent(shareToken)}/entries`
    })
    await page.goto(shareUrl)
    await expect(page.locator('.drive-share-page')).toBeVisible()
    await page.getByRole('textbox', { name: '提取码' }).fill(data.shareCode)
    await page.getByRole('button', { name: '访问分享' }).click()
    const verifyResponse = await verifyResponsePromise
    expect(verifyResponse.status()).toBe(200)
    const verifyBody = await verifyResponse.json()
    expect(typeof verifyBody.data?.ticket).toBe('string')
    expect(verifyBody.data.ticket).not.toBe('')
    const entriesResponse = await entriesResponsePromise
    expect(entriesResponse.status()).toBe(200)
    const entriesBody = await entriesResponse.json()
    expect(Array.isArray(entriesBody.data)).toBe(true)
    await expect(page.getByText('验证成功')).toBeVisible()
    await expect(page.getByText('此文件夹为空')).toBeVisible()

    await gotoHash(page, '/drive')
    await page.getByRole('tab', { name: '分享管理' }).click()
    await expect(page.locator('.drive-share-item').filter({ hasText: data.retainedShareFolder })).toBeVisible()

    await page.reload()
    await page.getByRole('tab', { name: '分享管理' }).click()
    const persistedShare = page.locator('.drive-share-item').filter({ hasText: data.retainedShareFolder })
    await expect(persistedShare).toBeVisible()
    // 撤销影响已拿到链接的访问者，需二次确认；撤销后列表项状态就地更新为已撤销。
    await persistedShare.getByRole('button', { name: '撤销' }).click()
    await page.getByRole('dialog').getByRole('button', { name: '撤销' }).click()
    await expect(persistedShare).toContainText('已撤销')
  })
})
