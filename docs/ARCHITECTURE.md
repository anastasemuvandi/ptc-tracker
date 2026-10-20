# PTC Tracker — Architecture and conventions

PTC Tracker is an internal workflow and progress-tracking layer for Public
Tender Committees. **Umucyo remains the system of record.** The tracker never
stores bid documents, bidder submissions, prices or evaluation scores, and
never publishes tenders, opens bids or awards. Tenders, contracts and bidders
are referenced **by reference only**. Every record is internal working
material.

## Layout

```
backend/    Spring Boot 3.5 / Java 21 API (Maven)
frontend/   React 18 + TypeScript + Vite PWA
deploy/     docker-compose, Dockerfiles, nginx, DB role bootstrap
docs/       this file, API.md, OPERATIONS.md
```

## Backend packages (`rw.gov.ptc`)

| Package          | Responsibility |
|------------------|----------------|
| `auth.api`       | The **only** auth surface the rest of the app uses: `TokenVerifier`, `AuthPrincipal`, `StepUpVerifier`, `IdentityAdmin`, `AuthMailer`. |
| `auth.internal`  | Token issuance (RS256, nimbus), refresh rotation, JWKS + key rotation, Argon2id, TOTP, lockout, invitations, resets. Uses its own DB role `ptc_auth`. Replace this package to federate to an external IdP. |
| `tenancy`        | `TenantContext` + `TenantAwareDataSource` (sets `app.institution_id` on every connection checkout). |
| `security`       | Filter chain, `Access` authorisation helpers, read-only guard. |
| `idempotency`    | `Idempotency-Key` filter for all authenticated mutating `/api/**` calls. |
| `audit`          | `AuditService` — append-only trail, same transaction as the change. |
| `config`         | Effective-dated configuration (`ConfigService`, `ConfigSnapshot`, `ConfigKeys`). |
| `calendar`       | Holidays and working-day arithmetic (`WorkingDayCalendar`). |
| `institution`    | Institutions, users (profile read model), platform metrics. |
| `committee`      | Committees, memberships, composition rules, replacements, tracked tasks. |
| `file`           | Files register, stage workflow, reassignments, officer assignment, table requests. |
| `contract`       | Contract references and the contract timeline. |
| `session`        | Sessions, agenda (scope enforced), attendance, apologies, quorum. |
| `decision`       | Decisions, votes, dissent, conflict-of-interest declarations, CBM responses. |
| `evaluation`     | Type A stages A1–A6, clarification log, abnormal-price gate, cure countdown, bid validity clock; checklists for B/E/F/G. |
| `contractchange` | Type C amendments (cumulative value, dual test, route) and type D extensions. |
| `minutes`        | Minutes generation, acknowledgement, .docx export (Apache POI). |
| `notification`   | Email (guaranteed channel), in-app notifications, Web Push; SMS-ready channel interface. |
| `reminder`       | Quartz jobs scanning deadlines per institution. |
| `dashboard`      | Member / Chair / CBM dashboards and reports. |

## Rules every module follows

1. **UUID keys.** Entities extend `BaseEntity` (UUIDv7 assigned in Java,
   `gen_random_uuid()` DB default) or `TenantEntity` (adds `institutionId`,
   stamped from `TenantContext` on persist). Foreign keys are plain `UUID`
   fields — no JPA associations, no lazy proxies. Entities expose public
   fields and may be returned directly as JSON; `institutionId` is hidden.
2. **Business numbers** (`file_number`, `session_number`) come from
   `institution_counter`, never from UUIDs. UUIDs are never shown to users or
   printed in minutes/exports.
3. **Tenant isolation** is enforced by PostgreSQL RLS. Never filter by
   institution in application code *instead of* RLS; never use the owner
   role at runtime. Background jobs iterate `app_active_institution_ids()`
   and wrap work in `TenantContext.runAs(...)`.
4. **Money** is `BigDecimal` / `numeric(20,2)` with a `char(3)` currency. A
   contract's amendments are computed in the contract's own currency; no
   conversion.
5. **Time.** Store `Instant` (UTC). Calendar dates are `LocalDate` in
   Africa/Kigali (`Time.today()`). Working days via `WorkingDayCalendar`.
6. **Configuration, not code.** Every legal threshold/period/size/rule is read
   from `ConfigService.snapshot()`. Records that are judged against rules
   (decisions, sessions, evaluations, amendment assessments) store the
   snapshot JSON and are always re-read via `ConfigService.fromStored(...)`.
   New keys: add to `ConfigKeys` and seed a platform default in a migration.
7. **Audit.** Every create/update/status change/vote/declaration/appointment
   calls `AuditService.record(action, entityType, id, previous, current)` in
   the same transaction (`audit.snapshot(entity)` before mutating).
8. **Optimistic locking.** Mutable entities carry `@Version public Long
   version;` Update requests carry the version they were based on; a
   mismatch is a 409 `concurrent_modification`.
9. **Errors** are `ApiException` with a stable `code` (the frontend's i18n
   key under `errors.`), an English fallback message and `params`.
   Rule violations from configuration use `ApiException.rule(...)` (HTTP 422).
10. **Legally significant acts** (vote, conflict-of-interest declaration,
    recommendation issue) call `StepUpVerifier.requireRecent(principal, act)`
    and store the returned instant. They are never queued offline.
11. **JSON** is camelCase. JDBC result maps go through `Rows.camel(...)`.
12. **Migrations** are append-only Flyway files; never edit an applied one.
    New tenant tables need RLS + grants (see V9 pattern).

## Request lifecycle

```
nginx ─▶ BearerTokenFilter (verify JWT, token_version) ─▶ TenantContext
      ─▶ ReadOnlyGuardFilter ─▶ IdempotencyFilter ─▶ controller
      ─▶ JPA/JDBC via TenantAwareDataSource (SET app.institution_id …) ─▶ RLS
```

## Database roles

| Role        | Used by            | Can |
|-------------|--------------------|-----|
| `ptc_owner` | Flyway only        | Owns schema; never used at runtime. |
| `ptc_app`   | Application (JPA)  | Tenant tables under RLS; INSERT/SELECT only on audit. |
| `ptc_auth`  | `auth.internal`    | Credentials, tokens, identities; INSERT on audit. No domain data. |
