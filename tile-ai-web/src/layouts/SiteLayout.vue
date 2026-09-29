<script setup lang="ts">
// C 端(业主)外壳。主流程页面自 F-1.1 起逐个落地,见 tile-ai-tasks.md。
</script>

<template>
  <header class="nav">
    <strong>瓷砖选购 AI</strong>
    <nav>
      <RouterLink to="/">C 端</RouterLink>
      <RouterLink to="/admin">后台管理</RouterLink>
    </nav>
  </header>

  <main class="main">
    <RouterView />
  </main>
</template>

<style scoped>
.nav {
  display: flex;
  gap: 1.5rem;
  align-items: center;
  padding: 0.75rem 1.25rem;
  border-bottom: 1px solid #e5e7eb;
}

.nav nav {
  display: flex;
  gap: 1rem;
}

.main {
  padding: 1.25rem;
}
</style>
