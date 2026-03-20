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

  // Local browser traffic now defaults to gateway-first (12881 -> 12880).
  const devPort = parsePort(env.VITE_DEV_PORT || env.VITE_PORT, 12881)
  const previewPort = parsePort(env.VITE_PREVIEW_PORT || env.VITE_PORT, devPort)
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:12880'
  const previewProxyTarget = env.VITE_PREVIEW_PROXY_TARGET || devProxyTarget

  const devProxy = {
    '/api': {
      target: devProxyTarget,
      changeOrigin: true
    }
  }

  const previewProxy = {
    '/api': {
      target: previewProxyTarget,
      changeOrigin: true
    }
  }

  return {
    plugins: [vue()],
    server: {
      port: devPort,
      strictPort: true,
      proxy: devProxy
    },
    preview: {
      port: previewPort,
      strictPort: true,
      proxy: previewProxy
    }
  }
})
