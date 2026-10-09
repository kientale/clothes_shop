import { defineConfig } from '@playwright/test'

// Started by FrontendAuthIntegrationTests against its isolated PostgreSQL and random HTTP port.
const target = process.env.VITE_API_PROXY_TARGET
if (!target) throw new Error('Run this suite through RUN_FRONTEND_AUTH_E2E=true and mvnw verify.')
const env = { VITE_API_BASE_URL: '', VITE_API_PROXY_TARGET: target }

export default defineConfig({
  testDir: './tests/integration',
  workers: 1,
  reporter: 'list',
  outputDir: '../target/frontend-auth-results',
  use: { browserName: 'chromium', baseURL: 'http://127.0.0.1:3401', trace: 'retain-on-failure' },
  webServer: [{
    command: 'npm run dev -- --host 127.0.0.1 --port 3401 --mode auth-backend',
    url: 'http://127.0.0.1:3401', env,
  }, {
    command: 'npm --prefix ../frontend-user run dev -- --host 127.0.0.1 --port 3400 --mode auth-backend',
    url: 'http://127.0.0.1:3400/shop/login', env,
  }],
})
