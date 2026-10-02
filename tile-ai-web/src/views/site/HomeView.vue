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
    <h1>把瓷砖,铺进你家的样子</h1>
    <p class="sub">
      选砖 → 录户型 → 生成效果图 → 按部位报价。买东西之前,先看见效果。
    </p>
  </section>

  <section class="paths">
    <RouterLink class="card" to="/products">
      <h2>A · 自己挑砖</h2>
      <p>按花色、材质、规格、风格、价格区间筛选产品库。</p>
      <span class="tag tag--on">可用</span>
    </RouterLink>

    <RouterLink class="card" to="/combos">
      <h2>B · 看成套搭配</h2>
      <p>按空间与风格查看人工维护的成套方案,含参考报价区间。</p>
      <span class="tag tag--on">可用</span>
    </RouterLink>

    <RouterLink class="card" to="/consult">
      <h2>C · 问 AI 顾问</h2>
      <p>用自然语言描述需求,让 AI 推荐瓷砖与搭配方案。</p>
      <span class="tag tag--on">可用</span>
    </RouterLink>
  </section>

  <section class="next">
    <h2>接下来会发生什么</h2>
    <p class="hint">
      选好砖之后进入<b>户型录入</b>与<b>效果图生成</b> —— 这两步都已开放,
      选好砖就能走完「录户型 → 生成效果图 → 按部位报价」。
    </p>
  </section>
</template>

<style scoped>
.hero {
  padding: 2rem 0 1rem;
}

h1 {
  margin: 0 0 0.5rem;
  font-size: 1.75rem;
}

.sub {
  color: #6b7280;
  margin: 0;
}

.paths {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(16rem, 1fr));
  gap: 1rem;
  margin: 1.5rem 0;
}

.card {
  display: block;
  padding: 1.1rem;
  border: 1px solid #e5e7eb;
  border-radius: 0.5rem;
  text-decoration: none;
  color: inherit;
}

.card:hover {
  border-color: #93c5fd;
}

.card--off {
  opacity: 0.6;
}

h2 {
  margin: 0 0 0.4rem;
  font-size: 1.05rem;
}

.card p {
  margin: 0 0 0.75rem;
  color: #6b7280;
  font-size: 0.9rem;
}

.tag {
  display: inline-block;
  padding: 0.1rem 0.45rem;
  border-radius: 0.25rem;
  background: #f3f4f6;
  color: #6b7280;
  font-size: 0.8rem;
}

.tag--on {
  background: #ecfdf5;
  color: #047857;
}

.next h2 {
  font-size: 1rem;
  color: #374151;
}

.hint {
  color: #6b7280;
  font-size: 0.9rem;
}
</style>
