import { defineConfig, devices } from "@playwright/test";

const remoteBaseURL = process.env.E2E_BASE_URL;
const localBaseURL = "http://localhost:3000";

export default defineConfig({
  testDir: "./e2e",
  forbidOnly: !!process.env.CI,
  workers: 1,
  reporter: [["list"], ["html", { open: "never" }]],
  use: {
    baseURL: remoteBaseURL || localBaseURL,
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
    video: "retain-on-failure",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: remoteBaseURL
    ? undefined
    : {
        command: "pnpm start",
        url: localBaseURL,
        reuseExistingServer: !process.env.CI,
        env: { MOCK: "off" },
      },
});
