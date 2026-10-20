/// <reference lib="webworker" />
import { precacheAndRoute, cleanupOutdatedCaches, createHandlerBoundToURL } from 'workbox-precaching';
import { registerRoute, NavigationRoute } from 'workbox-routing';
import { NetworkFirst, StaleWhileRevalidate, NetworkOnly } from 'workbox-strategies';
import { ExpirationPlugin } from 'workbox-expiration';
import { CacheableResponsePlugin } from 'workbox-cacheable-response';

declare const self: ServiceWorkerGlobalScope;

/*
 * Caching strategy
 *  - app shell (hashed build assets, index.html): precached, cache-first
 *  - reference & configuration data: stale-while-revalidate
 *  - files, sessions, decisions: network-first with cached fallback, so
 *    previously loaded agendas, member lists and file summaries stay
 *    readable offline
 *  - /api/auth/**: never cached (network only)
 * Only GET responses are cached. Responses never contain tokens; the access
 * token travels in the request header and is not part of the cache key.
 */
precacheAndRoute(self.__WB_MANIFEST);
cleanupOutdatedCaches();

registerRoute(new NavigationRoute(createHandlerBoundToURL('/index.html'), {
  denylist: [/^\/api\//, /^\/\.well-known\//],
}));

const ok = new CacheableResponsePlugin({ statuses: [200] });

registerRoute(({ url }) => url.pathname.startsWith('/api/auth/'), new NetworkOnly());

registerRoute(
  ({ url, request }) => request.method === 'GET' && /^\/api\/(config|workflow|holidays|users|committees)(\/|$|\?)/.test(url.pathname),
  new StaleWhileRevalidate({
    cacheName: 'ptc-reference',
    plugins: [ok, new ExpirationPlugin({ maxEntries: 200, maxAgeSeconds: 7 * 24 * 3600 })],
  }),
);

registerRoute(
  ({ url, request }) =>
    request.method === 'GET' &&
    /^\/api\/(files|sessions|agenda-items|decisions|contracts|me|dashboard)(\/|$)/.test(url.pathname) &&
    !url.pathname.endsWith('/docx'),
  new NetworkFirst({
    cacheName: 'ptc-working-data',
    networkTimeoutSeconds: 6,
    plugins: [ok, new ExpirationPlugin({ maxEntries: 400, maxAgeSeconds: 14 * 24 * 3600 })],
  }),
);

// Signing out on a shared device clears cached working data.
self.addEventListener('message', (event) => {
  if (event.data?.type === 'CLEAR_DATA_CACHES') {
    event.waitUntil(Promise.all(['ptc-reference', 'ptc-working-data'].map((c) => caches.delete(c))));
  }
  if (event.data?.type === 'SKIP_WAITING') void self.skipWaiting();
});

// ------------------------------------------------------------------ push
interface PushPayload {
  title?: string;
  body?: string;
  link?: string;
  tag?: string;
}

self.addEventListener('push', (event) => {
  let payload: PushPayload = {};
  try {
    payload = event.data?.json() ?? {};
  } catch {
    payload = { body: event.data?.text() };
  }
  event.waitUntil(
    self.registration.showNotification(payload.title ?? 'PTC Tracker', {
      body: payload.body ?? '',
      icon: '/icons/icon-192.png',
      badge: '/icons/icon-64.png',
      tag: payload.tag,
      data: { link: payload.link ?? '/notifications' },
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const link: string = event.notification.data?.link ?? '/';
  const target = new URL(link, self.location.origin);
  if (target.origin !== self.location.origin) return;
  event.waitUntil(
    (async () => {
      const all = await self.clients.matchAll({ type: 'window', includeUncontrolled: true });
      for (const client of all) {
        if ('focus' in client) {
          await client.focus();
          if ('navigate' in client) await (client as WindowClient).navigate(target.href);
          return;
        }
      }
      await self.clients.openWindow(target.href);
    })(),
  );
});
