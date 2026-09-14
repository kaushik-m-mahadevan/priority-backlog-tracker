import { defineConfig } from "@playwright/test";

/**
 * Runs against the real packaged jar (demo profile: embedded Mongo, seeded
 * founders, no external dependencies) — not the Vite dev server — because the
 * whole point of this suite is to be the safety net for the platform-
 * integration migration, which restructures backend packages that only the
 * real jar's wiring can catch.
 *
 * `webServer` builds nothing itself: run `npm run build:jar` (from repo root,
 * `./mvnw -DskipTests package`) before `npm run e2e` locally, or let CI do it
 * as an earlier step. This config only starts/stops the already-built jar.
 */
export default defineConfig({
  testDir: "./e2e",
  fullyParallel: false, // shared demo dataset — tests must not race each other
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  // core-flow.spec.ts alone is ~10 sequential logins/page-loads/network round-trips
  // (register -> approve -> group-create -> invite -> accept -> create item) -- the
  // 30s default is marginal for that even with no other load; observed real runs land
  // 30-45s under a merely-busy machine, so this isn't padding for flakiness, it's
  // sizing the timeout to what the flow actually does.
  timeout: 60_000,
  use: {
    baseURL: "http://localhost:8080",
    trace: "retain-on-failure",
  },
  webServer: {
    command:
      'bash -c "java -jar $(ls ../../../target/priority-backlog-tracker-*.jar | head -1) --spring.profiles.active=demo"',
    url: "http://localhost:8080/actuator/health",
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
  },
});
