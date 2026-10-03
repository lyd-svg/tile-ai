<script setup lang="ts">
import { ConfigProvider } from 'ant-design-vue'
import { computed, watchEffect } from 'vue'
import { useRoute } from 'vue-router'

import { themeFor, type AppThemeName } from '@/styles/antdTheme'
import AppConfirmHost from '@/components/base/AppConfirmHost.vue'

/**
 * 根外壳 —— **全站主题的唯一接线点**。
 *
 * ## 为什么在这里,而不是在两个 layout 里
 *
 * 有**两个页面不挂在任何 layout 下**(见 `router/index.ts`):
 * `/share/:token`(公开分享页)与 `/admin/login`(后台登录)。
 * 若主题在 layout 里设,这两页会**沿用上一个页面的主题** ——
 * 从后台点进分享页,分享页就成了后台样式。**这是会静默发生的错误**。
 *
 * 放在 App.vue 则一处覆盖全部路由。
 *
 * ## 判定口径
 *
 * 用**路径前缀**判 `/admin`,与登录守卫(`router.beforeEach`)同一口径,
 * 不另立标准。
 *
 * ## 两个动作,同源
 *
 * 1. 给根节点( `<html>` )设 `data-theme` —— 驱动 `tokens.css` 的 CSS 变量;
 * 2. 给 antd 的 `ConfigProvider` 传主题 —— 驱动 antd 组件。
 *
 * **两者必须同源**:各写各的会出现"页面底色对了、antd 按钮还是默认蓝"
 * 的半身不遂主题(`tile-ai-style-foundation.md` §3.2)。
 *
 * ## 为什么这里**只有** RouterView(2026-10-03 改)
 *
 * 原先这里还有一条全站导航(「瓷砖选购 AI / C 端 / 后台管理」)和一层
 * `<main>` 容器。但:
 *   - C 端**已经有** `SiteLayout` 的顶栏,后台**已经有**侧栏 + 顶栏 ——
 *     于是 C 端页面上**两条导航栏叠着**,两条都叫 `.nav`、都写着"瓷砖选购 AI";
 *   - 那层 `<main>` 的 padding 又与 layout 自己的容器**叠了两层内边距**。
 * ⇒ 全部删除,**两个 layout 各管各的外壳**。C 端的"后台管理"入口不丢 ——
 * `SiteLayout` 的页脚里有一个。
 * 见 `openapi-verification.md` §67.3。
 */
const route = useRoute()

const themeName = computed<AppThemeName>(() =>
  route.path.startsWith('/admin') ? 'admin' : 'site',
)
const theme = computed(() => themeFor(themeName.value))

watchEffect(() => {
  document.documentElement.dataset.theme = themeName.value
})

/**
 * ⚠️ **一处未决的架构岔路:静态 `Modal` 进不了应用上下文。**
 *
 * 起因:阶段 G2 要给 8 个页面把 `window.confirm` 换成 antd 的确认框。
 * 实测发现,antd 的**静态方法**(`Modal.confirm` / `message` / `notification`)
 * 是**脱离应用上下文**渲染的(自建根、挂到 body),于是有两个后果:
 *
 *   ① **主题可能拿不到** ⇒ 确认框的按钮会渲染成 **antd 默认蓝**,直接违反铁律 2;
 *   ② `<ConfigProvider>` 上的 `:auto-insert-space-in-button="false"` **管不到它**
 *      ⇒ 按钮文案是「确 定」(带空格),页面其它 antd 按钮是「确定」。
 *
 * 试过**全局配置**这条路(`ConfigProvider.config({ theme })`),**撞墙**:
 * 它的 `Theme` 类型与组件 prop 的**不是同一个**(`vue-tsc` 报「no properties in common」);
 * 而 ② 那个开关**在全局配置里根本没有**(`GlobalConfigProviderProps` 只有
 * `prefixCls` / `iconPrefixCls` / `getPopupContainer` / `theme`)。
 *
 * ⇒ **岔路已定(2026-10-03,用户选 A)**:自建**声明式**确认框 ——
 *   `components/base/AppConfirmHost.vue`(渲染 `<Modal>`)+
 *   `components/base/AppConfirm.ts`(纯状态与 Promise,不碰 antd),
 *   宿主挂在**本文件的 `<ConfigProvider>` 之内**,从而**进得去上下文**,
 *   主题与空格两个问题一起解决。见 `openapi-verification.md` §70.6。
 */
</script>

<template>
  <!-- `:auto-insert-space-in-button="false"`(2026-10-03 加):
       antd **默认会在"两个汉字"的按钮里自动插一个空格** —— 「确定」渲染成「确 定」。
       两个后果,第二个更坏:
         ① 跟本站其它按钮的文案不一致;
         ② **按文案找按钮的测试会静默失败**(实测踩到,见 `openapi-verification.md` §70)——
            不报错、不提示,只是 `querySelector` 找不到。
       ⇒ 关掉它,全站按钮文案统一。 -->
  <ConfigProvider :theme="theme" :auto-insert-space-in-button="false">
    <RouterView />

    <!-- 确认框宿主 —— 全项目**只挂这一处**,而且**必须在 `<ConfigProvider>` 之内**:
         它靠上下文拿主题令牌与"不插空格"这条设置。放到外面就又变成
         `Modal.confirm` 那种"脱上下文"的老问题(按钮会成默认蓝、文案成「确 定」)。
         见 `src/components/base/AppConfirm.ts` 的文件头与 `openapi-verification.md` §70.6。 -->
    <AppConfirmHost />
  </ConfigProvider>
</template>
