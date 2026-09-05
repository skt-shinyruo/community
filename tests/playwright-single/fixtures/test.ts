import { expect, test as base } from '@playwright/test'
import { createApiErrorAudit } from './audit'
import { apiBaseUrl } from './helpers'

export const test = base.extend({
  page: async ({ page }, use, testInfo) => {
    const audit = createApiErrorAudit(apiBaseUrl)
    audit.attach(page)
    await use(page)
    const failures = audit.failures()
    if (failures.length > 0) {
      await testInfo.attach('single-error-audit.json', {
        body: JSON.stringify(failures, null, 2),
        contentType: 'application/json'
      })
    }
    expect(failures, 'unexpected single page/API errors').toEqual([])
  }
})

export { expect }
