import { expect, type Page } from '@playwright/test'
import { type TestAccount } from './accounts'
import { appUrl } from './helpers'

export async function loginViaUi(page: Page, account: TestAccount): Promise<void> {
  const logoutButton = page.getByRole('button', { name: '登出' })
  if (await logoutButton.count()) {
    await logoutButton.click()
  }
  await page.goto(appUrl('/auth/login'))
  await expect(page.getByRole('textbox', { name: '请输入用户名' })).toBeVisible()
  await page.getByRole('textbox', { name: '请输入用户名' }).fill(account.username)
  await page.getByRole('textbox', { name: '请输入密码' }).fill(account.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/#\/posts/)
  await expect(page.getByText(account.username).first()).toBeVisible()
}
