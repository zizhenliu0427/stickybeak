import { describe, it, expect, vi } from 'vitest';
import { renderHook, act } from '@testing-library/react';
import { useOnlineStatus } from '../hooks/useOnlineStatus';
import { registerServiceWorker, unregisterServiceWorkers } from '../lib/pwa';

describe('PWA & Offline System (Client Test)', () => {
  describe('useOnlineStatus Hook', () => {
    it('should initialize with online status and react to offline/online window events', () => {
      const { result } = renderHook(() => useOnlineStatus());
      expect(result.current.isOnline).toBe(true);
      expect(result.current.wasOffline).toBe(false);

      // Simulate network going offline
      act(() => {
        window.dispatchEvent(new Event('offline'));
      });
      expect(result.current.isOnline).toBe(false);

      // Simulate network coming back online
      act(() => {
        window.dispatchEvent(new Event('online'));
      });
      expect(result.current.isOnline).toBe(true);
      expect(result.current.wasOffline).toBe(true);

      // Dismiss / clear wasOffline
      act(() => {
        result.current.clearWasOffline();
      });
      expect(result.current.wasOffline).toBe(false);
    });
  });

  describe('PWA Registration Helper', () => {
    it('should safely handle environments where navigator.serviceWorker exists', async () => {
      const mockRegister = vi.fn().mockResolvedValue({
        scope: '/',
        addEventListener: vi.fn(),
      });
      const mockGetRegistrations = vi.fn().mockResolvedValue([]);

      Object.defineProperty(window.navigator, 'serviceWorker', {
        value: {
          register: mockRegister,
          getRegistrations: mockGetRegistrations,
        },
        writable: true,
        configurable: true,
      });

      registerServiceWorker();
      // Trigger load event
      window.dispatchEvent(new Event('load'));
      expect(mockRegister).toHaveBeenCalledWith('/sw.js', { scope: '/' });

      const unregisterResults = await unregisterServiceWorkers();
      expect(mockGetRegistrations).toHaveBeenCalled();
      expect(unregisterResults).toEqual([]);
    });
  });
});
