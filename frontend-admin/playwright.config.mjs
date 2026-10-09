import { defineConfig } from '@playwright/test'

export default defineConfig({
  testDir: './tests',
  testIgnore: '**/integration/**',
  fullyParallel: true,
  workers: 2,
  reporter: 'list',
  use: {
    browserName: 'chromium',
    baseURL: 'http://127.0.0.1:3301',
    trace: 'retain-on-failure',
  },
  webServer: [{
    command: 'npm run dev -- --host 127.0.0.1 --port 3301 --mode auth-fixtures',
    url: 'http://127.0.0.1:3301',
    reuseExistingServer: false,
    env: { VITE_API_BASE_URL: '', VITE_API_PROXY_TARGET: 'http://127.0.0.1:8080' },
  }, {
    command: 'npm --prefix ../frontend-user run dev -- --host 127.0.0.1 --port 3300 --mode auth-fixtures',
    url: 'http://127.0.0.1:3300/shop/login',
    reuseExistingServer: false,
    env: { VITE_API_BASE_URL: '', VITE_API_PROXY_TARGET: 'http://127.0.0.1:8080' },
  }],
})
