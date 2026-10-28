import { expect, test } from '@playwright/test';
import { BUYER, mockBackend } from './support/mockApi';

test.describe('MCP access token', () => {
  test('a shopper generates a token with a ready-to-paste client config, then revokes it', async ({ page }) => {
    const backend = await mockBackend(page, { user: BUYER });
    await page.goto('/profile');

    await expect(page.getByText('No active token.')).toBeVisible();
    await page.getByRole('button', { name: 'Generate token' }).click();

    const config = page.getByLabel('MCP client configuration');
    await expect(config).toBeVisible();
    const text = await config.inputValue();
    expect(text).toContain(`Bearer ${backend.mcpToken!.token}`);
    expect(text).toContain('/api/mcp');
    await expect(page.getByText(/A token is active until/)).toBeVisible();

    await page.getByRole('button', { name: 'Revoke' }).click();
    await page.locator('.ant-popconfirm').getByRole('button', { name: 'OK' }).click();

    await expect(page.getByText('No active token.')).toBeVisible();
    await expect(config).toBeHidden();
    expect(backend.mcpToken).toBeNull();
    expect(backend.unmatched).toEqual([]);
  });
});
