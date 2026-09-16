/**
 * StickyBeak Service Worker (PWA Offline & Caching Engine)
 * Version: 1.0.0
 * 
 * Strategy Overview:
 * 1. Static Assets (HTML/CSS/JS/Fonts/Icons): Cache-First / Stale-While-Revalidate
 * 2. Product Catalog API & Images: Network-First with Cache Fallback
 * 3. Sensitive / Transactional APIs (Cart/Order/Auth/Admin): Network-Only (Never cached)
 */

const STATIC_CACHE = 'stickybeak-static-v1';
const DATA_CACHE = 'stickybeak-data-v1';

// Essential app shell resources to pre-cache on install
const PRECACHE_ASSETS = [
  '/',
  '/index.html',
  '/manifest.webmanifest',
  '/favicon.svg',
  '/favicon.ico',
  '/icons/icon-192.png',
  '/icons/icon-512.png',
  '/icons/icon-maskable-512.png',
  '/icons/apple-touch-icon.png',
];

// Transactional / Sensitive patterns that MUST NEVER be cached
const NEVER_CACHE_PATTERNS = [
  '/api/orders',
  '/api/cart',
  '/api/auth',
  '/api/stripe',
  '/api/webhook',
  '/admin',
  '/api/products/admin',
  '/api/orders/admin',
];

// Product catalog patterns suitable for Network-First caching
const CATALOG_PATTERNS = [
  '/api/products',
  '/api/categories',
  '/products/',
  '/mock-products/',
];

// 1. Install Event: Pre-cache App Shell
self.addEventListener('install', (event) => {
  event.waitUntil(
    caches
      .open(STATIC_CACHE)
      .then((cache) => {
        return cache.addAll(PRECACHE_ASSETS);
      })
      .then(() => self.skipWaiting())
      .catch((err) => {
        console.warn('[SW] Pre-cache failed (ignored during initial setup):', err);
      })
  );
});

// 2. Activate Event: Clean up stale caches and claim clients
self.addEventListener('activate', (event) => {
  event.waitUntil(
    caches
      .keys()
      .then((keys) => {
        return Promise.all(
          keys
            .filter((key) => key !== STATIC_CACHE && key !== DATA_CACHE)
            .map((key) => {
              console.log('[SW] Removing old cache:', key);
              return caches.delete(key);
            })
        );
      })
      .then(() => self.clients.claim())
  );
});

// Helper: Check if URL matches any pattern
function matchesPattern(url, patterns) {
  return patterns.some((p) => url.includes(p));
}

// 3. Fetch Event Routing
self.addEventListener('fetch', (event) => {
  const request = event.request;
  const url = new URL(request.url);

  // Ignore non-GET requests (e.g. POST, PUT, DELETE)
  if (request.method !== 'GET') {
    return;
  }

  // Ignore unsupported schemes (e.g. chrome-extension://)
  if (!url.protocol.startsWith('http')) {
    return;
  }

  // A. Network-Only: Explicit transactional & admin endpoints
  if (matchesPattern(url.pathname, NEVER_CACHE_PATTERNS)) {
    event.respondWith(fetch(request));
    return;
  }

  // B. Network-First with Cache Fallback: Product Catalog & Product Images
  if (matchesPattern(url.pathname, CATALOG_PATTERNS)) {
    event.respondWith(
      fetch(request)
        .then((response) => {
          // If valid response, clone and update cache
          if (response && response.status === 200) {
            const copy = response.clone();
            caches.open(DATA_CACHE).then((cache) => {
              cache.put(request, copy);
            });
          }
          return response;
        })
        .catch(() => {
          // Network failed or offline: Fall back to cache
          return caches.match(request).then((cachedResponse) => {
            if (cachedResponse) {
              return cachedResponse;
            }
            // Return empty mock list for products if no cache
            if (url.pathname.includes('/api/products')) {
              return new Response(
                JSON.stringify({
                  code: 200,
                  message: 'Offline mode: no cached data',
                  data: { items: [], total: 0, page: 1, size: 12 },
                }),
                {
                  status: 200,
                  headers: { 'Content-Type': 'application/json' },
                }
              );
            }
            return new Response('Offline resource not available', { status: 503 });
          });
        })
    );
    return;
  }

  // C. Navigation Requests: Return App Shell (index.html)
  if (request.mode === 'navigate') {
    event.respondWith(
      fetch(request).catch(() => {
        return caches.match('/index.html').then((cached) => {
          return cached || caches.match('/');
        });
      })
    );
    return;
  }

  // D. Static Assets (JS, CSS, Images, Fonts): Cache-First with Stale-While-Revalidate
  event.respondWith(
    caches.match(request).then((cachedResponse) => {
      if (cachedResponse) {
        // Fetch fresh copy in background to keep cache up to date
        fetch(request)
          .then((networkResponse) => {
            if (networkResponse && networkResponse.status === 200) {
              caches.open(STATIC_CACHE).then((cache) => {
                cache.put(request, networkResponse);
              });
            }
          })
          .catch(() => {
            // Background fetch failed (offline), ignore silently
          });
        return cachedResponse;
      }

      // Not in cache, fetch from network and store
      return fetch(request)
        .then((networkResponse) => {
          if (networkResponse && networkResponse.status === 200) {
            const copy = networkResponse.clone();
            caches.open(STATIC_CACHE).then((cache) => {
              cache.put(request, copy);
            });
          }
          return networkResponse;
        })
        .catch((err) => {
          console.warn('[SW] Fetch failed for asset:', request.url, err);
          return new Response('Network error', { status: 408 });
        });
    })
  );
});
