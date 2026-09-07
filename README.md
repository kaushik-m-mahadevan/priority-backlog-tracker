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
| Frontend | React + Vite, built by `frontend-maven-plugin` and served as static assets from the JAR (added in a later step) |
| Auth | Spring Security + JWT |

## Running locally

Requires JDK 21. No local MongoDB or Node install is needed — the embedded MongoDB and
the frontend Node toolchain are both provisioned by the build.

```bash
./mvnw spring-boot:run
```

Then open http://localhost:8080. Health check: http://localhost:8080/actuator/health.

## Configuration

| Env var | Purpose | Local default |
|---|---|---|
| `PORT` | HTTP port | `8080` |
| `MONGODB_URI` | MongoDB connection string | embedded MongoDB |
| `JWT_SECRET` | JWT signing secret | `test123` (dev only — override in production) |

## Build

```bash
./mvnw clean package
java -jar target/*.jar
```

## Deployment

Render.com free tier, GitHub-connected auto-deploy from `main`:
build `./mvnw clean package`, start `java -jar target/*.jar --server.port=$PORT`,
with `MONGODB_URI` and `JWT_SECRET` set as environment variables.
