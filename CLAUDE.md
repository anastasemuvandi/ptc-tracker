# CLAUDE.md

PTC Tracker: multi-tenant PWA for Rwandan Public Tender Committees to track their own deliberation (files, sessions, votes, minutes). **Umucyo is the system of record**: never store bid documents, bidder submissions, prices or evaluation scores, and never add tender publication, bid opening or award features. Tenders, contracts and bidders are referenced by reference only. The one stored document type is a request's internal supporting document (memo, justification, report), uploaded by a requester who declares it holds no bid content.

Read `docs/ARCHITECTURE.md` (modules, conventions) and `docs/API.md` (HTTP contract) before changing backend or API shapes. `docs/OPERATIONS.md` covers deployment, keys and backups.

## Layout

- `backend/` — Java 21, Spring Boot 3.5, Maven, Flyway, JPA, Quartz, Apache POI, nimbus-jose-jwt. Package `rw.gov.ptc`.
  Each feature package (`committee`, `file`, `session`, …) is split into `controller/`, `dto/` (request records, one per file), `enums/`, `entity/`, `repository/`, `service/` (interfaces) with `service/impl/` (`XServiceImpl`, holds `@Service` and `@Transactional`), and `component/` (helper beans, constants, jobs). `auth` (api/internal boundary), `common`, `security`, `tenancy` and `idempotency` are infrastructure and keep their flat layout.
  Closed value sets (statuses, kinds) are enums mapped with `@Enumerated(EnumType.STRING)`, matching the column's CHECK constraint; request DTOs keep them as `String` and services convert with `Enums.parse`, so unknown values raise our own error codes. Configurable values (file stages, notification kinds) stay `String`.
- `frontend/` — React 18 + TypeScript + Vite, Tailwind, shadcn-style components in `src/components/ui`, i18next (`src/i18n/locales/{en,fr,rw}.json`), Workbox service worker `src/sw.ts`.
- `deploy/` — docker-compose (db, backend, web), DB role bootstrap, `scripts/generate-secrets.sh`, `scripts/smoke-test.py`.
- `docs/user-guide/screenshots/` — screenshot sources; the in-app guide is `frontend/src/pages/guide/user-guide.en.md` (served at `/guide`, images in `frontend/public/guide/`).

## Commands

Backend (Docker must be running; tests use Testcontainers `postgres:16-alpine`):
```sh
cd backend
mvn -q test                                  # full suite (~41 integration tests)
mvn -q test -Dtest=TenantIsolationTest       # one class
mvn -q -Dptc.build.dir=target-x test         # separate build dir when running builds in parallel
```
Surefire summaries: `backend/target/surefire-reports/*.txt` (quiet mode prints nothing on success).

Frontend — Node is not installed on the host; run it in Docker:
```sh
cd frontend
docker run --rm --user $(id -u):$(id -g) -e HOME=/tmp -v $PWD:/app -w /app node:20-alpine sh -c "npx tsc --noEmit && npx vitest run && npx vite build"
docker run --rm --user $(id -u):$(id -g) -e HOME=/tmp -v $PWD:/app -w /app node:20-alpine node scripts/i18n-keys.mjs --check
```
Pin exact dependency versions (`npm install --save-exact`).

Full stack (http://localhost:8088):
```sh
cd deploy
docker compose up -d --build           # or: up -d --build web  (frontend only)
python3 scripts/smoke-test.py          # end-to-end checks through nginx (needs mail disabled)
docker compose logs backend | grep accept-invite   # invitation links when mail is disabled
docker compose down                    # add -v to wipe data
```
`deploy/.env` is a local trial config (cookie `Secure` off). Never commit it.

## Rules that are easy to break

- **Tenancy is enforced by PostgreSQL RLS**, keyed on `app.institution_id` that `TenantAwareDataSource` sets on every connection checkout from `TenantContext`. Never filter by institution *instead of* RLS, never connect as `ptc_owner` at runtime. Background jobs iterate `app_active_institution_ids()` inside `TenantContext.runAs(...)`.
- **Three DB roles**: `ptc_owner` (Flyway only), `ptc_app` (app, RLS), `ptc_auth` (auth module only). A new tenant table needs `institution_id`, an RLS policy and grants (copy the V9 pattern) in a **new** migration. Never edit an applied migration.
- **Auth module boundary**: the rest of the app uses only `rw.gov.ptc.auth.api` (`TokenVerifier`, `AuthPrincipal`, `StepUpVerifier`, `IdentityAdmin`, `AuthMailer`). Anything about credentials, tokens or keys stays in `auth.internal`. Role or membership changes must call `IdentityAdmin.membershipChanged` / bump `token_version` (after commit).
- **UUID keys** everywhere (UUIDv7 via `Ids.newId()`); entities extend `BaseEntity`/`TenantEntity`, use public fields, and hold foreign keys as plain `UUID` (no JPA associations). Human-facing numbers (`F-2026/0042`, `PTCA 2026/014`) come from `institution_counter` (key `FILE:<year>` for files). Never show or print a UUID to users, in minutes or exports.
- **Configuration, not code**: every threshold, period, size or rule is read from `ConfigService.snapshot()` (keys in `ConfigKeys`, defaults seeded in a migration). Records judged against rules store the snapshot and are re-read with `ConfigService.fromStored(...)`, never re-resolved.
- **Audit** every create/update/vote/declaration/appointment/status change with `AuditService.record(...)` in the same transaction. The audit table is append-only (trigger + grants).
- **Legally significant acts** (vote, conflict-of-interest declaration, issuing a recommendation) call `StepUpVerifier.requireRecent(...)` and must never be queued offline. Only attendance marks and absence apologies may go through the frontend outbox.
- **Money** is `BigDecimal` / `numeric(20,2)` with a currency; amendment totals are in the contract's own currency, no conversion.
- **Errors**: throw `ApiException` with a stable `code`; the frontend translates `errors.<code>`. Add new codes to all three locale files.
- **Frontend**: the access token lives only in memory (`src/api/tokenStore.ts`); never put it in web storage, IndexedDB or the SW cache. Every mutating request carries an `Idempotency-Key`, reused on retry. All instants are displayed in Africa/Kigali.

## Testing notes

- Integration tests extend `support.IntegrationTest`; seed cross-tenant data as the owner with `support.Owner` / `support.Fixtures`.
- `auth.internal.TestActors` creates users, signs them in, enrols TOTP for officers (CBM, Chair, Vice, Secretary have mandatory MFA) and performs step-up.
- TOTP codes are single-use per 30 s step: a test or script that enrols and then steps up must use a later step, or the server rightly rejects the replay.
- Browser screenshots: Playwright in Docker (`mcr.microsoft.com/playwright:v1.48.0-jammy`, `--network host`). The app polls, so wait for `load` plus a fixed delay, not `networkidle`.
