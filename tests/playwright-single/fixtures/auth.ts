import fs from 'node:fs/promises'
import path from 'node:path'
import { expect, type Browser, type Page } from '@playwright/test'
import { accounts, type TestAccount } from './accounts'
import { appUrl } from './helpers'

async function ensureAuthDir(): Promise<void> {
  await fs.mkdir('.auth', { recursive: true })
}

export async function loginViaUi(page: Page, account: TestAccount): Promise<void> {
  await page.goto(appUrl('/auth/login'))
  await page.getByRole('textbox', { name: '请输入用户名' }).fill(account.username)
  await page.getByRole('textbox', { name: '请输入密码' }).fill(account.password)
  await page.getByRole('button', { name: '登录' }).click()
  await expect(page).toHaveURL(/#\/posts/)
  await expect(page.getByText(account.username).first()).toBeVisible()
}

export async function saveStorageState(browser: Browser, account: TestAccount): Promise<void> {
  await ensureAuthDir()
  const context = await browser.newContext()
  const page = await context.newPage()
  await loginViaUi(page, account)
  await context.storageState({ path: account.storageStatePath })
  await context.close()
}

export async function ensureStorageState(browser: Browser, account: TestAccount): Promise<string> {
  try {
    await fs.access(account.storageStatePath)
    return path.resolve(account.storageStatePath)
  } catch {
    await saveStorageState(browser, account)
    return path.resolve(account.storageStatePath)
  }
}

export async function ensureAllStorageStates(browser: Browser): Promise<void> {
  await ensureStorageState(browser, accounts.aaa)
  await ensureStorageState(browser, accounts.bbb)
  await ensureStorageState(browser, accounts.admin)
}
