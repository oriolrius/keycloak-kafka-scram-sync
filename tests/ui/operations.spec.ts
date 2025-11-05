import { test, expect } from '@playwright/test';

test.describe('Operations Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/operations');
    // Wait for page to load (not networkidle since we have polling)
    await page.waitForLoadState('domcontentloaded');
    // Wait for the main heading to appear as a signal that React has rendered
    await page.waitForSelector('h1:has-text("Operation Timeline")', { timeout: 10000 });
  });

  test('should display operations page with correct title', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Operation Timeline', level: 1 })).toBeVisible();
    await expect(page.getByText('Detailed history of all sync operations')).toBeVisible();
  });

  test('should have active state on Operations link', async ({ page }) => {
    const operationsLink = page.getByRole('link', { name: /Operations/ });
    await expect(operationsLink).toHaveClass(/bg-primary/);
  });

  test('should display filter card', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Filters', level: 3 })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Reset' })).toBeVisible();
  });

  test('should display Export CSV button', async ({ page }) => {
    const exportButton = page.getByRole('button', { name: /Export CSV/ });
    await expect(exportButton).toBeVisible();
  });

  test('should display operations table', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Operations', level: 3 })).toBeVisible();
    // Wait for table to load
    await expect(page.locator('table')).toBeVisible();
  });

  test('should display operations data in table', async ({ page }) => {
    // Check that we have table rows
    const rows = page.locator('tbody tr').first();
    await expect(rows).toBeVisible({ timeout: 5000 });

    // Check that operations count is displayed
    await expect(page.getByText(/Showing \d+ of \d+ operations/)).toBeVisible();
  });

  test('should display page size selector', async ({ page }) => {
    await expect(page.getByText('Page size:')).toBeVisible();
  });

  test('should filter operations by principal', async ({ page }) => {
    // Type in principal filter
    const principalInput = page.getByPlaceholder('Filter by principal...');
    await principalInput.fill('admin');

    // Wait for filtering to complete
    await page.waitForTimeout(1000);

    // Verify we still have results
    const rows = page.locator('tbody tr').filter({ hasNotText: 'Entity ID' });
    const count = await rows.count();
    expect(count).toBeGreaterThan(0);
  });

  test('should expand and collapse operation details', async ({ page }) => {
    // Wait for data to load
    await page.waitForTimeout(1000);

    // Find the first row button
    const firstButton = page.locator('tbody tr').first().locator('button').first();
    await firstButton.click();

    // Verify details are shown
    await expect(page.getByText('Entity ID:')).toBeVisible();

    // Click again to collapse
    await firstButton.click();

    // Wait for animation
    await page.waitForTimeout(500);
  });

  test('should display status badges', async ({ page }) => {
    // Wait for data
    await page.waitForTimeout(1000);

    // Look for any status badge
    const statusBadges = page.locator('tbody').getByText(/SUCCESS|ERROR|SKIPPED/).first();
    await expect(statusBadges).toBeVisible();
  });

  test('should navigate to Dashboard when clicking Dashboard link', async ({ page }) => {
    await page.getByRole('link', { name: /Dashboard/ }).click();
    await expect(page).toHaveURL('/');
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  });
});
