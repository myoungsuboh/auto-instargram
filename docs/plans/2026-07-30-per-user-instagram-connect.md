---
title: 사용자별 인스타그램 계정 연결 (비즈니스 로그인 / OAuth)
status: in-progress
created: 2026-07-30
updated: 2026-07-30
owner: dev
---

# 사용자별 인스타그램 계정 연결

## 진행 상황 (2026-07-30 기준)

사용자 확정: **"지금 방식으로 먼저 게시 성공시키기"** — 아래 하드 제약 2건(HTTPS 필수,
앱 검수 전에는 내 계정만)을 확인한 뒤, OAuth 본체는 뒤로 미루고 불편한 부분만 먼저 없앴다.

| 항목 | 상태 |
|---|---|
| 계정 번호 자동 조회 (사람이 `.env` 에 옮겨 적던 단계 제거) | ✅ **완료** — [ADR-0024](../decisions/2026-07-30-fetch-ig-account-on-token-exchange.md), V5 마이그레이션 |
| 연결된 계정 이름을 화면에 표시 | ✅ **완료** — API-05 응답에 `igUsername` 추가 |
| **선행 확인 — 지금 방식으로 토큰이 한 번이라도 발급되는가** | 🚧 **막힘** — "개발자 역할 권한이 부족합니다". Meta 쪽 미해결 문제일 수 있다(아래 위험 참조). **이것이 안 되면 이 계획은 시작 근거가 없다** |
| 0단계 HTTPS 방식 결정 | ⏸ 대기 — 이것이 풀리지 않으면 아래 전부 로컬 검증 불가 |
| 1~4단계 (스키마·OAuth·게시 경로·화면) | ⏸ 대기 |
| 5단계 `refresh_access_token` 자동 연장 | ⏸ 대기 — 실행 조건은 아래에 조사 완료 |
| 6단계 문서 정리 | ⏸ 대기 |

아래 계획의 나머지는 그대로 유효하다. 다시 시작할 때 **0단계부터** 보면 된다.

## 목표

지금은 인스타그램 토큰이 **시스템 전체에 하나**다. 관리자가 단기 토큰을 화면에 붙여넣어
장기 토큰으로 교환해 저장하고, 게시는 그 하나의 토큰으로 이뤄진다.
계정 번호(`INSTAGRAM_USER_ID`)도 사람이 손으로 알아내 `.env` 에 적는다.

바꾸려는 것: **로그인한 사용자가 각자 자기 인스타그램 계정을 화면에서 연결**하고,
계정 번호는 **서버가 자동으로 받아온다.** 사용자 요청(2026-07-30).

이는 [ADR-0022](../decisions/2026-07-29-instagram-login-api-path.md) 가
"OAuth 리다이렉트 구현이 필요 없다"며 기각한 방향을 **뒤집는 것**이므로 새 ADR 이 필요하다.

## 확인된 외부 사실 (2026-07-30, Meta 공식 문서 직접 확인)

출처: [Business Login for Instagram](https://developers.facebook.com/docs/instagram-platform/instagram-api-with-instagram-login/business-login)

흐름은 3단계다.

| 단계 | 요청 | 핵심 |
|---|---|---|
| 1 | `GET https://www.instagram.com/oauth/authorize` | 브라우저를 보낸다. `client_id`(**Instagram 앱 ID**), `redirect_uri`, `response_type=code`, `scope`, `state` |
| 2 | `POST https://api.instagram.com/oauth/access_token` | `code` → 단기 토큰. form: `client_id`, `client_secret`(**Instagram 앱 시크릿**), `grant_type=authorization_code`, `redirect_uri`, `code` |
| 3 | `GET https://graph.instagram.com/access_token` | 단기 → 장기(60일). `grant_type=ig_exchange_token` — **이미 구현돼 있음** |

추가로 알게 된 것 (지금 구현에 없는 기능):

- **`GET https://graph.instagram.com/refresh_access_token?grant_type=ig_refresh_token`** 으로
  장기 토큰을 **재인증 없이 60일 연장**할 수 있다. 조건: 기존 토큰이 24시간 이상 지났고,
  아직 만료되지 않았고, `instagram_business_basic` 권한이 있어야 한다.
  60일 안에 갱신하지 않으면 영구 만료된다.
  → 현재 안내문·ADR-0022 는 "60일마다 다시 발급"이라고만 적고 있다. 이것도 정정 대상.
- **2단계 응답이 `user_id` 를 함께 준다.** 응답 형태는
  `{"data":[{"access_token":..., "user_id":..., "permissions":...}]}` — `data` 배열로 감싸여 있다.
  → `INSTAGRAM_USER_ID` 를 손으로 알아낼 필요가 **완전히 없어진다.**
- 인증 코드는 **1시간 유효, 한 번만 사용 가능**.
- 사용자가 취소하면 `error=access_denied&error_reason=user_denied` 로 리디렉션된다 — 처리 필요.

## ⚠️ 하드 제약 2건 (구현 전에 결정이 필요한 부분)

### 제약 1 — 리디렉션 주소는 HTTPS 여야 한다

`redirect_uri` 는 앱 대시보드에 등록한 값과 **정확히 일치**해야 하고, Meta 는 **HTTPS** 를 요구한다.
공식 예시가 모두 `https://...` 이고, 개발자 포럼에도 "Instagram business login is not accepting
localhost" 류의 사례가 있다. 즉 지금의 `http://localhost:5173` 은 **등록 자체가 안 된다.**

이 프로젝트는 [ADR-0021](../decisions/2026-07-29-https-enforcement-defaults-off.md) 에서
`HTTPS_ENFORCED` 를 기본 off 로 두고 "로컬에서 켜지 마세요"라고 명시했다. 즉 이 기능은
프로젝트가 의도적으로 피해 온 로컬 HTTPS 를 요구한다.

선택지:

| 안 | 내용 | 비용 |
|---|---|---|
| A | Vite 에 자체 서명 인증서로 `https://localhost:5173` 제공 | 브라우저 경고를 매번 넘겨야 한다. 쿠키 `Secure` 설정도 함께 손봐야 한다 |
| B | ngrok 같은 터널로 임시 공개 HTTPS 주소를 받아 등록 | 주소가 바뀔 때마다 앱 대시보드에 재등록. 외부에 로컬 서버가 노출된다 |
| C | 코드는 다 만들고 로컬 검증은 생략, 운영(HTTPS) 에서만 동작 | **로컬에서 한 번도 확인 못 한 기능을 남기게 된다** — 이 프로젝트가 지켜 온 "실측으로 검증" 원칙에 어긋난다 |

### 제약 2 — 남의 계정까지 되게 하려면 Meta 앱 검수가 필요하다

문서의 "액세스 수준":

- **표준 액세스** — 앱이 **내가 소유·관리하고 앱 대시보드에 추가한** 인스타그램 계정만 서비스
- **Advanced Access** — 내가 소유하지 않은 계정에 서비스하려면 필요

즉 앱 검수를 받기 전까지 "사용자별 연결"은 **대시보드에 테스터로 등록한 계정에 대해서만** 동작한다.
지금처럼 혼자 쓰는 상황에서는 기능이 완성돼도 체감 차이가 거의 없고,
여러 사람이 각자 계정을 붙이는 그림은 **앱 검수 통과 후**에 성립한다.

⚠️ **Advanced Access 의 비용이 "앱 검수 제출" 하나가 아니다 (2026-07-30 실측).**
권한에 Advanced Access 를 요청하려 하면 다음 창이 뜬다:

> "To add a permission or feature to App Review, become a Tech Provider"
> "**This decision cannot be reversed after you've been identified as a Tech Provider.**"

요구 사항이 세 겹이다: **비즈니스 인증 + 액세스 인증 + 앱 검수.**
게다가 Tech Provider 전환은 **되돌릴 수 없고**, 이후 심사와 데이터 접근 요건이 더 엄격해진다.

따라서 이 계획을 끝까지 가려면 **되돌릴 수 없는 계정 상태 변경**을 감수해야 한다.
"코드만 만들면 되는 일"이 아니므로, 착수 결정 시 이 점을 반드시 사용자에게 확인해야 한다.

반대로 **내 계정에만 게시하는 지금 방식에는 이 과정이 전혀 필요 없다** —
Standard Access 는 모든 권한에 자동 승인되고, 역할이 부여된 사용자에게는 그것으로 충분하다
([액세스 레벨](https://developers.facebook.com/docs/graph-api/overview/access-levels)).

## 영향 범위

### 데이터베이스 (V5 마이그레이션)

`security_credentials` 는 현재 사용자와 아무 연결이 없다.

```
id, token_encrypted, issued_at, expires_at, + 감사 6개
```

추가 필요:

| 컬럼 | 이유 |
|---|---|
| `app_account_id uuid NOT NULL` → `app_accounts(id)` | 누구의 연결인지. `auth_refresh_tokens` 가 이미 같은 패턴을 쓴다 |
| `ig_user_id varchar` | 2단계 응답에서 받은 계정 번호. `INSTAGRAM_USER_ID` 를 대체 |
| `ig_username varchar` | 화면에 "어느 계정이 연결됨"을 보여주기 위해 |
| `scopes text` | 부여된 권한 기록. `content_publish` 누락을 미리 잡을 수 있다 |
| 부분 유니크 인덱스 `(app_account_id) WHERE deleted_at IS NULL` | 사용자당 활성 연결 1개 |

⚠️ **명세 이탈**: `1_spack.md` ENT-03 은 속성을 3개(`credentialId`, `token`, `expiresAt`)로 규정한다.
위 4개 추가는 명세를 넘어서는 변경이라 ADR 에 근거를 남겨야 한다.

### 게시할 때 누구의 토큰을 쓰는가 — 미해결

`queue_items` 에는 소유자 컬럼이 없다. 감사 컬럼 `created_by varchar(100)`(사용자명 문자열)뿐이다.
백그라운드 워커가 예약 건을 게시할 때 **어느 사용자의 토큰을 쓸지** 결정할 근거가 없다.

| 안 | 내용 | 평가 |
|---|---|---|
| A | `queue_items` 에 `app_account_id` FK 추가 | 정석. AGG-01 을 건드리고 마이그레이션이 하나 더 필요 |
| B | 감사 컬럼 `created_by` 로 사용자를 찾아 쓴다 | 마이그레이션 불필요. 그러나 **감사 컬럼을 업무 로직에 쓰는 것**이고, 사용자명이 바뀌면 깨진다 |

→ A 를 권한다. B 는 감사 컬럼의 의미를 오염시킨다.

### 백엔드

- `GET  /api/v1/instagram/connect` — 인증 URL 생성. `state` 를 발급·저장(CSRF 방지)
- `GET  /api/v1/instagram/callback` — `code`·`state` 수신 → 2단계 → 3단계 → 암호화 저장.
  취소(`error=access_denied`)도 처리
- `DELETE /api/v1/instagram/connect` — 연결 해제(soft delete)
- `GET  /api/v1/instagram/connect` 상태 조회 — 연결된 계정명·만료일
- `InstagramProperties` 에 `clientId`(Instagram 앱 ID) 추가
- `InstagramGraphClient` 에 `exchangeCodeForShortLivedToken`, `refreshLongLivedToken` 추가
- `SecurityCredentialService` 를 사용자별로 조회하도록 변경
- `InstagramReelsPublisher` 가 큐 항목 소유자의 토큰과 `ig_user_id` 를 쓰도록 변경
- 기존 API-05(단기 토큰 직접 입력)는 **유지**한다 — 명세 항목이고, OAuth 를 쓸 수 없는 상황의 대안

### 프론트엔드

- 릴스 화면의 "인스타그램 토큰" 카드를 **"인스타그램 계정 연결"** 로 바꾼다
  - 미연결: [계정 연결하기] 버튼 → 인증 창으로 이동
  - 연결됨: `@계정명 · 만료 D-nn` + [연결 해제]
- 기존 단기 토큰 입력 폼은 접어 두고 "직접 입력" 보조 수단으로 남긴다
- 콜백 처리 화면(성공/취소/실패 안내)

### 문서

- 새 ADR: OAuth 도입 (ADR-0022 의 결정을 부분 번복하는 근거)
- ADR-0022: "60일마다 재발급" → `refresh_access_token` 으로 연장 가능함을 정정
- ADR-0023: `INSTAGRAM_USER_ID` 가 불필요해지므로 갱신
- 안내 모달·README: 연결 방식 변경, 앱 대시보드에 리디렉션 URI 등록 단계 추가
- `.env.example`: `INSTAGRAM_APP_ID` 추가, `INSTAGRAM_USER_ID` 폐기 표시
- IMPLEMENTATION-CHECKLIST: 명세 외 추가분 표에 기재

## 단계 계획 (각 단계 끝에서 멈추고 확인)

| 단계 | 내용 | 검증 |
|---|---|---|
| 0 | **HTTPS 방식 결정** (제약 1) + 앱 대시보드에 리디렉션 URI 등록 | 등록 성공 확인 |
| 1 | V5 마이그레이션 + 엔티티/리포지토리 + 사용자별 조회 | 실제 PostgreSQL 로 테스트 |
| 2 | OAuth 3단계 백엔드 (`connect`/`callback`/`disconnect`) + `state` CSRF | 단위·통합 테스트 |
| 3 | 게시 경로를 사용자별 토큰으로 전환 (`queue_items.app_account_id`) | 기존 게시 테스트 회귀 |
| 4 | 화면 전환 (연결 버튼·상태 표시·해제) | 브라우저 실측 |
| 5 | `refresh_access_token` 자동 연장 | 만료 임박 시나리오 테스트 |
| 6 | 문서·ADR·체크리스트 정리 | 링크 검사, 문서 간 대조 |

## 위험

- ⚠️ **가장 큰 위험 — 인증 단계 자체가 Meta 쪽에서 막혀 있을 수 있다 (2026-07-30 발견).**
  사용자가 앱 대시보드에서 토큰을 만들려 할 때 **"개발자 역할 권한이 부족합니다"**
  (Insufficient developer role) 로 막혔다. Instagram 테스터 등록·초대 수락 등 문서가
  요구하는 조건을 갖췄는데도 같은 오류가 난다는 신고가 Meta 개발자 커뮤니티에 있고,
  확인 시점에 **상태가 "해결되지 않음"** 이다
  ([스레드](https://developers.facebook.com/community/threads/1360157352263976/)).

  **이 계획에 직접 영향이 있다**: 아래 2단계(OAuth) 는 같은 인증 창을 거친다.
  즉 HTTPS 를 해결하고 OAuth 를 다 구현해도 **같은 지점에서 막힐 수 있다.**
  → **0단계보다 먼저** 확인해야 할 것: 지금 방식(앱 대시보드 [토큰 생성])으로
  토큰이 한 번이라도 발급되는지. 그것이 안 되면 이 계획은 시작할 근거가 없다.
- **제약 1을 해결하지 못하면 기능이 로컬에서 한 번도 동작하지 않는다.** 0단계가 먼저다.
- 제약 2 때문에 앱 검수 전에는 "여러 사용자"라는 목표가 실제로는 성립하지 않는다.
- 인증 코드가 1시간·1회용이라 콜백 실패 시 재시도 흐름이 필요하다.
- `ig_exchange_token` 은 비멱등이라 재시도하면 안 된다([ADR-0009](../decisions/2026-07-29-no-retry-on-token-exchange.md)) — 새로 만드는 2단계 교환도 같은 원칙을 따라야 한다.
