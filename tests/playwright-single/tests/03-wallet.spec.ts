import { expect, test } from '../fixtures/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('wallet product flow @regression', () => {
  test.beforeEach(async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
  })

  test('wallet page loads, test-credit grant succeeds, and transfer succeeds @regression', async ({ page }) => {
    await gotoHash(page, '/wallet')
    await expect(page.getByText('积分钱包').first()).toBeVisible()
    const grantCard = page.locator('.wallet-action-card').filter({ has: page.getByRole('heading', { name: '领取测试积分' }) })
    await expect(grantCard).toBeVisible()
    await grantCard.getByRole('spinbutton').fill('1')
    await grantCard.getByRole('button', { name: '领取测试积分' }).click()
    await expect(page.getByText('积分发放').first()).toBeVisible()
    const transferCard = page.locator('.wallet-action-card').filter({ has: page.getByRole('heading', { name: '转账' }) })
    await transferCard.getByRole('textbox', { name: '目标用户 ID' }).fill(accounts.bbb.userId)
    await transferCard.getByRole('spinbutton').fill('1')
    await transferCard.getByRole('button', { name: '发起转账' }).click()
    // 转账是资损动作，必须经 UiModalConfirm 二次确认后才会提交。
    await page.getByRole('button', { name: '确认转账' }).click()
    await expect(page.getByText('转账转出').first()).toBeVisible()
  })
})
