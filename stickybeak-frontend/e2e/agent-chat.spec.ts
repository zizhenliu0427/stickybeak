import { expect, test } from '@playwright/test';
import { BUYER, mockBackend } from './support/mockApi';

test.describe('AI shopping assistant', () => {
  test('signed-out visitors are asked to sign in', async ({ page }) => {
    const backend = await mockBackend(page);
    await page.goto('/');

    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();

    await expect(page.getByText('Sign in to chat with the assistant.')).toBeVisible();
    expect(backend.agentMessages).toEqual([]);
    expect(backend.unmatched).toEqual([]);
  });

  test('adding to the trolley waits for the shopper to confirm', async ({ page }) => {
    const backend = await mockBackend(page, { user: BUYER });
    await page.goto('/');

    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();
    await page.getByLabel('Ask about products, your orders or returns…').fill('Add the UNSW bus sign to my trolley');
    await page.getByRole('button', { name: 'Send' }).click();

    await expect(page.getByText('Add UNSW Bus Sign Magnet × 1 (AUD 15.00) to your trolley')).toBeVisible();
    expect(backend.cart).toEqual([]);

    await page.getByRole('button', { name: 'Confirm' }).click();

    await expect(page.getByText('Added UNSW Bus Sign Magnet to your trolley.')).toBeVisible();
    await expect(page.getByText('Confirmed')).toBeVisible();
    expect(backend.agentResumes).toEqual([true]);
    expect(backend.cart).toEqual([{ productId: 102, qty: 1 }]);
    expect(backend.unmatched).toEqual([]);
  });

  test('declining leaves the trolley unchanged', async ({ page }) => {
    const backend = await mockBackend(page, { user: BUYER });
    await page.goto('/');

    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();
    await page.getByLabel('Ask about products, your orders or returns…').fill('Add the UNSW bus sign');
    await page.getByRole('button', { name: 'Send' }).click();
    await page.getByRole('button', { name: 'Not now' }).click();

    await expect(page.getByText("No problem, I didn't add it.")).toBeVisible();
    expect(backend.agentResumes).toEqual([false]);
    expect(backend.cart).toEqual([]);
  });

  test('after-sales answers stream in with their sources', async ({ page }) => {
    const backend = await mockBackend(page, { user: BUYER });
    await page.goto('/');

    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();
    await page.getByLabel('Ask about products, your orders or returns…').fill('How long does a refund take?');
    await page.keyboard.press('Enter');

    await expect(page.getByText('Card refunds usually take 5-10 business days [1].')).toBeVisible();
    await expect(page.getByText('[1] Returns and Refunds · How long do refunds take')).toBeVisible();
    expect(backend.unmatched).toEqual([]);
  });

  test('the conversation and a pending confirmation survive a page reload', async ({ page }) => {
    const backend = await mockBackend(page, { user: BUYER });
    await page.goto('/');
    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();
    await page.getByLabel('Ask about products, your orders or returns…').fill('Add the UNSW bus sign');
    await page.getByRole('button', { name: 'Send' }).click();
    await expect(page.getByRole('button', { name: 'Confirm' })).toBeVisible();

    await page.reload();
    await page.getByRole('button', { name: 'Ask StickyBeak' }).click();

    await expect(page.getByText('Add the UNSW bus sign', { exact: true })).toBeVisible();
    await page.getByRole('button', { name: 'Confirm' }).click();
    await expect(page.getByText('Added UNSW Bus Sign Magnet to your trolley.')).toBeVisible();
    expect(backend.cart).toEqual([{ productId: 102, qty: 1 }]);
    expect(backend.unmatched).toEqual([]);
  });
});
