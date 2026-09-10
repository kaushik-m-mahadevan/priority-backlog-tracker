# Priority Backlog Tracker

Web app for teams to log, rank, and act on a prioritized backlog. Each view surfaces
something: what to work on next (**The Pecking Order** / Top 10), the fastest wins
(**Quick Wins**), items quietly stalling (**Needs Attention**), and who owns what
(**Workload**).

Work lives in **groups** — shared, owner-less workspaces. Anyone can register; an
**admin** approves the account; members invite each other into groups by email or
`@handle`.

Original ranking spec: [`docs/design.md`](docs/design.md). Onboarding / roles / groups
design: [`docs/design-onboarding-groups.md`](docs/design-onboarding-groups.md). Features
land one at a time — see the commit history.

## Tech stack

| Layer | Choice |
|---|---|
| Backend | Spring Boot 3.3, Java 21, executable JAR |
| Database | MongoDB (Spring Data MongoDB). Local dev uses an embedded MongoDB; production points `MONGODB_URI` at Atlas M0. |
| Frontend | React 18 + Vite + TypeScript, built by `frontend-maven-plugin` and served as static assets from the JAR |
| Auth | Spring Security + JWT |

## Quick start (with sample data)

Requires JDK 21 only — the embedded MongoDB and the frontend Node toolchain are both
provisioned by the build.

```bash
./mvnw clean package
java -jar target/*.jar --spring.profiles.active=demo
```

Open http://localhost:8080 and sign in as **`test123` / `test123`** (a local-only
bootstrap admin). The `demo` profile seeds ~20 live items, a handful of completed items,
a sample group ("Founders"), and three sample members so every view has something in it.
Data lives in the embedded MongoDB and resets on restart.

`./scripts/run-demo.ps1` does the same in one step.

## Running without sample data

```bash
./mvnw spring-boot:run          # backend only (no SPA build); API on :8080
./mvnw clean package && java -jar target/*.jar
```

## Frontend development

```bash
cd src/main/frontend
../../../src/main/frontend/node/npm run dev     # Vite dev server on http://localhost:5173
```

The dev server proxies `/api` and `/actuator` to `http://localhost:8080`, so run the
backend (`./mvnw spring-boot:run`) alongside it. `npm run build` writes the production
bundle into `target/classes/static/`; `mvn package` does this automatically.

## Loading sample data into your own MongoDB

Once your MongoDB is up, start the app once against it (creates the config document; in
`dev` the local-bootstrap also creates a `test123` admin), then:

```bash
mongosh "mongodb://localhost:27017/backlog" scripts/seed-demo.mongosh.js
```

The script is safe to re-run — it replaces only the rows it created (`createdBy =
"demo-script"`) and never touches items you added yourself.

## Profiles

| Profile | MongoDB | Data | Login | Use for |
|---|---|---|---|---|
| _(none)_ | embedded, in-memory | empty | `test123` / `test123` (local bootstrap) | quick local run |
| `demo` | embedded, in-memory | sample backlog + group seeded, resets on restart | `test123` / `test123` | offline demo / manual UI testing |
| `dev` | Atlas, `dev` database | sample backlog + group seeded (idempotent) | `test123` / `test123` (local bootstrap) | shared dev/testing against the real cluster |
| `prod` | Atlas, `prod` database | real data only, no seeding | register in the app, then get approved | production |

`dev` and `prod` share one cluster via **`MONGODB_URI`** and differ only by database
name. Give `MONGODB_URI` **no trailing `/database`** — each profile picks its own
(`MONGO_DB` overrides). `prod` refuses to boot with `JWT_SECRET=test123`
(`ProdSanityCheck`) and never runs the local-bootstrap admin
(`app.local-bootstrap.enabled: false`).

```bash
# dev  — throwaway data in the `dev` database
MONGODB_URI='mongodb+srv://USER:PASS@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority' \
  java -jar target/*.jar --spring.profiles.active=dev

# prod — real data in the `prod` database
MONGODB_URI='mongodb+srv://USER:PASS@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority' \
JWT_SECRET='<long random string>' \
  java -jar target/*.jar --spring.profiles.active=prod
```

The app creates the database and its collections (`items`, `archivedItems`, `users`,
`groups`, `notifications`, `config`, `configHistory`, `counters`) on first connect —
nothing to pre-create in Atlas.

### Roles, registration, and the admin

Two roles: **`ADMIN`** and **`USER`**. Anyone can `POST /api/auth/register`
(name / `@handle` / email / password); the account is created `PENDING` and can reach
nothing but `/api/auth/me` until an admin approves it. Approved users land in the app
with no group and create or get invited into one. The admin has exactly one extra
power — approving registrations — plus sole access to the ranking-formula settings;
in groups the admin is an ordinary member.

There is no in-app "make admin" flow. **Kaushik is the only intended admin.** On an
existing database `LegacyDataMigration` rewrites the old `OWNER` account to `ADMIN`
in place (and `CONTRIBUTOR`/`VIEWER` → `USER`, missing `status` → `ACTIVE`,
`userCode` → `handle`). Bootstrapping an admin into a brand-new prod database (no
legacy `OWNER` row) is out of scope — it would need a fresh onboarding path built
first. Locally, the `dev`/`demo`/none profiles seed a `test123` admin
(`UserSeeder`, gated by `app.local-bootstrap.enabled`).

## Configuration

| Env var | Purpose | Default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGODB_URI` | Atlas connection string, no trailing `/database` | embedded MongoDB (dev/demo) |
| `MONGO_DB` | Database name within the cluster | `dev` (dev profile) / `prod` (prod profile) |
| `JWT_SECRET` | JWT signing secret | `test123` (dev only — `prod` rejects this) |
| `JWT_EXPIRATION_MINUTES` | Token lifetime | `1440` |
| `DEMO_DATA_ENABLED` | Seed sample data on startup (`demo` and `dev` set this) | `false` |
| `MONGO_TRANSACTIONS_ENABLED` | Real transactions for the archive move — `true` only on a replica set / Atlas | `false` (`dev`/`prod` force `true`) |
| `app.local-bootstrap.enabled` (yaml, not env) | Seed a local `test123` admin on startup when the `users` collection is empty | `true` (base/`dev`), `false` (`prod`) |

## API surface (current)

Every backlog read/write is **group-scoped**: pass `groupId` (query param on `GET`, body
field on create) and you must be a member of that group or the call is `403`.

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/login` | `{email,password}` → `{token,user}` |
| `POST` | `/api/auth/register` | `{name,handle,email,password}` → `201 {token,user}`, account `PENDING` |
| `GET` | `/api/auth/me` | current user (role + status); the only endpoint a `PENDING` account may call |
| `GET` | `/api/admin/pending-users`, `/api/admin/users` | admin only |
| `POST` | `/api/admin/users/{id}/approve`, `/api/admin/users/{id}/reject` | admin only; reject silently deletes the `PENDING` row |
| `GET`/`POST` | `/api/groups` | my groups / create (capped at `maxGroupsPerUser`) |
| `GET` | `/api/groups/{id}` | group + members |
| `POST` | `/api/groups/{id}/invites` | `{to}` — an email or `@handle`; creates a `GROUP_INVITE` notification for that user |
| `DELETE` | `/api/groups/{id}/members/me` | leave; the last member leaving deletes the group and its items |
| `GET` | `/api/notifications` | `{items, pending}` — your inbox + unread count |
| `POST` | `/api/notifications/{id}/accept`, `/api/notifications/{id}/decline` | accept joins the group |
| `GET` | `/api/config` | ranking config |
| `GET`/`POST` | `/api/items` | search / filter / paginate live items (`groupId` required; `q`, `owner`, `category`, `priority`, `status`, `page`, `size`) → `PageResponse`; create |
| `GET`/`PUT` | `/api/items/{id}` | fetch / full update (send the `version` you read for optimistic-concurrency; stale writes get `409`) |
| `PATCH` | `/api/items/{id}/status` | `BACKLOG` ⇄ `IN_PROGRESS` |
| `POST` | `/api/items/{id}/complete` | `{terminalStatus}` → moves to archive |
| `GET` | `/api/items/top` | Top 10 by score (`groupId` required) |
| `GET` | `/api/items/quick-wins` | fastest first (`groupId` required) |
| `GET` | `/api/archived` | completed items, paginated (`groupId` required; `page`, `size`) → `PageResponse` |
| `GET` | `/api/insights/needs-attention` | stale & buried (`groupId` required) |
| `GET` | `/api/insights/workload` | per-owner workload (`groupId` required) |
| `GET` | `/api/insights/completions` | count finished in the last `days` (`groupId` required; feeds the grove) |
| `PUT` | `/api/config` | replace weights & thresholds (**admin only**) |
| `GET` | `/api/config/history` | last 30 config changes |
| `POST`/`DELETE` | `/api/config/categories`, `/api/config/priorities` | add / safe-remove list values (**admin only**) |

Interactive API docs (springdoc) are served at `/swagger-ui.html` when the app is running.

## Deployment (Render, Docker)

`Dockerfile` builds the jar (frontend included) and runs it under the `prod` profile;
`render.yaml` is a blueprint for a free-tier web service.

1. **Atlas:** create a DB user with readWrite on `prod`, allow Render's IPs (or
   `0.0.0.0/0`) in Network Access. Copy the `mongodb+srv://…` string, **no trailing
   `/database`**.
2. **Render:** New → Blueprint → pick this repo. It reads `render.yaml` and creates
   the service.
3. **Set `MONGODB_URI`** (blueprint marks it `sync:false`) in the service's
   Environment tab, then let the deploy run. `JWT_SECRET` is auto-generated;
   `SPRING_PROFILES_ACTIVE=prod` and `MONGO_TRANSACTIONS_ENABLED=true` come from the
   blueprint.
4. **First login:** if the database already has an `OWNER` account from an earlier
   version, `LegacyDataMigration` promotes it to `ADMIN` on boot — sign in with those
   credentials. A brand-new prod database has no admin and no way to make one in-app
   (see [Roles, registration, and the admin](#roles-registration-and-the-admin)).

Health check: `/actuator/health`. Without Docker you can still use Render's native
Java runtime — build `./mvnw -DskipTests clean package`, start
`java -jar target/*.jar --server.port=$PORT --spring.profiles.active=prod`, same env.

The `prod` profile (`application-prod.yml`) excludes the embedded MongoDB, disables
demo-data and the local-bootstrap admin, and runs `ProdSanityCheck` which fails fast on
a `test123` secret. Free instances sleep after ~15 min idle (≈1 min cold start); while
someone has a tab open the SPA pings `/actuator/health` every few minutes to hold it
awake (`VITE_KEEPALIVE=off` at build time disables that).
