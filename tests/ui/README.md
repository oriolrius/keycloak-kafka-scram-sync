# Frontend UI Tests

Playwright tests for the Keycloak Kafka Sync Agent frontend application.

## Test Coverage

### Dashboard Tests (`dashboard.spec.ts`)
- ✅ Page layout and navigation
- ✅ Summary metrics cards (Operations/Hour, Error Rate, Latency)
- ✅ Connection status cards (Kafka, Keycloak, Database)
- ✅ Force Reconcile functionality
- ✅ Operations volume charts (24h and 72h)
- ✅ Navigation between pages

### Operations Timeline Tests (`operations.spec.ts`)
- ✅ Page layout and table structure
- ✅ Filter controls (time range, principal, operation type, result)
- ✅ Sorting on columns (timestamp, duration, principal)
- ✅ Pagination controls and page size selector
- ✅ Row expansion for error details
- ✅ Status badges (SUCCESS, ERROR, SKIPPED)
- ✅ CSV export button
- ✅ Filter reset functionality

## Prerequisites

1. **Install Playwright browsers:**
   ```bash
   npm run test:install
   ```

2. **Backend must be running:**
   - The tests expect the Quarkus backend at `http://localhost:57010`
   - Frontend at `http://localhost:57000`
   - Both servers are automatically started by the test configuration

## Running Tests

### Run all UI tests (headless)
```bash
npm run test:ui
```

### Run tests in headed mode (see browser)
```bash
npm run test:ui:headed
```

### Run tests in debug mode (step through)
```bash
npm run test:ui:debug
```

### Run specific test file
```bash
npx playwright test tests/ui/dashboard.spec.ts --config=tests/playwright-ui.config.ts
```

### Run specific test
```bash
npx playwright test tests/ui/operations.spec.ts --config=tests/playwright-ui.config.ts -g "should filter operations by result status"
```

### View test report
```bash
npm run test:ui:report
```

### Run all tests (API + UI)
```bash
npm run test:all
```

## Test Configuration

Tests are configured in `tests/playwright-ui.config.ts`:

- **Base URL**: `http://localhost:57000` (configurable via `FRONTEND_BASE_URL` env var)
- **Backend URL**: `http://localhost:57010` (started automatically)
- **Browser**: Chromium (Desktop Chrome)
- **Retries**: 2 on CI, 0 locally
- **Screenshots**: On failure
- **Video**: On failure
- **Trace**: On first retry

## Writing New Tests

### Test Structure
```typescript
import { test, expect } from '@playwright/test';

test.describe('Feature Name', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/your-page');
  });

  test('should do something', async ({ page }) => {
    // Arrange
    await page.getByRole('button', { name: 'Click Me' }).click();

    // Assert
    await expect(page.getByText('Success')).toBeVisible();
  });
});
```

### Best Practices

1. **Use semantic selectors** (role, label, text) over CSS selectors
2. **Add waitForTimeout sparingly** - prefer built-in waiting mechanisms
3. **Test user workflows** - not implementation details
4. **Keep tests independent** - don't rely on test execution order
5. **Use page object model** for complex pages (optional)

## Debugging Tips

### Debug a specific test
```bash
npm run test:ui:debug -- -g "test name"
```

### View trace files
```bash
npx playwright show-trace tests/test-results/trace.zip
```

### Take screenshots manually
```typescript
await page.screenshot({ path: 'screenshot.png' });
```

### Slow down test execution
```typescript
test.use({ launchOptions: { slowMo: 500 } });
```

## CI/CD Integration

Tests can be run in CI with:

```bash
CI=true npm run test:ui
```

This will:
- Enable retry on failure (2 retries)
- Run tests sequentially (1 worker)
- Fail if `test.only` is present
- Start fresh backend and frontend servers

## Troubleshooting

### Port already in use
If ports 57000 or 57010 are in use:
```bash
# Kill processes on those ports
lsof -ti:57000 | xargs kill -9
lsof -ti:57010 | xargs kill -9
```

### Backend not starting
Check that backend is healthy:
```bash
curl http://localhost:57010/q/health/ready
```

### Frontend not building
Check frontend build:
```bash
cd frontend && npm run dev
```

### Tests timing out
Increase timeout in config:
```typescript
use: {
  timeout: 60000, // 60 seconds
}
```

## Additional Resources

- [Playwright Documentation](https://playwright.dev/docs/intro)
- [Playwright Best Practices](https://playwright.dev/docs/best-practices)
- [Playwright Selectors](https://playwright.dev/docs/selectors)
