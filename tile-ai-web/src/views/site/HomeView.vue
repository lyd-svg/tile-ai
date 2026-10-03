<script setup lang="ts">
/**
 * C 端首页 —— 三条选砖路径的入口(PRD §5.2)。
 *
 * 三条**都已开放**:自己挑砖 / 看成套搭配 / 问 AI 顾问。
 * (路径 C 曾因后端未实现而显示"开发中";T-1.6 与 F-1.6 于 2026-10-02 落地后放开。)
 */
</script>

<template>
  <section class="hero">
    <p class="hero__eyebrow">选砖 · 录户型 · 效果图 · 报价</p>
    <h1 class="hero__title">把瓷砖,铺进你家的样子</h1>
    <p class="hero__sub">
      选砖 → 录户型 → 生成效果图 → 按部位报价。买东西之前,先看见效果。
    </p>
  </section>

  <section class="paths">
    <RouterLink class="path" to="/products">
      <span class="path__badge">A</span>
      <h2 class="path__title">自己挑砖</h2>
      <p class="path__desc">按花色、材质、规格、风格、价格区间筛选产品库。</p>
      <span class="path__go">开始选砖 →</span>
    </RouterLink>

    <RouterLink class="path" to="/combos">
      <span class="path__badge">B</span>
      <h2 class="path__title">看成套搭配</h2>
      <p class="path__desc">按空间与风格查看人工维护的成套方案,含参考报价区间。</p>
      <span class="path__go">看看搭配 →</span>
    </RouterLink>

    <RouterLink class="path" to="/consult">
      <span class="path__badge">C</span>
      <h2 class="path__title">问 AI 顾问</h2>
      <p class="path__desc">用自然语言描述需求,让 AI 推荐瓷砖与搭配方案。</p>
      <span class="path__go">去问一问 →</span>
    </RouterLink>
  </section>

  <section class="next">
    <h2 class="next__title">接下来会发生什么</h2>
    <p class="next__text">
      选好砖之后进入<b>户型录入</b>与<b>效果图生成</b> —— 这两步都已开放,
      选好砖就能走完「录户型 → 生成效果图 → 按部位报价」。
    </p>
  </section>
</template>

<style scoped>
/* 样式一律走设计令牌(`src/styles/tokens.css`)——本文件不出现任何十六进制。
   令牌值见 `tile-ai-design.md` §3。 */

/* ── 主视觉 ─────────────────────────────────────────────────────────────── */
.hero {
  padding: var(--space-8) 0 var(--space-6);
  border-bottom: 1px solid var(--color-border);
}

.hero__eyebrow {
  margin: 0 0 var(--space-3);
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  letter-spacing: 0.08em;
  color: var(--color-accent);
}

.hero__title {
  margin: 0 0 var(--space-4);
  font-size: var(--text-3xl);
  line-height: var(--leading-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
  color: var(--color-text);
}

.hero__sub {
  margin: 0;
  max-width: 42rem;
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  color: var(--color-text-secondary);
}

/* ── 三条路径 ───────────────────────────────────────────────────────────── */
.paths {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(17rem, 1fr));
  gap: var(--space-4);
  margin: var(--space-6) 0;
}

.path {
  display: flex;
  flex-direction: column;
  padding: var(--space-5);
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
  background: var(--color-surface);
  box-shadow: var(--shadow-sm);
  text-decoration: none;
  color: inherit;
  transition:
    border-color var(--dur-base) var(--ease-standard),
    box-shadow var(--dur-base) var(--ease-standard),
    transform var(--dur-base) var(--ease-standard);
}

.path:hover {
  border-color: var(--color-primary);
  box-shadow: var(--shadow-md);
  transform: translateY(-2px);
}

.path:focus-visible {
  outline: 2px solid var(--color-focus);
  outline-offset: 2px;
}

.path__badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 1.75rem;
  height: 1.75rem;
  margin-bottom: var(--space-4);
  border-radius: var(--radius-sm);
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: var(--text-sm);
  line-height: 1;
  font-weight: 700;
}

.path__title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-xl);
  line-height: var(--leading-xl);
  font-weight: 600;
  color: var(--color-text);
}

.path__desc {
  margin: 0 0 var(--space-4);
  flex: 1;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

.path__go {
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  font-weight: 600;
  color: var(--color-primary);
}

/* ── 说明 ───────────────────────────────────────────────────────────────── */
.next {
  padding: var(--space-5);
  border-radius: var(--radius-md);
  background: var(--color-surface-sunken);
}

.next__title {
  margin: 0 0 var(--space-2);
  font-size: var(--text-lg);
  line-height: var(--leading-lg);
  font-weight: 600;
  color: var(--color-text);
}

.next__text {
  margin: 0;
  font-size: var(--text-sm);
  line-height: var(--leading-sm);
  color: var(--color-text-secondary);
}

.next__text b {
  color: var(--color-text);
}
</style>
