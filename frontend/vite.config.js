import { defineConfig, loadEnv } from 'vite'
import vue from '@vitejs/plugin-vue'

function parsePort(value, fallback) {
  const n = Number.parseInt(String(value ?? ''), 10)
  if (Number.isFinite(n) && n > 0 && n < 65536) return n
  return fallback
}

function buildProxy(target) {
  return {
    '/api': {
      target,
      changeOrigin: true
    },
    '/files': {
      target,
      changeOrigin: true
    },
    '/ws/im': {
      target,
      changeOrigin: true,
      ws: true
    }
  }
}

export default defineConfig(({ mode }) => {
  // Vite config runs in Node; use loadEnv so `.env*` changes take effect without exporting vars.
  const env = { ...loadEnv(mode, process.cwd(), ''), ...process.env }

  // Local same-origin API, file, and WebSocket traffic proxies to the gateway by default.
  const devPort = parsePort(env.VITE_DEV_PORT || env.VITE_PORT, 12881)
  const previewPort = parsePort(env.VITE_PREVIEW_PORT || env.VITE_PORT, devPort)
  const devProxyTarget = env.VITE_DEV_PROXY_TARGET || 'http://localhost:12880'
  const previewProxyTarget = env.VITE_PREVIEW_PROXY_TARGET || devProxyTarget

  return {
    plugins: [vue()],
    test: {
      environment: 'jsdom',
      setupFiles: './src/test/setup.js'
    },
    server: {
      port: devPort,
      strictPort: true,
      proxy: buildProxy(devProxyTarget)
    },
    preview: {
      port: previewPort,
      strictPort: true,
      proxy: buildProxy(previewProxyTarget)
    }
  }
})
