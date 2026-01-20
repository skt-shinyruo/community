import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

function parsePort(value, fallback) {
  const n = Number.parseInt(String(value ?? ''), 10)
  if (Number.isFinite(n) && n > 0 && n < 65536) return n
  return fallback
}

export default defineConfig(({ mode }) => {
  // Vite config runs in Node; use loadEnv so `.env*` changes take effect without exporting vars.
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env }

  // Default keeps current convention (12881/12882) to avoid Origin whitelist drift.
  const devPort = parsePort(env.VITE_DEV_PORT || env.VITE_PORT, 12881)
  const previewPort = parsePort(env.VITE_PREVIEW_PORT || env.VITE_PORT, devPort)
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:12882'

  return {
    plugins: [vue()],
    server: {
      port: devPort,
      strictPort: true,
      proxy: {
        '/api': {
          target: devProxyTarget,
          changeOrigin: true
        }
      }
    },
    preview: {
      port: previewPort,
      strictPort: true
    }
  }
})
