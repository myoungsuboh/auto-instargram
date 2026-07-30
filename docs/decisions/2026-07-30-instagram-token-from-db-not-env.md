---
description: 인스타그램 액세스 토큰은 환경변수가 아니라 DB(암호화 저장)에서 읽는다 — 3_architecture.md 의 필수 환경변수 목록과 어긋나는 부분을 명시한다
tags: [decision]
---

# ADR-0023: 인스타그램 토큰은 `INSTAGRAM_ACCESS_TOKEN` 이 아니라 DB 에서 읽는다

- 기록일: 2026-07-30
- 갱신: 2026-07-30 — `INSTAGRAM_USER_ID` 도 같은 이유로 사실상 불필요해졌다.
  토큰 교환 시 서버가 계정 번호를 함께 받아 저장한다([ADR-0024](2026-07-30-fetch-ig-account-on-token-exchange.md)).
  이제 이 환경변수는 자동 조회 실패 시의 **대체 수단**으로만 남는다.
- 상태: 승인됨
- 단계(Origin): dev (execute-dev) — 사후 기록(작업 교차검증 중 발견)
- 관련 spec: [3_architecture.md — SVC-02 Required env vars](../../3_architecture.md) · [1_spack.md — ENT-03 / API-05](../../1_spack.md) · [2_ddd.md — AGG-02 SecurityCredential](../../2_ddd.md)
- 선행 결정: [ADR-0008](2026-07-29-token-at-rest-encryption.md) · [ADR-0022](2026-07-29-instagram-login-api-path.md)
- 구현: [post/service/InstagramReelsPublisher.java](../../BE/src/main/java/com/autoinstagram/backend/post/service/InstagramReelsPublisher.java) · [security/service/SecurityCredentialService.java](../../BE/src/main/java/com/autoinstagram/backend/security/service/SecurityCredentialService.java) · [security/service/InstagramProperties.java](../../BE/src/main/java/com/autoinstagram/backend/security/service/InstagramProperties.java)

## 맥락 (Context)

명세 두 곳이 **토큰을 어디서 가져오는지** 서로 다르게 말한다.

| 문서 | 위치 | 말하는 내용 |
|---|---|---|
| `3_architecture.md` | SVC-02 배포 → `Required env vars` | `INSTAGRAM_ACCESS_TOKEN` 이 **필수 환경변수** |
| `1_spack.md` | ENT-03 `SecurityCredential` | 토큰은 **암호화된 값으로 저장되는 엔티티** |
| `1_spack.md` | API-05 `POST /api/v1/tokens/refresh` | 단기 토큰을 장기 토큰으로 **교환해 갱신**한다 |
| `2_ddd.md` | AGG-02 `SecurityCredential` | 토큰 갱신·보관을 담당하는 애그리거트 |

Phase 2·3 구현은 후자(엔티티 저장)를 따랐다. `InstagramReelsPublisher` 는
`credentialService.findCurrentPlainToken()` 으로 **DB 에서** 토큰을 꺼내 쓰고,
`InstagramProperties` 에는 `accessToken` 필드가 아예 없다.

그 결과 `INSTAGRAM_ACCESS_TOKEN` 은 **어떤 코드도 읽지 않는 죽은 변수**가 됐는데,
`.env.example` 은 계속 "비워 두면 인스타그램 실제 연동만 비활성화된다"고 설명하고 있었다.
비개발자가 안내를 따라가다 이 칸에 토큰을 붙여넣고 "왜 안 되지" 하게 되는 함정이다.
2026-07-30 작업 교차검증에서 발견했다.

## 결정 (Decision)

**토큰은 DB 에서만 읽는다.** `INSTAGRAM_ACCESS_TOKEN` 은 값을 넣어도 효과가 없다.
`.env.example` 에 그 사실을 명시하고, 토큰을 넣는 실제 위치(릴스 화면 → 토큰 갱신)를 가리킨다.

변수 줄 자체는 **남겨 둔다** — `3_architecture.md` 가 필수로 적어 둔 항목이라,
지우면 명세와 대조할 때 "빠졌다"로 보이기 때문이다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| DB 에서만 읽고, env 변수는 "안 쓰임"으로 명시 | ENT-03(암호화 저장)과 API-05(갱신)를 그대로 만족한다. 60일마다 바뀌는 값을 재배포 없이 갱신할 수 있다. 이미 구현·테스트된 동작이라 변경 위험이 없다 | `3_architecture.md` 의 필수 환경변수 목록과 형식적으로 어긋난다 | **채택** |
| `INSTAGRAM_ACCESS_TOKEN` 을 부트스트랩 토큰으로 읽어 DB 가 비었을 때 대체 사용 | 화면에 들어가지 않고도 게시를 시도할 수 있다 | 토큰이 `.env` 에 **평문**으로 남아 ENT-03·ADR-0008(암호화 저장)의 취지를 정면으로 깬다. 60일마다 파일을 고치고 재시작해야 한다. 토큰 출처가 두 곳이 되어 "왜 옛 토큰이 쓰이지" 류의 디버깅이 어려워진다 | 기각 |
| 변수를 `.env.example` 에서 삭제 | 죽은 설정이 사라져 깔끔하다 | 명세가 필수로 적은 항목이 사라져, 다음 감사에서 누락으로 오판된다 | 기각 |

## 근거 (Rationale)

장기 토큰은 **60일마다 만료**된다(ADR-0022). 환경변수는 재배포·재시작 없이 바꿀 수 없으므로,
만료되는 값을 담기에 부적합하다. 반면 API-05 + ENT-03 조합은 운영 중 화면에서 갱신할 수 있다.

또 ENT-03 이 "암호화된" 토큰을 요구하는데, `.env` 평문 저장은 그 요구와 정반대다.
`3_architecture.md` 의 `Required env vars` 는 배포 매니페스트 수준의 나열이고,
토큰의 **수명 관리 방식**을 규정한 것은 ENT-03·API-05·AGG-02 쪽이다 —
구체적인 규정이 나열보다 우선한다고 읽었다.

## 영향 (Consequences)

- 긍정: 토큰 출처가 한 곳(DB)이라 "어느 토큰이 쓰였나"가 항상 명확하다.
  운영 중 갱신이 화면에서 끝난다. 평문 토큰이 파일에 남지 않는다.
- 트레이드오프/비용: 게시를 처음 하려면 **반드시 릴스 화면에서 토큰 갱신을 한 번 해야** 한다.
  DB 를 초기화하면 토큰도 사라지므로 다시 갱신해야 한다.
- 후속 제약:
  - `INSTAGRAM_ACCESS_TOKEN` 에 값을 넣어도 게시가 되지 않는다. 증상은
    "토큰이 없어 게시할 수 없다"는 422 오류다 — 이 오류를 보면 env 가 아니라 화면을 봐야 한다.
  - `3_architecture.md` 와의 이 불일치는 **의도된 것**이다. 다음 감사에서
    "필수 환경변수 미구현"으로 판정하지 말 것.
  - 부트스트랩 토큰이 정말 필요해지면(예: 화면 없는 자동 배포) 위 2안을 다시 검토해야 하고,
    그때는 평문 노출을 어떻게 막을지가 선행 과제다.

## 검증

- `grep -rn "INSTAGRAM_ACCESS_TOKEN" BE/src` → 참조 0건
- `InstagramProperties` 필드: `graphBaseUrl`, `clientSecret`, `connectTimeout`, `readTimeout` (accessToken 없음)
- `application.properties` 에 `app.instagram.access-token` 항목 없음
- `InstagramReelsPublisher:113` → `credentialService.findCurrentPlainToken()`
