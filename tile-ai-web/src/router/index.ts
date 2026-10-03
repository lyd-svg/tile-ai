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
          // AI 咨询(F-1.6)。**要登录** —— 契约里 /agent/chat 与两条对话记录
          // 都是 cookieAuth(PRD §5.2.3「向 AI 提问前必须登录」),而且对话要留痕。
          path: 'consult',
          name: 'consult',
          component: () => import('@/views/site/ConsultView.vue'),
          meta: { requiresAuth: true },
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
      // 公开只读分享页(F-1.10)。
      //
      // ⚠️ **刻意不挂在 SiteLayout 下**,理由有两条:
      //   1. 拿到链接的家人 / 设计师 / 门店店员**没有账号**,不该看到
      //      「我的方案」「生成」这些只对登录用户有意义的导航(点进去只会被守卫
      //      弹去登录页,而他们根本没有登录页可去);
      //   2. `meta.public` 让守卫直接放行 —— 与 `/admin/login` 同一口径,
      //      是**显式声明**而不是"恰好没写 requiresAuth"。
      //
      // ⚠️ 路径**必须与后端拼出来的链接逐字一致**:`PlanShareService.SHARE_PATH`
      // 是 `/share/`,最终链接 = `tile.share.base-url + "/share/" + token`。
      // 这里改成别的形状,后端发出去的链接就会落到下面的兜底重定向、回首页。
      path: '/share/:token',
      name: 'share',
      component: () => import('@/views/site/ShareView.vue'),
      meta: { public: true },
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
          component: () => import('@/views/admin/AdminProductImportView.vue'),
        },
        {
          path: 'combos',
          name: 'admin-combos',
          component: () => import('@/views/admin/AdminCombosView.vue'),
        },
        {
          // 新增放在 :id 之前 —— 静态段优先级更高,不会与下面的 :id 冲突
          path: 'combos/new',
          name: 'admin-combo-new',
          component: () => import('@/views/admin/AdminComboEditView.vue'),
        },
        {
          path: 'combos/:id',
          name: 'admin-combo-edit',
          component: () => import('@/views/admin/AdminComboEditView.vue'),
        },
        {
          path: 'templates',
          name: 'admin-templates',
          component: () => import('@/views/admin/AdminTemplatesView.vue'),
        },
        {
          path: 'templates/new',
          name: 'admin-template-new',
          component: () => import('@/views/admin/AdminTemplateEditView.vue'),
        },
        {
          path: 'templates/:id',
          name: 'admin-template-edit',
          component: () => import('@/views/admin/AdminTemplateEditView.vue'),
        },
        {
          path: 'furnish',
          name: 'admin-furnish',
          component: () => import('@/views/admin/AdminFurnishView.vue'),
        },
        {
          path: 'quote-params',
          name: 'admin-quote-params',
          component: () => import('@/views/admin/AdminQuoteParamsView.vue'),
        },
        {
          path: 'dicts',
          name: 'admin-dicts',
          component: () => import('@/views/admin/AdminDictsView.vue'),
        },
        {
          path: 'store',
          name: 'admin-store',
          component: () => import('@/views/admin/AdminStoreView.vue'),
        },
        {
          path: 'accounts',
          name: 'admin-accounts',
          component: () => import('@/views/admin/AdminAccountsView.vue'),
          meta: { title: '账号与权限' },
        },
        {
          path: 'logs',
          name: 'admin-logs',
          component: () => import('@/views/admin/AdminLogsView.vue'),
          meta: { title: '操作日志' },
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
