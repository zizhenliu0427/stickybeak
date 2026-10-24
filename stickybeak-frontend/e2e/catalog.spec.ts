import { expect, test } from '@playwright/test';
import { mockBackend } from './support/mockApi';

test.describe('Catalogue', () => {
  test('keyword search switches to Best Match and highlights the matched words', async ({ page }) => {
    const backend = await mockBackend(page);

    await page.goto('/products');
    await expect(page.locator('.product-card')).toHaveCount(3);

    const search = page.getByPlaceholder('Search by name, campus, station...');
    await search.fill('opera');
    await search.press('Enter');

    await expect(page).toHaveURL(/q=opera/);
    await expect(page.locator('.product-card')).toHaveCount(1);
    // 后端（ES）返回的高亮片段原样渲染，只有命中词是 <em>
    await expect(page.locator('.product-card .search-highlight em')).toHaveText('Opera');
    await expect(page.locator('.ant-select-selection-item', { hasText: 'Best Match' })).toBeVisible();
    expect(backend.unmatched).toEqual([]);
  });

  test('product detail is reachable from the listing', async ({ page }) => {
    const backend = await mockBackend(page);

    await page.goto('/products');
    await page.locator('.product-card', { hasText: 'Kookaburra Magnet' }).click();

    await expect(page).toHaveURL(/\/products\/kookaburra-magnet$/);
    await expect(page.getByRole('heading', { name: 'Kookaburra Magnet' })).toBeVisible();
    expect(backend.unmatched).toEqual([]);
  });
});
