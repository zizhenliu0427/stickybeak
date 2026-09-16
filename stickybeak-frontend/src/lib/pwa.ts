/**
 * StickyBeak PWA Registration and Lifecycle Management
 */

export interface PwaUpdateCallback {
  (registration: ServiceWorkerRegistration): void;
}

/**
 * Register the Service Worker in supported browsers.
 * Safe to call multiple times.
 */
export function registerServiceWorker(onUpdate?: PwaUpdateCallback): void {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
    return;
  }

  // Register on window load to avoid blocking initial render
  window.addEventListener('load', () => {
    navigator.serviceWorker
      .register('/sw.js', { scope: '/' })
      .then((registration) => {
        console.log('[PWA] Service Worker registered with scope:', registration.scope);

        // Check for updates if supported
        if (typeof registration.addEventListener === 'function') {
          registration.addEventListener('updatefound', () => {
            const newWorker = registration.installing;
            if (!newWorker) return;

            newWorker.addEventListener('statechange', () => {
              if (newWorker.state === 'installed' && navigator.serviceWorker.controller) {
                console.log('[PWA] New content available; please refresh.');
                if (onUpdate) {
                  onUpdate(registration);
                }
              }
            });
          });
        }
      })
      .catch((error) => {
        console.warn('[PWA] Service Worker registration failed:', error);
      });
  });
}

/**
 * Unregister all active Service Workers (useful for troubleshooting or development resets)
 */
export async function unregisterServiceWorkers(): Promise<boolean[]> {
  if (typeof window === 'undefined' || !('serviceWorker' in navigator)) {
    return [];
  }
  const registrations = await navigator.serviceWorker.getRegistrations();
  return Promise.all(registrations.map((r) => r.unregister()));
}
