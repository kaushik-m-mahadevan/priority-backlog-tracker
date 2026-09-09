# Priority Backlog Tracker

Shared web app for a small founding team to log, rank, and act on a prioritized backlog.
It surfaces what to work on next (Top 10), the fastest wins available (Quick Wins), items
that are quietly stalling (Needs Attention), and who owns what (Owner Workload).

Full specification: [`docs/design.md`](docs/design.md).
Build roadmap: features are landed one at a time — see the commit history.

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

Open http://localhost:8080 and sign in as **`test123` / `test123`**. The `demo` profile
seeds ~20 live items, a handful of completed items, and three sample founders so every
view has something in it. Data lives in the embedded MongoDB and resets on restart.

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

Once your MongoDB is up, start the app once against it (creates the config document and
the `test123` account), then:

```bash
mongosh "mongodb://localhost:27017/backlog" scripts/seed-demo.mongosh.js
```

The script is safe to re-run — it replaces only the rows it created (`createdBy =
"demo-script"`) and never touches items you added yourself.

## Profiles

| Profile | MongoDB | Data | Login | Use for |
|---|---|---|---|---|
| _(none)_ | embedded, in-memory | empty | `test123` / `test123` | quick local run |
| `demo` | embedded, in-memory | sample backlog seeded, resets on restart | `test123` / `test123` | offline demo / manual UI testing |
| `dev` | Atlas, `dev` database | sample backlog seeded (idempotent) | `test123` / `test123` (override via `SEED_USER_*`) | shared dev/testing against the real cluster |
| `prod` | Atlas, `prod` database | real data only, no seeding | none — create a user (see below) | production |

`dev` and `prod` share one cluster via **`MONGODB_URI`** and differ only by database
name. Give `MONGODB_URI` **no trailing `/database`** — each profile picks its own
(`MONGO_DB` overrides). `prod` refuses to boot with `JWT_SECRET=test123` or a
`test123` seed password (`ProdSanityCheck`).

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
`config`, `configHistory`, `counters`) on first connect — nothing to pre-create in Atlas.

### First prod login

`prod` seeds no account. Either boot once with the seed user turned on and real
credentials (`SEED_USER_ENABLED=true SEED_USER_EMAIL=… SEED_USER_PASSWORD=… SEED_USER_CODE=…`,
idempotent, unset afterwards), or insert a user document directly.

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
| `SEED_USER_ENABLED` / `SEED_USER_EMAIL` / `SEED_USER_PASSWORD` / `SEED_USER_NAME` / `SEED_USER_CODE` | Seeded login account | on in dev, off in prod |

## API surface (current)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/login` | `{email,password}` → `{token,user}` |
| `GET` | `/api/auth/me` | current user |
| `GET` | `/api/config` | ranking config |
| `GET`/`POST` | `/api/items` | search / filter / paginate live items (`q`, `owner`, `category`, `priority`, `status`, `page`, `size`) → `PageResponse`; create |
| `GET`/`PUT` | `/api/items/{id}` | fetch / full update (send the `version` you read for optimistic-concurrency; stale writes get `409`) |
| `PATCH` | `/api/items/{id}/status` | `BACKLOG` ⇄ `IN_PROGRESS` |
| `POST` | `/api/items/{id}/complete` | `{terminalStatus}` → moves to archive |
| `GET` | `/api/items/top` | Top 10 by score |
| `GET` | `/api/items/quick-wins` | fastest first |
| `GET` | `/api/archived` | completed items, paginated (`page`, `size`) → `PageResponse` |
| `GET` | `/api/insights/needs-attention` | stale & buried |
| `GET` | `/api/insights/workload` | per-owner workload |
| `GET` | `/api/insights/completions` | count finished in the last `days` (feeds the grove) |
| `PUT` | `/api/config` | replace weights & thresholds (Owner only) |
| `GET` | `/api/config/history` | last 30 config changes |
| `POST`/`DELETE` | `/api/config/categories`, `/api/config/priorities` | add / safe-remove list values (Owner only) |

Interactive API docs (springdoc) are served at `/swagger-ui.html` when the app is running.

## Deployment

Render.com free tier, GitHub-connected auto-deploy from `main`.

- **Build:** `./mvnw clean package` (the frontend is compiled into the jar).
- **Start:** `java -jar target/*.jar --server.port=$PORT --spring.profiles.active=prod`
- **Environment:**

  | Var | Value |
  |---|---|
  | `MONGODB_URI` | Atlas connection string (a replica set, so transactions work) |
  | `JWT_SECRET` | a long random string — **not** `test123`; the `prod` profile refuses to start otherwise |
  | `MONGO_TRANSACTIONS_ENABLED` | `true` |

The `prod` profile (`application-prod.yml`) excludes the embedded MongoDB, disables
demo-data and the dev seed user, and runs a start-up sanity check (`ProdSanityCheck`)
that fails fast on a default secret. To keep a sleepy free-tier dyno warm while
someone has the app open, the SPA pings `/actuator/health` every few minutes; build
with `VITE_KEEPALIVE=off` to disable that.
