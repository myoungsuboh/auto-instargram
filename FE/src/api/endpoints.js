import client, { newIdempotencyKey } from './client'

/**
 * 백엔드 엔드포인트 호출 함수 모음.
 *
 * 계약 근거: _workspace/api_contracts.md — 경로·필드명·상태코드를 그 문서와 문자 단위로 맞춘다.
 * 화면 컴포넌트는 axios 를 직접 쓰지 않고 여기만 호출한다 (경로가 흩어지면 계약이 어긋난다).
 */

// ══ 인증 (명세 외 추가 — ADR-0005) ═══════════════════════════════════

export const auth = {
  /** POST /api/v1/auth/login — 성공 시 httpOnly 쿠키가 심긴다 (응답 바디에 토큰 없음). */
  async login(username, password) {
    const { data } = await client.post('/api/v1/auth/login', { username, password })
    return data // { username, role, expiresIn }
  },

  /** GET /api/v1/auth/me — 화면은 httpOnly 쿠키를 읽을 수 없으므로 세션 확인에 필요하다. */
  async me() {
    const { data } = await client.get('/api/v1/auth/me')
    return data
  },

  /** POST /api/v1/auth/logout — 서버의 갱신 토큰을 폐기하고 쿠키를 만료시킨다. */
  async logout() {
    await client.post('/api/v1/auth/logout')
  },
}

// ══ API-01 / API-02 예약 큐 ═══════════════════════════════════════════

export const queues = {
  /**
   * POST /api/v1/queues → 201 { queueId, status, createdAt }
   *
   * 멱등성 키를 반드시 싣는다 (규칙 1) — 사용자가 등록 버튼을 두 번 눌러도
   * 예약이 하나만 생긴다. 서버가 같은 키의 재요청에 첫 응답을 그대로 돌려준다.
   */
  async create({ mediaPath, caption, scheduledAt }, idempotencyKey) {
    const { data } = await client.post(
      '/api/v1/queues',
      { mediaPath, caption, scheduledAt },
      { headers: { 'Idempotency-Key': idempotencyKey ?? newIdempotencyKey() } },
    )
    return data
  },

  /**
   * GET /api/v1/queues → 200 { items, total }
   *
   * POL-03: 0건이어도 200 + 빈 배열이다. 화면은 이것을 오류로 다루지 않는다.
   */
  async list({ page = 0, limit = 6 } = {}) {
    const { data } = await client.get('/api/v1/queues', { params: { page, limit } })
    return data
  },
}

// ══ API-03 게시 이력 ═════════════════════════════════════════════════

export const history = {
  /**
   * GET /api/v1/history → 200 { history: [...] }
   *
   * startDate·endDate 는 선택 항목이며 서로 독립이다.
   * 값이 비어 있으면 파라미터 자체를 보내지 않는다 — 빈 문자열을 보내면 400 이 된다.
   */
  async list({ startDate, endDate } = {}) {
    const params = {}
    if (startDate) params.startDate = startDate
    if (endDate) params.endDate = endDate
    const { data } = await client.get('/api/v1/history', { params })
    return data
  },
}

// ══ API-04 릴스 업로드 ════════════════════════════════════════════════

export const reels = {
  /**
   * POST /api/v1/reels/upload → 201 { containerId, status }
   *
   * containerId 는 QueueItem 의 id 이므로(ADR-0012) 예약 목록에서 진행 상태를 추적할 수 있다.
   * 게시는 되돌릴 수 없으므로 멱등성 키가 특히 중요하다.
   */
  async upload({ binaryPath, caption }, idempotencyKey) {
    const { data } = await client.post(
      '/api/v1/reels/upload',
      { binaryPath, caption },
      { headers: { 'Idempotency-Key': idempotencyKey ?? newIdempotencyKey() } },
    )
    return data
  },
}

// ══ API-05 인스타그램 토큰 갱신 ═══════════════════════════════════════

export const tokens = {
  /**
   * POST /api/v1/tokens/refresh → 200 { accessToken, expiresIn }
   *
   * ⚠️ system_admin 권한만 호출할 수 있다 (1_spack.md required_roles).
   * 운영자가 호출하면 403 이 온다 — 화면에서 미리 막되, 서버 검증이 최종 방어선이다
   * (SKL-AUTHN-AUTHZ 규칙 3: 클라이언트 조건으로만 숨기는 데 의존하지 않는다).
   */
  async refresh(shortLivedToken) {
    const { data } = await client.post('/api/v1/tokens/refresh', { shortLivedToken })
    return data
  },
}
