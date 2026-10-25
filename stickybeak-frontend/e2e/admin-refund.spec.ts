import { expect, test } from '@playwright/test';
import { ADMIN, mockBackend, order } from './support/mockApi';

test.describe('Admin console', () => {
  test('admin refunds a paid order and can retry while it is refunding', async ({ page }) => {
    const paid = { ...order('SO202610240001', 'paid'), allowedNextStatuses: ['processing', 'refunding'] };
    const backend = await mockBackend(page, { user: ADMIN, orders: [paid] });

    await page.goto('/admin/orders');
    await expect(page.getByText('SO202610240001')).toBeVisible();

    await page.getByRole('button', { name: /^rollback Refund$|^Refund$/ }).click();
    await page.getByRole('button', { name: 'Yes, Refund' }).click();

    await expect.poll(() => backend.refundRequests).toEqual(['SO202610240001']);
    await expect(page.locator('.ant-tag', { hasText: 'Refunding' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Retry Refund/ })).toBeVisible();
    expect(backend.unmatched).toEqual([]);
  });
});
