---
description: 인스타그램 계정 번호를 사람이 손으로 알아내 환경변수에 적는 대신, 토큰 교환 시 서버가 함께 받아 저장한다
tags: [decision]
---

# ADR-0024: 계정 번호는 토큰 교환 때 서버가 함께 받아 저장한다

- 기록일: 2026-07-30
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — ENT-03 / API-04 / API-05](../../1_spack.md) · [2_ddd.md — AGG-03](../../2_ddd.md)
- 선행 결정: [ADR-0009](2026-07-29-no-retry-on-token-exchange.md) · [ADR-0022](2026-07-29-instagram-login-api-path.md) · [ADR-0023](2026-07-30-instagram-token-from-db-not-env.md)
- 관련 계획: [사용자별 인스타그램 계정 연결](../plans/2026-07-30-per-user-instagram-connect.md)
- 구현: [V5__credential_account_info.sql](../../BE/src/main/resources/db/migration/V5__credential_account_info.sql) · [security/domain/SecurityCredential.java](../../BE/src/main/java/com/autoinstagram/backend/security/domain/SecurityCredential.java) · [security/service/InstagramGraphClient.java](../../BE/src/main/java/com/autoinstagram/backend/security/service/InstagramGraphClient.java) · [security/service/SecurityCredentialService.java](../../BE/src/main/java/com/autoinstagram/backend/security/service/SecurityCredentialService.java) · [post/service/InstagramReelsPublisher.java](../../BE/src/main/java/com/autoinstagram/backend/post/service/InstagramReelsPublisher.java)

## 맥락 (Context)

게시에는 인스타그램 **계정 번호**가 필요하다. 지금까지 그 값은 사람이 이렇게 구했다:

1. 브라우저 주소창에
   `https://graph.instagram.com/v25.0/me?fields=user_id,username&access_token=<단기토큰>` 입력
2. 응답의 `user_id` 를 복사
3. `.env` 의 `INSTAGRAM_USER_ID` 에 붙여넣기
4. 서버 재시작

사용자가 이 절차를 "좀 불편하다"고 지적했고(2026-07-30), 실제로 **틀린 값이 들어가 있었다** —
숫자만 들어가야 하는 자리에 **23자의 숫자 아닌 문자열**이 있었다. 그대로면 토큰 갱신은 되지만
게시 단계에서 실패한다. 즉 이 절차는 불편한 데 그치지 않고 **조용히 실패하는 함정**이었다.

그런데 이 값은 **토큰만 있으면 API 가 알려주는 값**이다. 사람이 중간에서 옮겨 적을 이유가 없다.

## 결정 (Decision)

**토큰을 교환하는 그 트랜잭션에서 서버가 `GET /me?fields=user_id,username` 을 호출해
계정 번호와 계정 이름을 자격 증명과 같은 행에 저장한다.** 게시할 때는 그 값을 쓰고,
값이 없을 때만 기존 `INSTAGRAM_USER_ID` 환경변수로 대체한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 교환 시 함께 조회해 같은 행에 저장 | 사람이 개입할 단계가 사라진다. 틀린 값이 들어갈 여지가 없다. 토큰과 계정 번호가 **같은 행**에 있어 항상 짝이 맞는다 | `security_credentials` 에 컬럼 2개가 늘어 ENT-03 명세(속성 3개)를 넘어선다 | **채택** |
| 게시할 때마다 `/me` 를 호출해 알아낸다 | 저장할 것이 없다 | 게시마다 외부 호출이 하나 늘어 실패 지점이 생긴다. 게시는 이미 4단계 외부 호출이라 더 늘릴 이유가 없다 | 기각 |
| 응답으로만 알려주고 사람이 `.env` 에 넣게 한다 | 스키마 변경 없음 | 옮겨 적는 단계가 그대로 남는다 — 이번에 문제가 된 바로 그 단계다 | 기각 |
| 환경변수를 없애고 저장값만 쓴다 | 값 출처가 하나로 단순해진다 | 이 기능 이전에 발급된 토큰과 `/me` 조회 실패 시 게시가 아예 불가능해진다 | 기각 (대체 수단으로 남김) |

## 근거 (Rationale)

**사람이 옮겨 적는 단계는 없앨 수 있으면 없애는 게 맞다.** 그 단계에서 실제로 오류가 났고,
증상이 나타나는 시점(게시)이 원인 발생 시점(값 입력)과 멀어 원인을 찾기 어려웠다.

토큰과 계정 번호를 **같은 행에 함께 두는** 이유: 따로 조회하면 그 사이에 토큰 갱신이 끼어들어
서로 다른 자격 증명의 토큰과 계정 번호가 섞일 수 있다. 한 번의 조회로 함께 읽어 그 가능성을 없앤다.
그래서 `findCurrentForPublishing()` 이 둘을 묶어 돌려준다.

**저장 시점에 함께 넣는** 이유: 저장 후 UPDATE 로 붙이면 "토큰은 저장됐는데 계정 정보만 빠진"
중간 상태가 생긴다. 처음 쓰기에 포함시켜 쓰기를 한 번으로 끝낸다.

## 영향 (Consequences)

- 긍정: 토큰 발급 안내가 **8단계 → 7단계**로 줄었다(주소창에 URL 넣는 단계 삭제).
  갱신 성공 시 **연결된 계정 이름**이 화면에 표시돼, 엉뚱한 계정에 연결된 것을 즉시 알 수 있다.
  `INSTAGRAM_USER_ID` 를 손댈 필요가 없어졌다.
- 트레이드오프/비용:
  - `security_credentials` 에 `ig_user_id`·`ig_username` 두 컬럼이 늘었다.
    **1_spack.md ENT-03 이 규정한 속성 3개를 넘어서는 의도된 이탈이다** —
    다음 감사에서 "명세 초과"로 판정하지 말 것.
  - API-05 응답에 `igUsername` 필드가 추가됐다. 기존 필드를 바꾸지 않는 **추가**이므로
    명세를 따르는 소비자는 영향받지 않는다.
  - 토큰 교환에 외부 호출이 하나 늘었다(약 1회 추가 왕복). 교환은 사용자가 명시적으로
    누르는 드문 동작이라 POL-04 예산에 부담이 되지 않는다.
- 후속 제약 (중요):
  - **`/me` 조회 실패는 토큰 교환을 실패시키지 않는다.** `fetchAccountInfo` 가 예외 대신
    `Optional.empty()` 를 돌려준다. 교환은 비멱등이라(ADR-0009) 되돌리거나 다시 시도할 수 없고,
    부가 정보 조회 실패로 이미 발급된 장기 토큰을 버리면 사용자는 단기 토큰을 다시 받아야 한다 —
    손실이 훨씬 크다. 이 예외 처리를 "삼켜진 예외"로 보고 고치지 말 것.
  - `ig_username` 은 **식별자로 쓰지 않는다.** 사용자가 언제든 바꿀 수 있다. 표시 전용이다.
  - `/me` 는 공식 문서가 버전을 붙여 호출한다(`/v25.0/me`). Meta 는 버전을 생략하면
    가장 오래된 지원 버전으로 처리할 수 있어, 버전은 `app.instagram.api-version`
    (기본 `v25.0`)으로 명시한다. `/access_token` 은 공식 예시가 버전 없이 호출하므로 그대로 둔다.
  - `INSTAGRAM_USER_ID` 환경변수는 **대체 수단으로만 남는다.** 지우지 않은 이유는
    이 기능 이전에 발급된 토큰과 조회 실패 상황을 위해서다.
  - 사용자별 계정 연결(계획 문서)을 구현하면 이 컬럼들은 그 설계로 흡수된다.
    그때 `app_account_id` 와 함께 재검토한다.

## 검증

- `CredentialAccountInfoTest` 5건 — 계정 정보 보관, 없어도 발급 성공(토큰 유실 방지),
  3인자 발급 하위 호환, 빈 문자열→null 통일, `toString` 에 계정은 노출·토큰은 미노출
- 백엔드 전체 테스트 통과 — 통합 테스트가 실제 PostgreSQL 을 쓰므로
  ([ADR-0006](2026-07-29-integration-tests-use-real-postgres.md)) V5 마이그레이션도 함께 검증됐다
- FE lint 0건, build 통과
