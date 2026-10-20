# PTC Tracker

Internal progress tracker for Public Tender Committees in Rwandan public
institutions. It follows the committee's **process** — what is assigned,
which stage it has reached, what is overdue, what was decided and on what
grounds. **Umucyo remains the system of record**: no bid documents, bidder
submissions, prices or evaluation scores are stored here, and nothing is
published, opened or awarded from this application. Tenders, contracts and
bidders are referenced by reference only.

* Multi-tenant (institution → committee → membership), PostgreSQL row-level
  security keyed on the authenticated token.
* Self-issued RS256 JWTs, rotating refresh tokens with reuse detection,
  Argon2id, TOTP MFA, step-up for votes / declarations / recommendations.
* Every threshold, period and rule is effective-dated configuration; records
  store the configuration they were judged under.
* Installable PWA (English, Kinyarwanda, French), offline reading, offline
  attendance only.

## Repository

| Path | |
|---|---|
| `backend/` | Java 21, Spring Boot 3.5, Flyway, JPA, Quartz, Apache POI |
| `frontend/` | React 18, TypeScript, Vite, Tailwind, shadcn/ui, i18next, Workbox |
| `deploy/` | Docker Compose for on-premise installation |
| `docs/ARCHITECTURE.md` | modules, conventions, request lifecycle, DB roles |
| `docs/API.md` | HTTP contract |
| `docs/OPERATIONS.md` | installation, keys, rotation, backups, device checklist |

## Development

Backend (needs Docker for the Testcontainers PostgreSQL):

```sh
cd backend
mvn test                     # unit + integration tests (PostgreSQL 16 via Testcontainers)
```

Run locally against a dev database:

```sh
docker run -d --name ptc-db -e POSTGRES_DB=ptc -e POSTGRES_USER=ptc_owner \
  -e POSTGRES_PASSWORD=owner -e PTC_DB_APP_PASSWORD=app -e PTC_DB_AUTH_PASSWORD=auth \
  -v "$PWD/deploy/db/init:/docker-entrypoint-initdb.d:ro" -p 5432:5432 postgres:16-alpine
cd backend
PTC_DB_OWNER_PASSWORD=owner PTC_DB_APP_PASSWORD=app PTC_DB_AUTH_PASSWORD=auth \
PTC_GENERATE_KEYS=true PTC_COOKIE_SECURE=false PTC_BOOTSTRAP_ADMIN_EMAIL=admin@example.rw \
  mvn spring-boot:run
```

Frontend (dev server proxies `/api` to `localhost:8080`):

```sh
cd frontend
npm install
npm run dev
```

Production: see `docs/OPERATIONS.md`.
