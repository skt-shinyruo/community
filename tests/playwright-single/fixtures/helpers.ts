import { expect, type APIRequestContext, type Page } from '@playwright/test'

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
