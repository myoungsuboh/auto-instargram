<script setup>
import AppNav from './components/AppNav.vue'
</script>

<template>
  <div class="shell">
    <AppNav />
    <main class="page">
      <!--
        페이지 전환은 디자인 스킬 §5 Motion 의 "페이지 전환: 300ms 페이드" 를 따른다.

        이전 구현은 스크롤 등장 규칙("opacity 0→1 + translateY 24px, 500ms, 1회만")을
        모든 라우트 전환에 걸었는데, 그 규칙은 <b>콘텐츠가 스크롤로 나타날 때 1회</b>를 위한 것이다.
        매 전환마다 24px 밀려 올라오게 만들어 느리고 덜컹거렸다
        (측정값: /dashboard/posts 전환 시 레이아웃 이동 0.4188 — "나쁨" 기준 0.25 초과).

        opacity 만 바꾸는 이유: transform·높이 변화는 레이아웃을 다시 계산하게 만들지만
        opacity 는 합성(composite)만 하므로 레이아웃 이동을 만들지 않는다.

        mode="out-in": 두 화면이 동시에 DOM 에 있으면 높이가 겹쳐 튀므로 순차 전환한다.
        나가기 120ms + 들어오기 180ms = 스킬이 정한 300ms.
      -->
      <RouterView v-slot="{ Component }">
        <Transition name="page" mode="out-in">
          <component :is="Component" />
        </Transition>
      </RouterView>
    </main>
  </div>
</template>
