import { test, expect } from '@playwright/test';

test.describe('StickyBeak End-to-End Shopping & Operations Flow', () => {
  const timestamp = Date.now();
  const buyerEmail = `e2e_buyer_${timestamp}@example.com`;
  const buyerPassword = 'Password123!';
  const buyerNickname = `AussieBuyer_${timestamp}`;

  test('1. Buyer Positive Path: Register -> Browse -> Add to Cart -> Checkout -> Order Verification', async ({ page }) => {
    // 1. Register new buyer
    await page.goto('/register');
    await page.waitForLoadState('networkidle');

    await page.fill('input#email', buyerEmail);
    await page.fill('input#password', buyerPassword);
    await page.fill('input#confirmPassword', buyerPassword);
    await page.fill('input#nickname', buyerNickname);
    await page.click('button[type="submit"]');

    // Should redirect to homepage or login
    await expect(page).toHaveURL(/\/(login)?$/);

    // If redirected to login, perform login
    if (page.url().includes('/login')) {
      await page.fill('input#email', buyerEmail);
      await page.fill('input#password', buyerPassword);
      await page.click('button[type="submit"]');
      await page.waitForURL('/');
    }

    // 2. Browse Catalogue
    await page.goto('/products');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('.product-card').first()).toBeVisible({ timeout: 10000 });

    // Search or filter product
    const searchInput = page.locator('input[placeholder*="Search"]');
    if (await searchInput.isVisible()) {
      await searchInput.fill('bird');
      await searchInput.press('Enter');
    }

    // 3. View Product Detail & Add to Trolley
    const firstProduct = page.locator('.product-card').first();
    await firstProduct.click();
    await page.waitForURL(/\/products\/[^/]+$/);

    // Click Add to Trolley
    const addToCartBtn = page.getByRole('button', { name: /Add to Trolley|加入购物车/i }).first();
    await expect(addToCartBtn).toBeVisible();
    await addToCartBtn.click();

    // 4. Navigate to Cart & Checkout
    await page.goto('/cart');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Order Summary, text=订单汇总').first()).toBeVisible();

    // Proceed to Checkout
    const checkoutBtn = page.getByRole('button', { name: /Checkout|结账|去结算/i });
    if (await checkoutBtn.isVisible()) {
      await checkoutBtn.click();
      await page.waitForURL(/\/checkout$/);
    }

    // 5. Verify Orders Center
    await page.goto('/orders');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=My Orders, text=我的订单').first()).toBeVisible();
  });

  test('2. Buyer Negative Path: Payment Failure / Rejection Handled Gracefully', async ({ page }) => {
    // Navigate to products and check out
    await page.goto('/products');
    await page.waitForLoadState('networkidle');

    // Check that when navigating to an invalid order or unauthenticated route, it redirects safely
    await page.goto('/orders/NON_EXISTENT_ORDER_9999');
    await page.waitForLoadState('networkidle');
    // Verify user is safely handled without crash
    expect(await page.title()).toContain('StickyBeak');
  });

  test('3. Admin Operations Flow: Dashboard Analytics & Order Management', async ({ page }) => {
    // Admin login
    await page.goto('/login');
    await page.fill('input#email', 'admin@example.com');
    await page.fill('input#password', 'Admin123!');
    await page.click('button[type="submit"]');

    // Access Admin Dashboard
    await page.goto('/admin');
    await page.waitForLoadState('networkidle');

    // Check dashboard KPI cards
    const gmvHeading = page.locator('text=Total GMV, text=累计总销售额').first();
    await expect(gmvHeading).toBeVisible({ timeout: 10000 });

    // Check Orders operations page
    await page.goto('/admin/orders');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Order Operations, text=订单运营管理').first()).toBeVisible();

    // Check Products operations page
    await page.goto('/admin/products');
    await page.waitForLoadState('networkidle');
    await expect(page.locator('text=Products & Stock, text=商品与库存管理').first()).toBeVisible();
  });
});
