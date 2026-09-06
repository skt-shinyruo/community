import type { Page } from '@playwright/test'

export type AuditFailure = {
  kind: 'http' | 'pageerror' | 'console'
  method?: string
  url?: string
  status?: number
  message: string
}

type AuditedResponse = {
  method: string
  url: string
  path: string
  status: number
}

export type ApiErrorAudit = {
  attach(page: Page): void
  failures(): AuditFailure[]
}

function originOf(value: string): string {
  return new URL(value).origin
}

function isApiUrl(value: string, apiOrigin: string, webOrigin: string): boolean {
  try {
    const url = new URL(value)
    return (url.origin === apiOrigin || url.origin === webOrigin) && url.pathname.startsWith('/api/')
  } catch {
    return false
  }
}

export function createApiErrorAudit(apiBaseUrl: string): ApiErrorAudit {
  const apiOrigin = originOf(apiBaseUrl)
  const webOrigin = originOf(process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881')
  const responses: AuditedResponse[] = []
  const requestFailures: AuditFailure[] = []
  const pageErrors: AuditFailure[] = []
  const consoleErrors: AuditFailure[] = []

  return {
    attach(page) {
      page.on('response', (response) => {
        const url = response.url()
        if (!isApiUrl(url, apiOrigin, webOrigin)) return
        const parsed = new URL(url)
        responses.push({
          method: response.request().method(),
          url,
          path: parsed.pathname,
          status: response.status()
        })
      })

      page.on('requestfailed', (request) => {
        const url = request.url()
        if (!isApiUrl(url, apiOrigin, webOrigin)) return
        requestFailures.push({
          kind: 'http',
          method: request.method(),
          url,
          status: 0,
          message: request.failure()?.errorText || 'request failed'
        })
      })

      page.on('pageerror', (error) => {
        pageErrors.push({
          kind: 'pageerror',
          message: error.message,
          url: page.url()
        })
      })

      page.on('console', (message) => {
        if (message.type() !== 'error') return
        consoleErrors.push({
          kind: 'console',
          message: message.text(),
          url: page.url()
        })
      })
    },

    failures() {
      const httpFailures = responses
        .filter((response) => response.status >= 500 || (response.status >= 400 && response.status <= 499))
        .map((response) => ({
          kind: 'http' as const,
          method: response.method,
          url: response.url,
          status: response.status,
          message: `unexpected HTTP ${response.status}`
        }))

      return [...httpFailures, ...requestFailures, ...pageErrors, ...consoleErrors]
    }
  }
}
