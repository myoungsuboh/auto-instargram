<script setup>
import AppNav from './components/AppNav.vue'
</script>

<template>
  <div class="shell">
    <AppNav />
    <main class="page">
      <!-- 라우트가 바뀔 때마다 등장 애니메이션을 1회만 실행한다
           (디자인 스킬 §5 Motion: opacity 0→1 + translateY 24px, 500ms).
           :key 로 라우트마다 새로 마운트되게 해 애니메이션이 다시 걸리도록 한다. -->
      <RouterView v-slot="{ Component, route }">
        <component :is="Component" :key="route.path" class="enter" />
      </RouterView>
    </main>
  </div>
</template>
