import { expect, test } from '@playwright/test'
import { accounts } from '../fixtures/accounts'
import { loginViaUi } from '../fixtures/auth'
import { gotoHash } from '../fixtures/helpers'

test.describe.serial('admin and role guard flow', () => {
  test('ordinary user is forbidden from admin user management', async ({ page }) => {
    await loginViaUi(page, accounts.aaa)
    await gotoHash(page, '/admin/users')
    await expect(page).toHaveURL(/#\/403/)
    await expect(page.getByText('无权限').first()).toBeVisible()
  })

  test('admin menu and readable operation pages load', async ({ page }) => {
    await loginViaUi(page, accounts.admin)
    await expect(page.getByText('治理后台')).toBeVisible()
    await expect(page.getByText('统计')).toBeVisible()
    await expect(page.getByText('用户管理')).toBeVisible()
    await expect(page.getByText('钱包后台')).toBeVisible()
    await expect(page.getByText('争议裁定')).toBeVisible()
    await gotoHash(page, '/analytics')
    await expect(page.getByText('统计').first()).toBeVisible()
    await gotoHash(page, '/moderation')
    await expect(page.getByText('治理后台').first()).toBeVisible()
  })
})
