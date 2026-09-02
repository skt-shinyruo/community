import { defineConfig, devices } from '@playwright/test'

const webBaseUrl = (process.env.SINGLE_WEB_BASE_URL || 'http://localhost:12881').replace(/\/$/, '')
const visualDesktop = {
  ...devices['Desktop Chrome'],
  viewport: { width: 1440, height: 900 },
  deviceScaleFactor: 1,
  locale: 'zh-CN',
  timezoneId: 'Asia/Shanghai'
}

export default defineConfig({
  testDir: './tests',
  timeout: 60_000,
  expect: {
    timeout: 15_000
  },
  fullyParallel: false,
  workers: Number(process.env.PW_WORKERS || 1),
  retries: 0,
  reporter: [
    ['list'],
    ['html', { outputFolder: 'playwright-report', open: 'never' }]
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
      testIgnore: /08-visual\.spec\.ts/,
      use: { ...devices['Desktop Chrome'] }
    },
    {
      name: 'chromium-light',
      testMatch: /08-visual\.spec\.ts/,
      use: { ...visualDesktop, colorScheme: 'light' }
    },
    {
      name: 'chromium-dark',
      testMatch: /08-visual\.spec\.ts/,
      grep: /@visual-dark/,
      use: { ...visualDesktop, colorScheme: 'dark' }
    }
  ]
})
