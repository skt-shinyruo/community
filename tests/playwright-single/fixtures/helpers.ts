import { expect, type APIRequestContext, type Locator, type Page } from '@playwright/test'

export const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
export const apiBaseUrl = (process.env.SINGLE_API_BASE_URL || 'http://localhost:12880').replace(/\/$/, '')

export function appUrl(hashPath: string): string {
  const normalized = hashPath.startsWith('/') ? hashPath : `/${hashPath}`
  return `${webBaseUrl}/#${normalized}`
}

export async function expectGatewayHealthy(request: APIRequestContext): Promise<void> {
  const response = await request.get(`${apiBaseUrl}/actuator/health`)
  expect(response.status()).toBe(200)
  const body = await response.json()
  expect(body.status).toBe('UP')
}

export async function expectFrontendReachable(request: APIRequestContext): Promise<void> {
  const response = await request.get(webBaseUrl)
  expect(response.status()).toBe(200)
  await expect(response.text()).resolves.toContain('<div id="app"></div>')
}

export async function gotoHash(page: Page, hashPath: string): Promise<void> {
  await page.goto(appUrl(hashPath))
}

export async function fillByLabelOrPlaceholder(page: Page, name: string, value: string): Promise<void> {
  const byRole = page.getByRole('textbox', { name }).first()
  if (await byRole.count()) {
    await byRole.fill(value)
    return
  }
  await page.getByPlaceholder(name).fill(value)
}

export async function clickFirst(locator: Locator): Promise<void> {
  await locator.first().click()
}

export async function expectText(page: Page, text: string | RegExp): Promise<void> {
  await expect(page.getByText(text).first()).toBeVisible()
}
