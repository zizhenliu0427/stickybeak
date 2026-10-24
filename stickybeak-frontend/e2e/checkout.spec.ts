import { expect, test } from '@playwright/test';
import { BUYER, mockBackend } from './support/mockApi';

test.describe('Checkout', () => {
  test('guest adds to the trolley, signs in at checkout and pays', async ({ page }) => {
    const backend = await mockBackend(page);

    await page.goto('/products/sydney-opera-house-magnet');
    await page.getByRole('button', { name: /Add to Trolley/ }).first().click();
    await expect(page.getByText('Added to your trolley!')).toBeVisible();

    await page.goto('/cart');
    await expect(page.getByText('Sydney Opera House Magnet').first()).toBeVisible();
    await page.getByRole('button', { name: /Proceed to Checkout/ }).click();

    // 游客结账先登录，登录后回到结账页
    await expect(page).toHaveURL(/\/login$/);
    await page.locator('input#email').fill(BUYER.email);
    await page.locator('input#password').fill('Password123!');
    await page.getByRole('button', { name: 'Sign in' }).click();

    await expect(page).toHaveURL(/\/checkout$/);
    await expect(page.getByText('1 Martin Place').first()).toBeVisible();
    await page.getByRole('button', { name: /Pay Now via Stripe/ }).click();

    await expect(page).toHaveURL(/\/checkout\/success\?order_no=SO/);
    await expect(page.getByText('Order Paid Successfully!')).toBeVisible();

    // 游客购物车在登录时合并到服务端；下单只提交地址 ID、币种与支付方式，金额由后端按购物车计算
    expect(backend.cart).toEqual([{ productId: 101, qty: 1 }]);
    expect(backend.checkoutRequests).toEqual([{ addressId: 31, currency: 'AUD', paymentMethod: 'card' }]);
    expect(backend.unmatched).toEqual([]);
  });
});
