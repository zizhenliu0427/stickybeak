import { expect, test } from '@playwright/test';
import { BUYER, mockBackend, order } from './support/mockApi';

test.describe('Orders', () => {
  test('signed-out visitors are sent to the login page', async ({ page }) => {
    await mockBackend(page);

    await page.goto('/orders');

    await expect(page).toHaveURL(/\/login$/);
  });

  test('order history shows paid, refunding and refunded orders', async ({ page }) => {
    const backend = await mockBackend(page, {
      user: BUYER,
      orders: [
        order('SO202610240001', 'paid'),
        order('SO202610240002', 'refunding'),
        order('SO202610240003', 'refunded'),
      ],
    });

    await page.goto('/orders');

    await expect(page.getByText('SO202610240001')).toBeVisible();
    await expect(page.locator('.ant-tag', { hasText: 'Refunding' })).toBeVisible();
    await expect(page.locator('.ant-tag', { hasText: 'Refunded' })).toBeVisible();
    expect(backend.unmatched).toEqual([]);
  });
});
