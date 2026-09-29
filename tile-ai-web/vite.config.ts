import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vite'

export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  server: {
    port: 5173,
    proxy: {
      // 前端只连 Java —— Java 是唯一对外 API(CLAUDE.md 架构不变量 3)。
      // 开发期把 /api 代理到 Java 业务核;生产由 Nginx 反代(架构 §12.1)。
      // C 端 /api/v1 与后台 /api/v1/admin 都在此前缀下。
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
})
