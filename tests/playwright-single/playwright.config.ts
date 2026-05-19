import { defineConfig, devices } from '@playwright/test'

const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const includeKnownIssues = process.env.PW_INCLUDE_KNOWN_ISSUES === '1'

export default defineConfig({
  testDir: './tests',
  testIgnore: includeKnownIssues ? [] : ['**/99-known-issues.spec.ts'],
  timeout: 60_000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  workers: Number(process.env.PW_WORKERS || 1),
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }],
    ['json', { outputFile: 'reports/latest-results.json' }]
  ],
  outputDir: 'test-results',
  use: {
    baseURL: webBaseUrl,
    trace: 'retain-on-failure',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure'
  },
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] }
    }
  ]
})
