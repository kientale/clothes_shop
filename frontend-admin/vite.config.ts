import { loadEnv, defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Dev server proxies /api to the Spring Boot backend, so no CORS setup is needed locally.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  return {
    cacheDir: `node_modules/.vite/${mode}`,
    plugins: [react()],
    server: {
      port: 3001,
      strictPort: true,
      proxy: {
        '/api': env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
      },
    },
  }
})
