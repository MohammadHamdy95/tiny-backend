# URL Shortener API — design

Serves `s.hamdy.app`. Two very different surfaces on one host:

| Surface | Path | Consumer |
|---|---|---|
| Redirects | `/{slug}` | anyone opening a short link |
| API | `/api/v1/*` | the frontend at `s.hamdy.app` (same origin — no CORS) |

Machine-readable contract: [`../api/openapi.yaml`](../api/openapi.yaml) — the
source of truth. This file explains the decisions.

## Endpoints

| Method | Path | Purpose |
|---|---|---|
| GET | `/{slug}` | 302 redirect to the target URL, increments click count |
| POST | `/api/v1/links` | create a short link (anonymous; optional custom slug) |
| GET | `/api/v1/links/{slug}` | link details + click stats (knowing the slug is the access) |
| GET | `/api/v1/health` | liveness (used by Caddy/monitoring) |

**No auth, no accounts, no tracking — by design** (same philosophy as the
paste service). Anyone can shorten a URL and share it. Consequences:

- No list endpoint (a global list would expose every creator's links) and
  no delete endpoint (no owner to scope deletion to). Cleanup is the
  5-year TTL or the operator.
- The UI's "recent links" panel is localStorage in the creator's browser —
  the server keeps no per-user anything.
- Abuse guards are URL validation plus rate limiting at Cloudflare
  (429 reserved in the contract).

## Conventions (shared with paste-backend)

- **Versioning**: URL prefix (`/api/v1`). Breaking changes → `/api/v2`; additive
  changes don't bump the version.
- **Errors**: RFC 9457 Problem Details (`application/problem+json`) with
  `type`, `title`, `status`, `detail` — never stack traces or HTML.
- **Rate limiting**: 429 + `Retry-After` on create/redirect abuse (enforcement
  can start at Cloudflare; the contract reserves the status code).
- **JSON naming**: camelCase; timestamps are RFC 3339 UTC (`2026-08-02T12:00:00Z`).

## Design decisions

**302, not 301, for redirects.** A 301 is cached aggressively by browsers and
Cloudflare, which would (a) make deleted/edited links live on in caches and
(b) swallow click counts. 302 keeps every hit on the backend. If analytics
ever move to the edge, switching to 301 is a one-line change.

**Slugs.** `[A-Za-z0-9_-]{1,64}` for custom slugs; generated ones are 7-char
base62 (~3.5 × 10^12 space — collision retry on write). Reserved (because the
UI and slugs share one host): `api`, `assets`, `index.html`, `favicon.ico`,
`robots.txt`, plus anything the frontend build emits at root.

**Click stats.** v1 is a single atomic counter on the link item (DynamoDB
`ADD`) — cheap and race-free. Per-day/referrer breakdowns would move to an
event stream later; the API shape (`stats` object) leaves room for that.

**Expiry.** Every link expires — default **5 years** from creation
(configurable via `SHORTENER_DEFAULT_TTL_DAYS`), or an earlier
`expiresAt` given on create. Enforced at redirect time (expired → 404);
DynamoDB TTL on `expires_at_epoch` garbage-collects the rows. The
response always echoes the effective `expiresAt`.
