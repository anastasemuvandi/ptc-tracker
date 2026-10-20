# PTC Tracker — Operations

## First installation

```sh
cd deploy
cp .env.example .env
./scripts/generate-secrets.sh >> .env      # DB passwords + PTC_DATA_KEY
$EDITOR .env                               # public URL, SMTP relay, bootstrap admin email
docker compose up -d --build
docker compose logs -f backend             # invitation link appears here if mail is disabled
```

1. The database volume is initialised by `db/init/01-roles.sh`, which creates
   the runtime login roles `ptc_app` and `ptc_auth`. Flyway (running as the
   owner `ptc_owner`) creates the schema, RLS policies and grants.
2. On first start the backend generates an RS256 signing key into the `keys`
   volume (`PTC_GENERATE_KEYS=true`). Set it to `false` afterwards if you
   prefer startup to fail rather than silently create a new key.
3. `PTC_BOOTSTRAP_ADMIN_EMAIL` is invited as platform administrator if none
   exists. The platform administrator creates institutions and invites each
   institution's first administrator; that administrator invites everyone
   else. There is no self-registration.

Serve the `web` container behind the institution's TLS terminator. The
refresh cookie is `Secure`; set `PTC_COOKIE_SECURE=false` only for local
`http://localhost` trials.

## Secrets and keys

| Secret | Where | Notes |
|---|---|---|
| DB passwords | `.env` | three roles; the owner is used only by migrations |
| `PTC_DATA_KEY` | `.env` | AES-256 key for TOTP secrets at rest. **Back it up.** Losing it forces all users to re-enrol MFA (admin: Users → Reset MFA). |
| JWT signing keys | `keys` volume (`*.jwk.json`, mode 600) | never in the repository or image |
| VAPID keys | `.env` or generated into `keys` volume | Web Push |

### Signing-key rotation

Keys rotate automatically every 90 days (`ptc.auth.key-rotation-interval`)
and on demand: `POST /api/platform/keys/rotate` (platform administrator).
The previous public key stays in `/.well-known/jwks.json` for 24 h
(`ptc.auth.key-overlap`, must exceed the 15-minute access-token lifetime), so
no session is interrupted. Every backend instance re-reads the key directory
every minute; run multiple instances against the same volume.

### Revoking access

* Disable a user, change roles or committee membership → `token_version`
  increments → that user's access tokens are rejected on the next request.
* Users → *Revoke sessions* (or a refresh-token reuse event) revokes the
  refresh-token family as well.

## Database

* Back up the `db-data` volume with `pg_dump` as `ptc_owner` (daily, kept on
  national infrastructure).
* The audit trail (`audit_event`) cannot be updated or deleted by any role —
  the trigger rejects it even for the owner. Archive by dump, not deletion.
* Tenant isolation is enforced by RLS on every tenant table. Never connect the
  application as `ptc_owner`.

## Configuration changes (Ministerial Order revisions)

Institution administrators (own institution) and the platform administrator
(platform defaults) add rules in **Settings → Rules** with an effective date,
reason and legal reference. Rules cannot be backdated. Each decision, session
and evaluation stores the snapshot it was judged under, so past records keep
their original rules.

Public holidays: **Settings → Holidays**. Islamic holidays are seeded as
provisional each year; confirm them when the government announces the dates.

## Device testing checklist (PWA)

Not automatable in CI; run on real devices before each release:

| Check | Android Chrome | iOS Safari 16.4+ (home-screen app) |
|---|---|---|
| Install prompt shown once; not again after dismissal | ☐ | ☐ (instructions sheet) |
| Cold start from home screen signs in silently (refresh cookie survives) | ☐ | ☐ |
| Offline: previously opened agenda, members, file summaries readable | ☐ | ☐ |
| Offline attendance mark queued, synced on reconnect | ☐ | ☐ |
| Offline vote / declaration blocked with message | ☐ | ☐ |
| Push notification received and opens the landing page | ☐ | ☐ |
| New version banner → reload picks up the new service worker | ☐ | ☐ |
