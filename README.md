# rekordo-backend

Backend for **Rekordo**, a record-collection tracker for vinyl, CD, cassette and
digital copies.

The app is **local-first**: it works fully with no account, keeping everything in the
client's local store. An account only adds cross-device sync. This service therefore does
*not* serve the collection — there are no CRUD endpoints for copies. It provides:

- **auth** — self-issued JWT, short-lived access token plus an httpOnly refresh cookie
- **sync** — per-entity, field-level last-write-wins reconciliation
- **metadata** — a cached, rate-limited proxy in front of MusicBrainz and the Cover Art
  Archive, open to unauthenticated callers
- **image storage** — user-uploaded sleeve photos, to MinIO

See [`docs/PLAN.md`](docs/PLAN.md) for the full design and the decisions behind it.

## Stack

Java 25 · Spring Boot 4.1 · Spring Web MVC · Spring Data JPA · Flyway · PostgreSQL ·
springdoc-openapi · Bucket4j + Caffeine

## Running locally

```bash
docker compose up -d          # Postgres on :5432
export MC_JWT_SECRET=dev-only-secret-at-least-32-characters-long
mvn spring-boot:run
```

- API: <http://localhost:8080/api/v1/health>
- OpenAPI UI: <http://localhost:8080/swagger-ui.html>

`MC_JWT_SECRET` has no default on purpose — the service fails to start rather than run on a
known secret.

## Tests

```bash
mvn test
```
