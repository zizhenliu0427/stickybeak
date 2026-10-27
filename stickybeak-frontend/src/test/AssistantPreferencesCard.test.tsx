import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { vi } from 'vitest';
import AssistantPreferencesCard from '../components/AssistantPreferencesCard';
import { api } from '../lib/api';

vi.mock('../lib/i18n', () => ({ useI18n: () => ({ locale: 'en-AU' }) }));
vi.mock('../lib/api', () => ({
  api: { get: vi.fn(), put: vi.fn(), delete: vi.fn() },
  errorMessage: (e: Error) => e.message,
}));

it('loads saved preferences, replaces the budget, and forgets the saved profile', async () => {
  const memory = { preferences: { category: 'bird', max_price_aud: 15 }, updated_at: 100, expires_at: 2000000000 };
  vi.mocked(api.get).mockResolvedValue({ data: { memory } });
  vi.mocked(api.put).mockResolvedValue({ data: { memory: { ...memory, preferences: { category: 'bird', max_price_aud: 20 } } } });
  vi.mocked(api.delete).mockResolvedValue({ data: { memory: null } });
  render(<AssistantPreferencesCard />);
  const budget = await screen.findByRole('spinbutton');
  await waitFor(() => expect(budget).toHaveValue('15.00'));
  fireEvent.change(budget, { target: { value: '20' } });
  fireEvent.click(screen.getByRole('button', { name: 'Save preferences' }));
  await waitFor(() => expect(api.put).toHaveBeenCalledWith('/agent/preferences', { category: 'bird', max_price_aud: 20 }));
  await waitFor(() => expect(screen.getByRole('button', { name: 'Forget preferences' })).toBeEnabled());
  fireEvent.click(screen.getByRole('button', { name: 'Forget preferences' }));
  await waitFor(() => expect(api.delete).toHaveBeenCalledWith('/agent/preferences'));
  await waitFor(() => expect(budget).toHaveValue(''));
});
