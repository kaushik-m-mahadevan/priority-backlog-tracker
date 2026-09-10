# prod-reset

One-off tool: wipe the **prod** database down to a single `ADMIN` account
(Kaushik, `kaushik.m.mahadevan@gmail.com`, handle `@cessabit_kerl`, password
`password123`) and nothing else. Everything else — items, archived items, groups,
notifications, config history, counters, other users — is deleted. The `config`
collection is emptied too; the app reseeds default ranking weights/thresholds on its
next start.

## Run

From this folder, in PowerShell:

```powershell
# 1. see what's in prod right now (read-only)
.\run.ps1

# 2. do the reset
.\run.ps1 apply
```

Connection string is read from `..\..\config\dev.properties`
(`spring.data.mongodb.uri=`) — same Atlas cluster, the tool just targets the `prod`
database instead of `dev`. Override with `$env:MONGODB_URI` / `$env:MONGO_DB` if needed.

Needs JDK 21 and the MongoDB driver jars in `~/.m2` (already present after any
`mvnw` build; otherwise `..\..\mvnw -q dependency:resolve`).

## After

Log in at the app with `kaushik.m.mahadevan@gmail.com` / `password123`. You'll have
no group — create one from the Groups page. There is currently no in-app password
change, so treat `password123` as temporary until that screen exists.
