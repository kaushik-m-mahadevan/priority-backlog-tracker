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

## Configuration

| Env var | Purpose | Local default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGODB_URI` | MongoDB connection string | embedded MongoDB |
| `JWT_SECRET` | JWT signing secret | `test123` (dev only — override in production) |
| `JWT_EXPIRATION_MINUTES` | Token lifetime | `1440` |
| `DEMO_DATA_ENABLED` | Seed sample data on startup (the `demo` profile sets this) | `false` |
| `MONGO_TRANSACTIONS_ENABLED` | Use real transactions for the archive move — set `true` only on a replica set / Atlas | `false` |
| `SEED_USER_ENABLED` / `SEED_USER_EMAIL` / `SEED_USER_PASSWORD` | Dev login account | `true` / `test123` / `test123` |

## API surface (current)

| Method | Path | Notes |
|---|---|---|
| `POST` | `/api/auth/login` | `{email,password}` → `{token,user}` |
| `GET` | `/api/auth/me` | current user |
| `GET` | `/api/config` | ranking config |
| `GET`/`POST` | `/api/items` | list live items / create |
| `GET`/`PUT` | `/api/items/{id}` | fetch / full update |
| `PATCH` | `/api/items/{id}/status` | `BACKLOG` ⇄ `IN_PROGRESS` |
| `POST` | `/api/items/{id}/complete` | `{terminalStatus}` → moves to archive |
| `GET` | `/api/items/top` | Top 10 by score |
| `GET` | `/api/items/quick-wins` | fastest first |
| `GET` | `/api/archived` | completed items |
| `GET` | `/api/insights/needs-attention` | stale & buried |
| `GET` | `/api/insights/workload` | per-owner workload |

## Deployment

Render.com free tier, GitHub-connected auto-deploy from `main`:
build `./mvnw clean package`, start `java -jar target/*.jar --server.port=$PORT`,
with `MONGODB_URI`, `JWT_SECRET`, and `MONGO_TRANSACTIONS_ENABLED=true` set as
environment variables.
