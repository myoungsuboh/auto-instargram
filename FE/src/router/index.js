import { createRouter, createWebHistory } from 'vue-router'
import { isLoggedIn, restore, session } from '../stores/session'

/**
 * 라우트 정의.
 *
 * 경로는 1_spack.md §5 Screens 가 규정한 값을 그대로 쓴다 — 임의로 바꾸면 명세 위반이다:
 *   SCREEN-01 /dashboard/upload   자동 업로드 대시보드
 *   SCREEN-02 /dashboard/posts    자동 게시 관리 대시보드
 *   SCREEN-03 /dashboard/history  CLI 인터페이스 및 로그 대시보드
 *   SCREEN-04 /dashboard/reels    릴스 업로드 제어 화면
 *
 * /login 은 명세 외 추가다 (ADR-0005 — 로그인 기능 추가에 따름).
 */
const routes = [
  {
    path: '/',
    redirect: '/dashboard/upload',
  },
  {
    path: '/login',
    name: 'login',
    component: () => import('../views/LoginView.vue'),
    meta: { public: true, title: '로그인' },
  },
  {
    path: '/dashboard/upload',
    name: 'upload',
    component: () => import('../views/UploadDashboardView.vue'),
    meta: { title: '자동 업로드 대시보드', screen: 'SCREEN-01' },
  },
  {
    path: '/dashboard/posts',
    name: 'posts',
    component: () => import('../views/PostsDashboardView.vue'),
    meta: { title: '자동 게시 관리', screen: 'SCREEN-02' },
  },
  {
    path: '/dashboard/history',
    name: 'history',
    component: () => import('../views/HistoryDashboardView.vue'),
    meta: { title: '게시 이력', screen: 'SCREEN-03' },
  },
  {
    path: '/dashboard/reels',
    name: 'reels',
    component: () => import('../views/ReelsDashboardView.vue'),
    meta: { title: '릴스 업로드 제어', screen: 'SCREEN-04' },
  },
  {
    // 잘못된 주소로 들어와도 빈 화면을 보여주지 않는다
    path: '/:pathMatch(.*)*',
    redirect: '/dashboard/upload',
  },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
  scrollBehavior: () => ({ top: 0 }),
})

/**
 * 로그인하지 않았으면 로그인 화면으로 보낸다.
 *
 * ⚠️ 이것은 <b>편의 기능일 뿐 보안 장치가 아니다.</b>
 * SKL-AUTHN-AUTHZ 규칙 3: "권한 검증은 반드시 서버 측에서 수행하고,
 * 클라이언트 조건으로만 UI 를 숨기는 방식에 의존하지 않는다."
 * 진짜 방어선은 서버의 SecurityConfig 이고, 이 가드는 사용자가 빈 화면을 보지 않게 하는 것이다.
 */
router.beforeEach(async (to) => {
  // 새로고침 직후에는 아직 세션을 모른다 — 서버에 한 번 물어본다
  if (!session.resolved) {
    await restore()
  }

  if (to.meta.public) {
    // 이미 로그인한 사용자가 로그인 화면으로 가면 대시보드로 돌린다
    return isLoggedIn.value ? { name: 'upload' } : true
  }

  if (!isLoggedIn.value) {
    // 로그인 후 원래 가려던 곳으로 돌아갈 수 있게 기억한다
    return { name: 'login', query: { redirect: to.fullPath } }
  }

  return true
})

router.afterEach((to) => {
  const title = to.meta.title
  document.title = title ? `${title} · auto-instargram` : 'auto-instargram'
})

export default router
