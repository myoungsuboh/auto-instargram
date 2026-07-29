# API 계약 (api_contracts.md)

> Task 2.1 산출물. **각 Phase 시작 시 이 파일을 다시 읽는다** (00-ORCHESTRATOR 지시 — 기억에 의존 금지).
> Phase 4 의 프론트엔드 API 클라이언트는 이 문서를 근거로 작성한다.

- 기준 주소: `http://localhost:8080` (3_architecture.md SVC-02 Port 8080)
- 모든 요청·응답은 `application/json; charset=UTF-8`
- 모든 시각은 UTC (ISO-8601, 예: `2026-06-01T10:00:00Z`)

---

## 1. 인증 방식

`3_architecture.md` Connection Map 은 FE → BE 인증을 `bearer` 로 규정하고,
`skills/security/JWT-authn-authz.md` 규칙 1 은 JWT 를 httpOnly 쿠키에 저장하라고 요구한다.
**둘 다 지키기 위해 서버는 두 경로를 모두 받는다:**

| 순서 | 위치 | 용도 |
|---|---|---|
| 1 (우선) | 쿠키 `ai_access` | 브라우저(화면). JavaScript 가 토큰을 만질 수 없어 XSS 로 탈취되지 않음 |
| 2 (대체) | 헤더 `Authorization: Bearer <token>` | curl 등 프로그램 클라이언트 |

**쿠키 속성** — `httpOnly` 항상 true / `SameSite=Strict` / `Secure`는 `JWT_COOKIE_SECURE`(로컬 false, 운영 true)

| 쿠키 | 수명 | path | 내용 |
|---|---|---|---|
| `ai_access` | 15분 | `/` | 액세스 토큰(JWT) |
| `ai_refresh` | 7일 | `/api/v1/auth` | 갱신 토큰(불투명 난수) |

### 권한 (1_spack.md `required_roles`)

| 권한 | 명세 표기 | JWT claim | Spring 권한 |
|---|---|---|---|
| 관리자 | `system_admin` | `SYSTEM_ADMIN` | `ROLE_SYSTEM_ADMIN` |
| 운영자 | `system_operator` | `SYSTEM_OPERATOR` | `ROLE_SYSTEM_OPERATOR` |

---

## 2. 공통 에러 응답

모든 에러는 아래 한 가지 형태다. 스택트레이스·SQL·내부 클래스명은 절대 포함하지 않는다(OWASP #4).

```json
{
  "code": "AUTH_REQUIRED",
  "message": "인증이 필요합니다",
  "fields": null,
  "at": "2026-07-29T02:47:20.749Z"
}
```

`fields` 는 입력 검증 실패일 때만 채워진다 (값 자체는 담지 않음 — 비밀번호·토큰 반향 방지):

```json
{"code":"VALIDATION_ERROR","message":"잘못된 입력값입니다",
 "fields":[{"field":"username","reason":"아이디는 3~100자여야 합니다"}],
 "at":"..."}
```

| code | HTTP | 의미 | 출처 |
|---|---|---|---|
| `AUTH_REQUIRED` | 401 | JWT 누락 또는 만료 | 1_spack.md 전 API |
| `FORBIDDEN` | 403 | 권한 부족 | 1_spack.md API-01 등 |
| `VALIDATION_ERROR` | 422 | 입력값 검증 실패 | 1_spack.md API-01/04 |
| `INVALID_TOKEN` | 422 | 만료·잘못된 토큰 | 1_spack.md API-05 |
| `BAD_REQUEST` | 400 | JSON 형식 오류 | 1_spack.md 에러 가이드 |
| `NOT_FOUND` | 404 | 대상 없음 | 1_spack.md 에러 가이드 |
| `UNPROCESSABLE` | 422 | 비즈니스 규칙 위반 | 1_spack.md 에러 가이드 |
| `INVALID_CREDENTIALS` | 401 | 아이디/비밀번호 불일치 | 명세 외(로그인 추가) |
| `TOO_MANY_ATTEMPTS` | 429 | 로그인 시도 한도 초과 | SKL-AUTHN-AUTHZ 규칙 5 |
| `UPSTREAM_UNAVAILABLE` | 502 | 인스타그램 Graph API 실패 | SKL-ERROR-HANDLING 규칙 2 |
| `INTERNAL_ERROR` | 500 | 예상치 못한 서버 오류 | — |

---

## 3. 로그인 API (⚠️ 1_spack.md 에 없는 추가 API)

> 추가 이유: 명세의 API 5개가 모두 `auth.required=true` 인데 로그인 창구가 없어 화면이 인증을 얻을 수 없었다.
> 사용자 확정 결정 — 근거: `docs/decisions/2026-07-29-dashboard-login-added.md`

### `POST /api/v1/auth/login` — 인증 불필요

요청:
| 이름 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `username` | string | O | 3~100자, `^[A-Za-z0-9._-]+$` |
| `password` | string | O | 8~200자 |

```json
{"username": "admin", "password": "Admin!2026Local"}
```

응답 **200 OK** — `Set-Cookie: ai_access`, `Set-Cookie: ai_refresh`
| 이름 | 타입 | 설명 |
|---|---|---|
| `username` | string | 로그인한 아이디 |
| `role` | string | `system_admin` \| `system_operator` |
| `expiresIn` | integer | 액세스 토큰 남은 초 (900) |

```json
{"username": "admin", "role": "system_admin", "expiresIn": 900}
```

> **토큰은 응답 바디에 없다** — 쿠키로만 전달된다 (SKL-AUTHN-AUTHZ 규칙 1).

에러: `422 VALIDATION_ERROR` / `401 INVALID_CREDENTIALS` / `429 TOO_MANY_ATTEMPTS`

### `POST /api/v1/auth/refresh` — 인증 불필요 (갱신 쿠키가 신원 증명)

요청 본문 없음. `ai_refresh` 쿠키 필요.
응답 **200 OK** — login 과 동일한 바디 + 새 쿠키 2개.
> 회전(rotation)한다: 쓰인 갱신 토큰은 즉시 폐기된다. 폐기된 토큰을 재사용하면 해당 계정의 모든 세션이 끊긴다.

에러: `401 AUTH_REQUIRED`

### `POST /api/v1/auth/logout` — 인증 필요

응답 **204 No Content** — 쿠키 2개를 만료시킨다. 서버의 갱신 토큰도 전부 폐기.

### `GET /api/v1/auth/me` — 인증 필요

응답 **200 OK** — login 과 동일한 바디. 화면이 로그인 상태를 확인하는 용도
(httpOnly 쿠키는 JavaScript 가 읽을 수 없으므로 필요하다).

에러: `401 AUTH_REQUIRED`

---

## 4. API-05 `POST /api/v1/tokens/refresh` — 인증 필요, **`system_admin` 만**

1_spack.md Story-06.2. 단기 인스타그램 토큰을 장기(60일) 토큰으로 교환해 암호화 저장한다.

요청:
| 이름 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `shortLivedToken` | string | O | 최대 1000자 |

```json
{"shortLivedToken": "EAAG..."}
```

응답 **200 OK**:
| 이름 | 타입 | 필수 | 제약 | 설명 |
|---|---|---|---|---|
| `accessToken` | string | O | | 갱신된 장기 토큰 |
| `expiresIn` | integer | O | >0 | 만료까지 남은 초 |

```json
{"accessToken": "EAAG...", "expiresIn": 5184000}
```

에러: `401 AUTH_REQUIRED` / `403 FORBIDDEN`(운영자) / `422 INVALID_TOKEN` /
`422 UNPROCESSABLE`(`INSTAGRAM_CLIENT_SECRET` 미설정) / `502 UPSTREAM_UNAVAILABLE`

부수 효과: 자격 증명이 `security_credentials` 에 **암호화**되어 저장되고 도메인 이벤트 `TokenRefreshed`(EVT-03) 가 발행된다.

> ⚠️ 이 응답에만 토큰 전문이 담긴다 — 명세가 그렇게 규정했다. 관리자만 호출할 수 있고, 로그에는 남지 않는다(POL-05).

---

## 5. Phase 3 에서 구현할 API (1_spack.md 기준, 계약 미리 확정)

Phase 4 의 화면이 이 계약을 전제로 작성된다.

### `POST /api/v1/queues` — `system_admin`, `system_operator`
요청: `mediaPath`(O, ≤255) / `caption`(-, ≤2200) / `scheduledAt`(O, datetime)
응답 **201 Created**: `queueId`(uuid) / `status`(`PENDING|SUCCESS|FAILED`) / `createdAt`(datetime)
에러: 401 / 403 / 422

### `GET /api/v1/queues` — `system_operator`, `system_admin`
쿼리: `page`(-, ≥0) / `limit`(-, >0)
응답 **200 OK**: `items`(array) / `total`(integer ≥0)
에러: 401
> POL-03: 0건이어도 200 + 빈 배열.

### `GET /api/v1/history` — `system_operator`, `system_admin`
쿼리: `startDate`(-, date) / `endDate`(-, date)
응답 **200 OK**: `history`(array)
에러: 401
> POL-03: 0건이어도 200 + 빈 배열.

### `POST /api/v1/reels/upload` — `system_operator`, `system_admin`
요청: `binaryPath`(O) / `caption`(O, ≤2200)
응답 **201 Created**: `containerId`(uuid) / `status`(string)
에러: 401 / 422

### 상태 값 변환 (ADR-0003 — 반드시 지킬 것)

DB·도메인은 넓은 상태를 저장하고, **API 응답에서는 아래로 변환**한다:

| 대상 | 내부(DB) | API 응답 |
|---|---|---|
| QueueItem | `PENDING` / `RUNNING` / `COMPLETED` / `FAILED` | `PENDING` / `PENDING` / `SUCCESS` / `FAILED` |
| HistoryRecord | `SUCCESS` / `FAILED` / `RETRY` | `SUCCESS` / `FAILED` / `FAILED` |

### 식별자 이름 변환 (ADR-0004 — 반드시 지킬 것)

| DB 컬럼 | API 응답 필드 |
|---|---|
| `queue_items.id` | `queueId` |
| `history_records.id` | `recordId` |
| `security_credentials.id` | `credentialId` |
| `history_records.recorded_at` | `timestamp` |

> 엔티티를 컨트롤러에서 그대로 반환하면 두 변환이 모두 깨진다 — **반드시 DTO 를 거친다.**

---

## 6. 헬스체크

`GET /actuator/health` — 인증 불필요 (3_architecture.md SVC-02 Health check)
응답 **200 OK**: `{"status":"UP"}`
> 접속 정보·내부 구성이 새지 않도록 상세는 감춘다 (`show-details=never`).
