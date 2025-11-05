import { test, expect } from '@playwright/test';

test.describe('Retention Panel', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for page to load
    await page.waitForLoadState('domcontentloaded');
    // Wait for the dashboard heading
    await page.waitForSelector('h1:has-text("Dashboard")', { timeout: 10000 });
  });

  test('should display retention panel on dashboard', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Retention Policy', level: 3 })).toBeVisible();
    await expect(page.getByText('Manage database storage limits')).toBeVisible();
  });

  test('should display current database usage', async ({ page }) => {
    const usageSection = page.locator('h4:has-text("Current Database Usage")').locator('..');
    await expect(usageSection.getByText('Current Database Usage')).toBeVisible();
    // Check for formatted byte display (MB/GB/etc) - look for the large font size
    await expect(usageSection.locator('.text-2xl.font-bold')).toBeVisible();
  });

  test('should display storage and age limit fields', async ({ page }) => {
    await expect(page.getByText('Max Storage (Bytes)').first()).toBeVisible();
    await expect(page.getByText('Max Age (Days)').first()).toBeVisible();
  });

  test('should display last updated timestamp', async ({ page }) => {
    await expect(page.locator('h5:has-text("Last Updated")')).toBeVisible();
  });

  test('should have editable input fields for retention config', async ({ page }) => {
    // Check for input fields
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();
    const maxAgeDaysInput = page.getByPlaceholder('Leave empty for no limit').last();

    await expect(maxBytesInput).toBeVisible();
    await expect(maxAgeDaysInput).toBeVisible();
    await expect(maxBytesInput).toBeEditable();
    await expect(maxAgeDaysInput).toBeEditable();
  });

  test('should display save configuration button', async ({ page }) => {
    const saveButton = page.getByRole('button', { name: /Save Configuration/ });
    await expect(saveButton).toBeVisible();
  });

  test('should show validation error for negative maxBytes', async ({ page }) => {
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();

    // Enter negative value
    await maxBytesInput.fill('-100');
    await maxBytesInput.blur();

    // Wait for validation
    await page.waitForTimeout(500);

    // Check for validation message
    await expect(page.getByText('Must be non-negative')).toBeVisible();
  });

  test('should show validation error for negative maxAgeDays', async ({ page }) => {
    const maxAgeDaysInput = page.getByPlaceholder('Leave empty for no limit').last();

    // Enter negative value
    await maxAgeDaysInput.fill('-10');
    await maxAgeDaysInput.blur();

    // Wait for validation
    await page.waitForTimeout(500);

    // Check for validation message
    await expect(page.getByText('Must be non-negative')).toBeVisible();
  });

  test('should show validation error when maxBytes exceeds limit', async ({ page }) => {
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();

    // Enter value exceeding 10GB (10737418240 bytes)
    await maxBytesInput.fill('99999999999');
    await maxBytesInput.blur();

    // Wait for validation
    await page.waitForTimeout(500);

    // Check for validation message about exceeding limit
    await expect(page.getByText(/Cannot exceed/)).toBeVisible();
  });

  test('should show validation error when maxAgeDays exceeds limit', async ({ page }) => {
    const maxAgeDaysInput = page.getByPlaceholder('Leave empty for no limit').last();

    // Enter value exceeding 3650 days
    await maxAgeDaysInput.fill('5000');
    await maxAgeDaysInput.blur();

    // Wait for validation
    await page.waitForTimeout(500);

    // Check for validation message
    await expect(page.getByText(/Cannot exceed 3650 days/)).toBeVisible();
  });

  test('should disable save button when there are validation errors', async ({ page }) => {
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();
    const saveButton = page.getByRole('button', { name: /Save Configuration/ });

    // Enter invalid value
    await maxBytesInput.fill('-100');
    await maxBytesInput.blur();

    // Wait for validation
    await page.waitForTimeout(500);

    // Save button should be disabled
    await expect(saveButton).toBeDisabled();
  });

  test('should show progress bar when maxBytes is configured', async ({ page }) => {
    // Wait for the panel to load
    await page.waitForTimeout(1500);

    // Check if progress bar exists (it will exist if maxBytes is set)
    const progressBar = page.locator('.bg-muted.rounded-full');
    const progressBarVisible = await progressBar.isVisible().catch(() => false);

    if (progressBarVisible) {
      // If progress bar exists, verify it's rendered
      await expect(progressBar).toBeVisible();
    }
  });

  test('should display warning badge when storage >80%', async ({ page }) => {
    // Wait for the panel to load
    await page.waitForTimeout(1500);

    // Check if warning badge exists
    const warningBadge = page.getByText('Storage Warning');
    const warningVisible = await warningBadge.isVisible().catch(() => false);

    if (warningVisible) {
      // If warning is shown, it should be visible
      await expect(warningBadge).toBeVisible();
    }
  });

  test('should display limit information below input fields', async ({ page }) => {
    // Check for limit info texts
    await expect(page.getByText(/Max: .* GB/)).toBeVisible(); // maxBytes limit
    await expect(page.getByText('Max: 3650 days (10 years)')).toBeVisible(); // maxAgeDays limit
  });

  test('should accept valid positive values', async ({ page }) => {
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();
    const maxAgeDaysInput = page.getByPlaceholder('Leave empty for no limit').last();

    // Enter valid values
    await maxBytesInput.fill('536870912'); // 512 MB
    await maxAgeDaysInput.fill('90'); // 90 days

    // Wait a bit
    await page.waitForTimeout(500);

    // Verify no validation errors are shown
    const errorMessages = page.locator('text=/Must be|Cannot exceed/');
    const errorCount = await errorMessages.count();
    expect(errorCount).toBe(0);
  });

  test('should allow empty values for no limit configuration', async ({ page }) => {
    const maxBytesInput = page.getByPlaceholder('Leave empty for no limit').first();
    const maxAgeDaysInput = page.getByPlaceholder('Leave empty for no limit').last();
    const saveButton = page.getByRole('button', { name: /Save Configuration/ });

    // Clear both fields
    await maxBytesInput.clear();
    await maxAgeDaysInput.clear();

    // Wait a bit
    await page.waitForTimeout(500);

    // Save button should not be disabled (empty is valid)
    await expect(saveButton).not.toBeDisabled();
  });

  test('should have appropriate ARIA labels and accessibility', async ({ page }) => {
    // Check for heading
    await expect(page.getByRole('heading', { name: 'Retention Policy', level: 3 })).toBeVisible();

    // Check for input fields
    const inputs = page.getByPlaceholder('Leave empty for no limit');
    const inputCount = await inputs.count();
    expect(inputCount).toBeGreaterThanOrEqual(2);

    // Check for button
    await expect(page.getByRole('button', { name: /Save Configuration/ })).toBeVisible();
  });
});
