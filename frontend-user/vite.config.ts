import { loadEnv, defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// One React app serves the homepage (/) and the shop (/shop/...); Vite's SPA fallback covers deep links.
// The dev server proxies /api to the Spring Boot backend, so no CORS setup is needed locally.
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, '.', 'VITE_')
  return {
    cacheDir: `node_modules/.vite/${mode}`,
    plugins: [react()],
    // Scan the active app only; archived HTML in legacy/ may reference unused packages.
    optimizeDeps: {
      entries: ['index.html'],
    },
    server: {
      port: 3000,
      strictPort: true,
      proxy: {
        '/api': env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
        // The backend builds the sitemap from the live catalog; production proxies map it the same way.
        '/sitemap.xml': {
          target: env.VITE_API_PROXY_TARGET || 'http://localhost:8080',
          rewrite: () => '/api/v1/store/sitemap.xml',
        },
      },
    },
  }
})
