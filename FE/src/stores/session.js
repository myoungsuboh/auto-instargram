import { computed, reactive, readonly } from 'vue'
import { auth } from '../api/endpoints'

/**
 * 로그인 세션 상태.
 *
 * Pinia 같은 상태 관리 라이브러리를 쓰지 않은 이유: 이 앱의 공유 상태는 "누가 로그인했나" 하나뿐이다.
 * 의존성을 하나 더 얹을 만한 복잡도가 아니다 (Vue 의 reactive 만으로 충분하다).
 *
 * ⚠️ 토큰을 저장하지 않는다. 토큰은 httpOnly 쿠키에만 있고 JavaScript 는 접근할 수 없다(ADR-0006).
 * 여기 담기는 것은 "표시용 정보"(아이디·권한)뿐이다.
 */
const state = reactive({
  username: null,
  role: null,
  /** 서버에 세션을 한 번이라도 확인했는지. 앱 시작 시 깜빡임을 막는 데 쓴다. */
  resolved: false,
})

export const session = readonly(state)

export const isLoggedIn = computed(() => state.username !== null)

/**
 * 1_spack.md 의 권한 표기를 그대로 쓴다 (서버가 `system_admin` 형태로 내보낸다).
 * API-05 는 관리자만 호출할 수 있다.
 */
export const isAdmin = computed(() => state.role === 'system_admin')

function apply(sessionData) {
  state.username = sessionData?.username ?? null
  state.role = sessionData?.role ?? null
  state.resolved = true
}

export async function login(username, password) {
  apply(await auth.login(username, password))
}

export async function logout() {
  try {
    await auth.logout()
  } finally {
    // 서버 호출이 실패해도 화면 상태는 반드시 비운다 —
    // 실패했다고 로그인 상태로 남겨 두면 사용자가 로그아웃했다고 믿는데 화면은 그대로다.
    apply(null)
  }
}

/**
 * 앱 시작 시 쿠키로 세션이 살아 있는지 확인한다.
 *
 * 화면은 httpOnly 쿠키를 읽을 수 없으므로, 새로고침 후 로그인 상태를 아는 유일한 방법이
 * 서버에 물어보는 것이다.
 */
export async function restore() {
  try {
    apply(await auth.me())
  } catch {
    // 401 은 "로그인 안 된 상태"라는 정상 응답이다 — 오류로 다루지 않는다.
    // (client.js 의 인터셉터가 갱신을 한 번 시도한 뒤 실패한 결과다)
    apply(null)
  }
}

/** 세션이 만료됐을 때 client.js 인터셉터가 호출한다. */
export function clear() {
  apply(null)
}
