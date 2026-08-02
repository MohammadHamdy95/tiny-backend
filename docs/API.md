# URL Shortener API — design

Serves `s.hamdy.app`. Two very different surfaces on one host:

| Surface | Path | Consumer |
|---|---|---|
| Redirects | `/{slug}` | anyone opening a short link |
| Management API | `/api/v1/*` | the frontend at `s.hamdy.app` (same origin — no CORS) |

Machine-readable contract: [`../api/openapi.yaml`](../api/openapi.yaml) — the
source of truth. This file explains the decisions.

## Endpoints

| Method | Path | Auth | Purpose |
|---|---|---|---|
| GET | `/{slug}` | — | 302 redirect to the target URL, increments click count |
| POST | `/api/v1/links` | admin | create a short link (optional custom slug) |
| GET | `/api/v1/links` | admin | list links, cursor-paginated |
| GET | `/api/v1/links/{slug}` | admin | link details + click stats |
| DELETE | `/api/v1/links/{slug}` | admin | delete a link |
| GET | `/api/v1/health` | — | liveness (used by Caddy/monitoring) |

## Conventions (shared with paste-backend)

- **Versioning**: URL prefix (`/api/v1`). Breaking changes → `/api/v2`; additive
  changes don't bump the version.
- **Auth**: `Authorization: Bearer <token>` with the single admin token
  (`SHORTENER_ADMIN_TOKEN` env var). Constant-time comparison. 401 for
  missing/bad token. Multi-user auth is explicitly out of scope for v1.
- **Errors**: RFC 9457 Problem Details (`application/problem+json`) with
  `type`, `title`, `status`, `detail` — never stack traces or HTML.
- **Pagination**: cursor-based (`?limit=` + `?cursor=`, response carries
  `nextCursor`) — maps directly onto DynamoDB's `LastEvaluatedKey`; offset
  pagination can't be implemented efficiently there.
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

**Expiry.** Optional `expiresAt` on create, enforced at redirect time
(expired → 404) and cleaned up by DynamoDB TTL on the same attribute.
