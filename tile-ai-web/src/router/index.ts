import { createRouter, createWebHistory } from 'vue-router'

import AdminLayout from '@/layouts/AdminLayout.vue'
import SiteLayout from '@/layouts/SiteLayout.vue'
import { useAdminAuthStore } from '@/stores/adminAuth'
import { useUserAuthStore } from '@/stores/userAuth'

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
          component: () => import('@/views/site/HomeView.vue'),
        },
        {
          path: 'products',
          name: 'products',
          component: () => import('@/views/site/ProductListView.vue'),
        },
        {
          path: 'products/:sku',
          name: 'product-detail',
          component: () => import('@/views/site/ProductDetailView.vue'),
        },
        {
          path: 'combos',
          name: 'combos',
          component: () => import('@/views/site/ComboListView.vue'),
        },
        {
          path: 'store',
          name: 'store',
          component: () => import('@/views/site/StoreView.vue'),
        },
        {
          // 录户型**不需要登录**(契约里 /layout/* 都是 security: [])——
          // 登录门槛落在下一步「提交生成」,与 PRD §5.1「浏览免登录」一致。
          path: 'layout',
          name: 'layout',
          component: () => import('@/views/site/LayoutEntryView.vue'),
        },
        {
          // 生成与查进度都要登录(契约 /generate 两个接口都是 cookieAuth)
          path: 'generate',
          name: 'generate',
          component: () => import('@/views/site/GenerateView.vue'),
          meta: { requiresAuth: true },
        },
        {
          // 方案是"谁的",服务端按 Cookie 判定 —— 契约 /plans 四条全都 cookieAuth。
          // 用 `/plans` 而不是 `/my-plans`:与后端 `/api/v1/plans` 同形,一眼能对上;
          // 也给将来的公开分享页 `/share/:token` 让开了前缀。
          path: 'plans',
          name: 'plans',
          component: () => import('@/views/site/MyPlansView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'plans/:id',
          name: 'plan-detail',
          component: () => import('@/views/site/PlanDetailView.vue'),
          meta: { requiresAuth: true },
        },
        {
          path: 'login',
          name: 'login',
          component: () => import('@/views/site/LoginView.vue'),
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
 * 登录守卫(两端共用)。
 *
 * ⚠️ **这不是安全边界** —— 前端没有令牌,拦不拦得住都不影响服务端。
 * 它的作用只是别让用户走进一个注定 401 的页面。真正的拦截在服务端
 * (C 端 `PROTECTED_C_PATHS` + `ROLE_USER`;后台 RBAC 的 403/10004)。
 *
 * v1.9 起:守卫会向服务端确认会话(`/auth/me`),而不是看本地有没有缓存 ——
 * 令牌在 HttpOnly Cookie 里,前端读不到,所以"是否登录"这件事**只有服务端知道**。
 * `ensureSession` 内部有 `verified` 标记,每次页面加载只问一次。
 *
 * C 端只有标了 `meta.requiresAuth` 的页面才拦(如「生成」);
 * 浏览产品库、录户型都免登录(PRD §5.1)。
 */
router.beforeEach(async (to) => {
  if (to.meta.public) return true

  if (to.path.startsWith('/admin')) {
    const auth = useAdminAuthStore()
    if (await auth.ensureSession()) return true
    // 带上原目标,登录后回到用户本来要去的地方
    return { name: 'admin-login', query: { redirect: to.fullPath } }
  }

  if (!to.meta.requiresAuth) return true

  const auth = useUserAuthStore()
  if (await auth.ensureSession()) return true

  // 登录页自己读 query.redirect 并 replace 回去(见 LoginView)
  return { name: 'login', query: { redirect: to.fullPath } }
})

export default router
