import { defineConfig, devices } from '@playwright/test';

/**
 * Playwright Configuration for Frontend UI Testing
 *
 * See https://playwright.dev/docs/test-configuration
 */
export default defineConfig({
  testDir: './ui',

  /* Run tests in files in parallel */
  fullyParallel: true,

  /* Fail the build on CI if you accidentally left test.only in the source code */
  forbidOnly: !!process.env.CI,

  /* Retry on CI only */
  retries: process.env.CI ? 2 : 0,

  /* Opt out of parallel tests on CI */
  workers: process.env.CI ? 1 : undefined,

  /* Reporter to use */
  reporter: [
    ['html', { outputFolder: 'test-results/html-ui' }],
    ['json', { outputFile: 'test-results/results-ui.json' }],
    ['list']
  ],

  /* Shared settings for all the projects below */
  use: {
    /* Base URL to use in actions like `await page.goto('/')` */
    baseURL: process.env.FRONTEND_BASE_URL || 'http://localhost:57000',

    /* Collect trace when retrying the failed test */
    trace: 'on-first-retry',

    /* Screenshot on failure */
    screenshot: 'only-on-failure',

    /* Video on failure */
    video: 'retain-on-failure',
  },

  /* Configure projects for major browsers */
  projects: [
    {
      name: 'chromium',
      use: { ...devices['Desktop Chrome'] },
    },
  ],

  /* Run your local dev server before starting the tests */
  // Note: For development, start servers manually:
  //   - Backend: ./mvnw quarkus:dev (from project root)
  //   - Frontend: npm run dev (from frontend directory)
  // webServer: [
  //   {
  //     command: 'cd .. && ./mvnw quarkus:dev',
  //     url: 'http://localhost:57010/readyz',
  //     reuseExistingServer: !process.env.CI,
  //     timeout: 120 * 1000,
  //   },
  //   {
  //     command: 'cd ../frontend && npm run dev',
  //     url: 'http://localhost:57000',
  //     reuseExistingServer: !process.env.CI,
  //     timeout: 60 * 1000,
  //   }
  // ],
});
