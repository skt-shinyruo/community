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
