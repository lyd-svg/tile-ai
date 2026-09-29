import { createRouter, createWebHistory } from 'vue-router'

import HomeView from '@/views/HomeView.vue'

/**
 * 两套路由树:
 *   /            C 端(业主)     契约 openapi-c.yaml
 *   /admin       B 端(后台运营) 契约 openapi-admin.yaml
 *
 * 均为**前端只连 Java**(CLAUDE.md 架构不变量 3),请求经 /api 前缀走 Java。
 * 具体页面自 T-1.x / 阶段 0 各后台任务起逐个落地。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      name: 'home',
      component: HomeView,
    },
    {
      path: '/admin',
      name: 'admin-home',
      component: () => import('@/views/admin/AdminHomeView.vue'),
    },
  ],
})

export default router
