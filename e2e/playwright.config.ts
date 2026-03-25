import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,        // critical-flow tests must run sequentially
  retries: process.env.CI ? 2 : 0,
  timeout: 60_000,             // 60s per test (AI endpoints can be slow)
  expect: { timeout: 10_000 },

  reporter: [['html', { open: 'never' }], ['list']],

  use: {
    baseURL: process.env.WEB_URL ?? 'http://localhost:3000',
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
    video: 'retain-on-failure',
  },

  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
