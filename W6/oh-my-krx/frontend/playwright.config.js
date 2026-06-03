import { defineConfig } from "@playwright/test";

export default defineConfig({
  testDir: "./tests",
  timeout: 30_000,
  expect: {
    timeout: 10_000
  },
  use: {
    baseURL: process.env.APP_URL || "http://localhost:5173",
    trace: "on-first-retry"
  },
  webServer: process.env.SKIP_WEB_SERVER
    ? undefined
    : {
        command: "npm run dev",
        url: "http://127.0.0.1:5173/health",
        reuseExistingServer: true,
        timeout: 120_000
      }
});
