import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import SiteLayout from '@/layouts/SiteLayout.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'

/**
 * 两套路由树:
 *   /            C 端(业主)     契约 openapi-c.yaml
 *   /admin       B 端(后台运营) 契约 openapi-admin.yaml
 *
 * 均为**前端只连 Java**(CLAUDE.md 架构不变量 3),请求经 /api/v1 前缀走 Java。
 *
 * 后台页面多数还是占位页(见各菜单项的 `task` 字段),按 F-0.3 ~ F-0.9 逐个落地。
 * 先建路由是为了**让权限门控现在就可验证** —— 菜单与守卫的逻辑不依赖具体页面。
 */
const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: SiteLayout,
      children: [
        {
          path: '',
          name: 'home',
          component: () => import('@/views/HomeView.vue'),
        },
      ],
    },
    {
      path: '/admin/login',
      name: 'admin-login',
      component: () => import('@/views/admin/AdminLoginView.vue'),
      meta: { public: true },
    },
    {
      path: '/admin',
      component: AdminLayout,
      children: [
        {
          path: '',
          name: 'admin-home',
          component: () => import('@/views/admin/AdminHomeView.vue'),
        },
        {
          path: 'products',
          name: 'admin-products',
          component: () => import('@/views/admin/AdminProductsView.vue'),
        },
        {
          path: 'products/new',
          name: 'admin-product-new',
          component: () => import('@/views/admin/AdminProductEditView.vue'),
        },
        {
          // 放在 products 之后即可:vue-router 匹配的是静态段 'new',不会与 :id 冲突
          path: 'products/:id',
          name: 'admin-product-edit',
          component: () => import('@/views/admin/AdminProductEditView.vue'),
        },
        {
          path: 'products/import',
          name: 'admin-products-import',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.4', title: '批量导入' },
        },
        {
          path: 'combos',
          name: 'admin-combos',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.5', title: '推荐搭配' },
        },
        {
          path: 'templates',
          name: 'admin-templates',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.6', title: '户型模板' },
        },
        {
          path: 'furnish',
          name: 'admin-furnish',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.7', title: '陈设风格' },
        },
        {
          path: 'quote-params',
          name: 'admin-quote-params',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.8', title: '报价参数' },
        },
        {
          path: 'dicts',
          name: 'admin-dicts',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.8', title: '数据字典' },
        },
        {
          path: 'store',
          name: 'admin-store',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.9', title: '门店信息' },
        },
        {
          path: 'accounts',
          name: 'admin-accounts',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.2', title: '账号与权限' },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/AdminPlaceholderView.vue'),
          meta: { task: 'F-0.2', title: '操作日志' },
        },
      ],
    },
    {
      // 兜底:未匹配的路径回首页。放在最后,以免吃掉上面的具体路由。
      path: '/:pathMatch(.*)*',
      redirect: '/',
    },
  ],
})

/**
 * 后台登录守卫。
 *
 * ⚠️ **这不是安全边界** —— 前端没有令牌,拦不拦得住都不影响服务端。
 * 它的作用只是别让用户走进一个注定 401 的页面。真正的拦截在服务端(403/10004)。
 *
 * v1.9 起:守卫会向服务端确认会话(`/auth/me`),而不是看本地有没有缓存 ——
 * 令牌在 HttpOnly Cookie 里,前端读不到,所以"是否登录"这件事**只有服务端知道**。
 * `ensureSession` 内部有 `verified` 标记,每次页面加载只问一次。
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!to.path.startsWith('/admin')) return true

  const auth = useAdminAuthStore()
  if (await auth.ensureSession()) return true

  // 带上原目标,登录后回到用户本来要去的地方
  return { name: 'admin-login', query: { redirect: to.fullPath } }
})

export default router
