import { test, expect } from '@playwright/test';

test.describe('Dashboard Page', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/');
    // Wait for page to load (not networkidle since we have polling)
    await page.waitForLoadState('domcontentloaded');
    // Wait for the main heading to appear as a signal that React has rendered
    await page.waitForSelector('h1:has-text("Dashboard")', { timeout: 10000 });
  });

  test('should display dashboard with correct title', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Dashboard', level: 1 })).toBeVisible();
  });

  test('should display navigation menu with Dashboard and Operations links', async ({ page }) => {
    await expect(page.getByRole('link', { name: /Dashboard/ })).toBeVisible();
    await expect(page.getByRole('link', { name: /Operations/ })).toBeVisible();
  });

  test('should have active state on Dashboard link', async ({ page }) => {
    const dashboardLink = page.getByRole('link', { name: /Dashboard/ });
    await expect(dashboardLink).toHaveClass(/bg-primary/);
  });

  test('should display summary metrics cards', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Operations / Hour', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Error Rate', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: /Latency/, level: 3 })).toBeVisible();
  });

  test('should display connection status cards', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Kafka Connection', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Keycloak Connection', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Database Usage', level: 3 })).toBeVisible();
  });

  test('should display Force Reconcile button', async ({ page }) => {
    const reconcileButton = page.getByRole('button', { name: /Force Reconcile/ });
    await expect(reconcileButton).toBeVisible();
  });

  test('should display operations volume charts', async ({ page }) => {
    await expect(page.getByRole('heading', { name: 'Operations Volume (24h)', level: 3 })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Operations & Errors (72h)', level: 3 })).toBeVisible();
  });

  test('should navigate to Operations page when clicking Operations link', async ({ page }) => {
    await page.getByRole('link', { name: /Operations/ }).click();
    await expect(page).toHaveURL('/operations');
    await expect(page.getByRole('heading', { name: 'Operation Timeline', level: 1 })).toBeVisible();
  });
});
