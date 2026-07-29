<script setup>
import { useRouter } from 'vue-router'
import { isLoggedIn, logout, session } from '../stores/session'

/**
 * 상단 내비게이션.
 *
 * 디자인 스킬: "내비게이션은 13px, 최소 항목" — 장식 없이 화면 이동만 담당한다.
 * 현재 위치를 색이 아니라 밑줄로도 표시한다(색만으로 정보 전달 금지 — 접근성).
 *
 * 링크는 1_spack.md §5 Screens 의 4개 경로다.
 */
const router = useRouter()

const links = [
  { to: '/dashboard/upload', label: '예약 등록' },
  { to: '/dashboard/posts', label: '게시 관리' },
  { to: '/dashboard/history', label: '이력' },
  { to: '/dashboard/reels', label: '릴스' },
]

/** 권한 표기는 서버가 준 명세 값(system_admin/system_operator)을 한국어로 바꿔 보여준다. */
const ROLE_LABELS = {
  system_admin: '관리자',
  system_operator: '운영자',
}

async function handleLogout() {
  await logout()
  router.push({ name: 'login' })
}
</script>

<template>
  <header class="nav">
    <div class="container nav-inner">
      <RouterLink to="/dashboard/upload" class="nav-brand">
        auto&#8203;-instargram
      </RouterLink>

      <nav v-if="isLoggedIn" aria-label="주요 메뉴">
        <ul class="nav-links">
          <li v-for="link in links" :key="link.to">
            <RouterLink
              :to="link.to"
              class="nav-link"
              active-class="is-active"
            >
              {{ link.label }}
            </RouterLink>
          </li>
        </ul>
      </nav>

      <div v-if="isLoggedIn" class="nav-session">
        <span class="meta">
          {{ session.username }}
          <template v-if="session.role">
            · {{ ROLE_LABELS[session.role] ?? session.role }}
          </template>
        </span>
        <button type="button" class="button-text" @click="handleLogout">
          로그아웃
        </button>
      </div>
    </div>
  </header>
</template>
