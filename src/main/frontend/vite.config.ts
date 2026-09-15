import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// Built output is written straight into the Spring Boot static resources so `mvn package`
// bundles the SPA into the jar. `npm run dev` proxies the API to the running backend.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: "../../../target/classes/static",
    emptyOutDir: true,
  },
  server: {
    port: 5173,
    proxy: {
      "/api": "http://localhost:8080",
      "/actuator": "http://localhost:8080",
    },
  },
  test: {
    // Playwright owns e2e/ (its `test` global isn't vitest's) — keep the two
    // runners from tripping over each other's spec files.
    exclude: ["e2e/**", "node_modules/**"],
    environment: "jsdom",
    setupFiles: ["./src/test-setup.ts"],
  },
});
