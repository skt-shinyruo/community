import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

const devProxyTarget = process.env.VITE_DEV_PROXY_TARGET || 'http://localhost:12882'

export default defineConfig({
  plugins: [vue()],
  server: {
    port: 12881,
    strictPort: true,
    proxy: {
      '/api': {
        target: devProxyTarget,
        changeOrigin: true
      }
    }
  },
  preview: {
    port: 12881,
    strictPort: true
  }
})
