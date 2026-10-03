import { fileURLToPath, URL } from 'node:url'

import vue from '@vitejs/plugin-vue'
import { defineConfig } from 'vitest/config'

/**
 * 前端测试配置。
 *
 * 单独一份而不是并进 `vite.config.ts`:开发/构建那份是所有人每天都跑的,
 * 测试配置只跟测试有关,分开改互不牵连。
 *
 * 环境用 `happy-dom` —— 组件测试(F-0.2)需要 DOM。它比 jsdom 轻且够用;
 * api 层与纯逻辑的测试在 DOM 环境下同样照跑,不必为此分两套配置。
 *
 * 组件内用到 `sessionStorage`(后台登录态缓存),happy-dom 提供该 API。
 */
export default defineConfig({
  plugins: [vue()],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
  test: {
    environment: 'happy-dom',
    include: ['tests/**/*.spec.ts'],
    // 全局钩子:每条用例后清 `document.body` —— 见 `tests/setup.ts` 的说明
    // (antd 的 portal 弹层不会被自动回收,不清会漏到下一条用例里)。
    setupFiles: ['tests/setup.ts'],
  },
})
