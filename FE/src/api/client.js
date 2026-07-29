import axios from 'axios'

/**
 * 백엔드 API 클라이언트.
 *
 * 계약 근거: _workspace/api_contracts.md
 * 연결 근거: 3_architecture.md §4 Connection Map
 *   Mobile Web Frontend → Instagram Automation Backend (HTTPS/REST, auth: bearer)
 *
 * 인증 방식 (ADR-0006):
 *   토큰은 httpOnly 쿠키로만 오간다. 이 코드는 토큰을 읽거나 저장하지 않는다 —
 *   JavaScript 가 토큰을 만질 수 없으므로 XSS 로 탈취되지 않는다.
 *   그래서 `withCredentials: true` 가 필수다. 이걸 빼면 쿠키가 전송되지 않아 전부 401 이 된다.
 */
const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080'

const client = axios.create({
  baseURL: API_BASE_URL,
  withCredentials: true,
  headers: { 'Content-Type': 'application/json' },
  // POL-04 는 서버 응답 3초 이내를 규정한다. 그보다 넉넉히 두되 무한 대기는 막는다.
  timeout: 10_000,
})

/**
 * 세션이 끊겼을 때 호출되는 콜백. main.js 에서 라우터로 연결한다.
 *
 * 여기서 직접 라우터를 import 하지 않는 이유: 라우터가 이 모듈을 (간접적으로) 의존하므로
 * 순환 import 가 된다.
 */
let onSessionExpired = () => {}

export function setSessionExpiredHandler(handler) {
  onSessionExpired = handler
}

/** 갱신 요청이 동시에 여러 개 나가지 않게 하는 잠금. */
let refreshInFlight = null

function isRefreshCall(config) {
  return config?.url?.includes('/auth/refresh')
}

function isLoginCall(config) {
  return config?.url?.includes('/auth/login')
}

/**
 * 401 을 만나면 액세스 토큰을 한 번 갱신하고 원래 요청을 다시 보낸다.
 *
 * SKL-AUTHN-AUTHZ 규칙 2("Access Token 15분 + Refresh Token 으로 자동 갱신")의 화면 측 구현이다.
 * 15분마다 사용자가 다시 로그인해야 한다면 규칙을 지킨 의미가 없다.
 *
 * 동시에 여러 요청이 401 을 받으면 갱신도 여러 번 나가는데, 서버는 갱신 토큰을 회전시키므로
 * (ADR-0007) 두 번째 갱신은 이미 폐기된 토큰을 쓰게 되어 <b>전체 세션이 끊긴다</b>.
 * 그래서 갱신은 하나만 진행하고 나머지는 그 결과를 기다린다.
 */
client.interceptors.response.use(
  (response) => response,
  async (error) => {
    const { config, response } = error

    if (response?.status !== 401 || !config) {
      return Promise.reject(error)
    }

    // 로그인·갱신 자체가 401 이면 재시도할 것이 없다 (무한 루프 방지)
    if (isRefreshCall(config) || isLoginCall(config) || config.__retried) {
      if (!isLoginCall(config)) {
        onSessionExpired()
      }
      return Promise.reject(error)
    }

    config.__retried = true

    try {
      refreshInFlight = refreshInFlight ?? client.post('/api/v1/auth/refresh')
      await refreshInFlight
      return client(config)
    } catch (refreshError) {
      onSessionExpired()
      return Promise.reject(refreshError)
    } finally {
      refreshInFlight = null
    }
  },
)

/**
 * 서버 에러 응답을 화면에서 쓰기 쉬운 형태로 바꾼다.
 *
 * 서버는 항상 `{code, message, fields, at}` 형식으로 응답한다(api_contracts.md §2).
 * 화면은 서버가 준 `message` 를 그대로 보여준다 — 문구를 화면에서 다시 만들면
 * 서버와 어긋나고, 명세가 규정한 메시지("인증이 필요합니다" 등)를 잃는다.
 */
export function toDisplayError(error) {
  const body = error?.response?.data

  if (body?.message) {
    return {
      code: body.code ?? 'UNKNOWN',
      message: body.message,
      // 필드별 사유가 있으면 { 필드명: 사유 } 로 변환해 폼에 붙인다
      fields: Object.fromEntries(
        (body.fields ?? []).map((violation) => [violation.field, violation.reason]),
      ),
    }
  }

  // 서버에 닿지 못한 경우 — 네트워크·CORS·서버 미기동을 구분해 알려준다.
  // "알 수 없는 오류"만 보여주면 사용자가 무엇을 해야 할지 알 수 없다.
  if (error?.code === 'ECONNABORTED') {
    return {
      code: 'TIMEOUT',
      message: '서버 응답이 너무 늦습니다. 잠시 후 다시 시도해 주세요.',
      fields: {},
    }
  }

  // 주소를 실제 설정값으로 알려준다 — 하드코딩하면 다른 주소를 쓸 때 엉뚱한 곳을 찾게 된다
  return {
    code: 'NETWORK_ERROR',
    message: `서버에 연결할 수 없습니다. 백엔드가 실행 중인지 확인해 주세요 (${API_BASE_URL}).`,
    fields: {},
  }
}

/**
 * 멱등성 키를 만든다 (skills/backEnd/idempotency-idempotency.md 규칙 3: 클라이언트 생성 UUID v4).
 *
 * 부작용이 있는 POST 는 이 헤더를 반드시 실어야 한다 — 사용자가 버튼을 두 번 누르거나
 * 네트워크가 불안해 재시도될 때 예약이 두 번 등록되는 것을 서버가 막아 준다.
 * 서버는 UUID v4 가 아닌 키를 422 로 거부한다.
 */
export function newIdempotencyKey() {
  if (globalThis.crypto?.randomUUID) {
    return globalThis.crypto.randomUUID()
  }
  // 보안 컨텍스트가 아니면 randomUUID 가 없을 수 있다(구형 브라우저·http 원격 접속).
  // 이 값은 비밀이 아니라 중복 판별용이므로 대체 구현으로 충분하다.
  const bytes = new Uint8Array(16)
  globalThis.crypto.getRandomValues(bytes)
  bytes[6] = (bytes[6] & 0x0f) | 0x40 // version 4
  bytes[8] = (bytes[8] & 0x3f) | 0x80 // variant 10
  const hex = [...bytes].map((b) => b.toString(16).padStart(2, '0')).join('')
  return `${hex.slice(0, 8)}-${hex.slice(8, 12)}-${hex.slice(12, 16)}-${hex.slice(16, 20)}-${hex.slice(20)}`
}

export default client
