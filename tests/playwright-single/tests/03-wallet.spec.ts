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
    const rechargeCard = page.locator('.wallet-action-card').filter({ has: page.getByRole('heading', { name: '充值' }) })
    await rechargeCard.getByRole('spinbutton').fill('1')
    await rechargeCard.getByRole('button', { name: '确认充值' }).click()
    await expect(page.getByText(/充值/).first()).toBeVisible()
    const transferCard = page.locator('.wallet-action-card').filter({ has: page.getByRole('heading', { name: '转账' }) })
    await transferCard.getByRole('textbox', { name: '目标用户 ID' }).fill(accounts.bbb.userId)
    await transferCard.getByRole('spinbutton').fill('1')
    await transferCard.getByRole('button', { name: '发起转账' }).click()
    await expect(page.getByText(/转账/).first()).toBeVisible()
  })
})
