# PTC Tracker — frontend (PWA)

React 18 + TypeScript + Vite, Tailwind, shadcn-style components on Radix,
TanStack Query, react-router, i18next (en / rw / fr), vite-plugin-pwa
(Workbox, `injectManifest`).

```bash
# Node is run through Docker; replace `npm …` with:
docker run --rm -it --user $(id -u):$(id -g) -e HOME=/tmp -p 5173:5173 \
  -v "$PWD":/app -w /app node:20-alpine npm run dev -- --host
npm run typecheck   # tsc --noEmit
npm test            # vitest
npm run build       # typecheck + production build into dist/
node scripts/i18n-keys.mjs --check   # every t('…') key exists in all locales
```

The dev server proxies `/api` and `/.well-known` to `http://localhost:8080`
(override with `PTC_BACKEND_URL`).

## Layout

```
src/api/        tokenStore (memory only), refresh (single-flight + Web Locks),
                client (bearer, Idempotency-Key, refresh-once, step-up retry),
                endpoints (typed calls), types (mirrors docs/API.md)
src/lib/        outbox (offline queue: attendance + apologies ONLY), quorum,
                time (Africa/Kigali display), push, config, issues
src/components/ ui/ (shadcn-style), app/ (shell, step-up dialog, offline bar,
                update banner, install prompt), domain/ (quorum, attendance,
                voting, declarations, tables)
src/pages/      screens; notify/Landings.tsx = one-question notification pages
src/sw.ts       service worker: precache shell, SWR reference data,
                network-first working data, never /api/auth/**, push
src/i18n/       en.json (bundled), fr.json / rw.json (lazy-loaded)
```

## Security-relevant behaviour

* The access token lives only in a module variable. The refresh token is the
  HttpOnly `ptc_rt` cookie; refresh is serialised across tabs with
  `navigator.locks` because the server revokes the whole session on reuse of
  a consumed refresh token.
* Cold start (including relaunch of the installed app) silently refreshes
  before showing the sign-in page.
* Every write carries a client-generated `Idempotency-Key`; retries after
  refresh or step-up reuse it.
* Votes, conflict-of-interest declarations, stage outcomes and
  recommendations are refused while offline (`OfflineError`); only
  attendance marks and apologies are queued (IndexedDB, no tokens) and
  synced with conflict handling.
* Sign-out asks the service worker to drop cached working data.
