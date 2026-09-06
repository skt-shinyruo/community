import { expect, type Page } from '@playwright/test'
import { type TestAccount } from './accounts'
import { appUrl } from './helpers'

export async function loginViaUi(page: Page, account: TestAccount): Promise<void> {
  const logoutButton = page.getByRole('button', { name: '登出' })
  if (await logoutButton.count()) {
    await logoutButton.click()
  }
  await page.goto(appUrl('/auth/login'))
  await expect(page.getByRole('textbox', { name: '用户名', exact: true })).toBeVisible()
  await page.getByRole('textbox', { name: '用户名', exact: true }).fill(account.username)
  await page.getByRole('textbox', { name: '密码', exact: true }).fill(account.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/#\/posts/)
  await expect(page.getByText(account.username).first()).toBeVisible()
}
