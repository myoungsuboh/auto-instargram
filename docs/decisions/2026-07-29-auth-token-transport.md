---
description: 인증 토큰을 httpOnly 쿠키 우선 + Bearer 헤더 병행으로 받고, CSRF 토큰 대신 SameSite=Strict 로 방어
tags: [decision]
---

# ADR-0006: 인증 토큰 전달 방식 — 쿠키 우선 + Bearer 병행, CSRF 는 SameSite 로

- 기록일: 2026-07-29 12:30
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — §4 Connection Map](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 2 Task 2.1](../../00-ORCHESTRATOR.md)
- 계약 문서: [_workspace/api_contracts.md — §1 인증 방식](../../_workspace/api_contracts.md)

## 맥락 (Context)

설계 문서와 코딩 규칙이 토큰 저장 위치에 대해 다른 것을 요구한다:

| 출처 | 요구 |
|---|---|
| [3_architecture.md](../../3_architecture.md) Connection Map | FE → BE 인증은 `bearer` → 관례상 `Authorization: Bearer` 헤더 |
| [skills/security/JWT-authn-authz.md](../../skills/security/JWT-authn-authz.md) 규칙 1 | JWT 를 httpOnly·Secure·SameSite=Strict **쿠키**에 저장하고 localStorage/sessionStorage 에 저장하지 말 것 |

Bearer 헤더 방식을 브라우저에서 쓰려면 JavaScript 가 토큰을 어딘가(대개 localStorage)에 들고 있어야 하는데,
그것이 바로 규칙 1 이 금지하는 것이다(XSS 한 번으로 토큰이 탈취된다).
`CLAUDE.md` 규칙 4 는 skills 규칙 위반을 금지하므로 헤더 단독 방식은 선택할 수 없다.

쿠키를 쓰면 CSRF 가 새로 문제가 된다. 그런데 규칙 1 은 그 방어책까지 함께 지정하고 있다(SameSite=Strict).

## 결정 (Decision)

서버는 쿠키(`ai_access`)를 **우선** 확인하고 없으면 `Authorization: Bearer` 헤더로 넘어간다.
CSRF 는 토큰 방식 대신 SameSite=Strict + JSON 전용 + CORS 출처 제한으로 방어한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 쿠키 우선 + Bearer 병행 | 브라우저는 쿠키만 써서 JS 가 토큰을 만지지 않는다(규칙 1 충족). curl 등 프로그램 클라이언트는 Bearer 를 쓸 수 있어 architecture 의 `bearer` 계약도 유지된다 | 입력 경로가 둘이라 필터 로직이 조금 복잡하다. 두 경로 모두 테스트해야 한다 | **채택** |
| Bearer 헤더 단독 | architecture 문서와 문자 그대로 일치. CSRF 걱정 없음 | 브라우저에서 토큰을 JS 가 보관해야 함 → 규칙 1 정면 위반(XSS 취약) | 기각 |
| 쿠키 단독 | 가장 단순하고 안전 | curl·CI 에서 인증 호출이 번거로워지고, architecture 의 `bearer` 표기와 어긋난다. Verify 3 이 요구하는 curl 왕복이 불편해진다 | 기각 |
| 쿠키 + CSRF 토큰(이중 제출) | CSRF 방어가 가장 두텁다 | 규칙 1 이 지정한 방어(SameSite)를 넘어서는 추가 메커니즘. 화면·서버 양쪽에 토큰 관리 코드가 늘어난다. JSON 전용 API 에서 실익이 작다 | 기각(현 시점) |

## 근거 (Rationale)

ADR-0003·0004 와 같은 구조의 문제이며 같은 방식으로 풀었다 — **두 요구가 서로 다른 청중을 향한다.**
`bearer` 는 "이 API 는 토큰 기반 인증이다"라는 계약이고, 규칙 1 은 "브라우저에 토큰을 어떻게 두느냐"의 문제다.
경로를 둘로 열면 브라우저는 안전한 쪽을, 프로그램은 편한 쪽을 쓰면서 어느 쪽도 위반하지 않는다.

CSRF 토큰을 쓰지 않은 근거는 세 겹이 겹치기 때문이다:
1. 규칙 1 이 지정한 방어인 SameSite=Strict — 다른 사이트에서 시작된 요청에는 쿠키가 실리지 않는다.
2. 이 API 는 JSON 전용이다 — 폼 전송(`application/x-www-form-urlencoded`)으로는 호출할 수 없고,
   교차 출처 fetch 로 JSON 을 보내려면 CORS 사전 요청을 통과해야 한다.
3. Phase 5 에서 CORS 허용 출처를 프론트엔드로 제한한다.

## 영향 (Consequences)

- 긍정: 화면의 JavaScript 는 토큰을 볼 수 없다(XSS 로 탈취 불가). 실측으로 확인된 쿠키 속성 —
  `HttpOnly; SameSite=Strict`, 액세스 `Max-Age=900`, 갱신 쿠키는 `Path=/api/v1/auth` 로 좁혀 노출 표면을 줄였다.
- 트레이드오프/비용:
  - `Secure` 속성은 로컬 http 개발에서 false 여야 브라우저가 쿠키를 보낸다 →
    `JWT_COOKIE_SECURE` 환경변수로 분리했다. **운영에서 true 로 바꾸지 않으면 토큰이 평문 전송된다.**
  - SameSite 의 "site" 판정에 포트는 포함되지 않는다. 즉 같은 호스트의 다른 포트는 same-site 로 취급되어
    SameSite 만으로는 막히지 않는다. 실질 방어는 위 2·3(JSON 전용 + CORS 제한)이 담당한다.
- 후속으로 따라오는 결정·제약:
  - **Phase 5 는 CORS 허용 출처를 반드시 프론트엔드 출처로 제한해야 한다.**
    `*` 로 열면 위 근거 3 이 무너지고 CSRF 방어가 실제로 약해진다.
  - 운영 배포 시 `JWT_COOKIE_SECURE=true` 를 강제한다(Phase 5 전송 보안에서 재확인).
  - 화면은 httpOnly 쿠키를 읽을 수 없으므로 로그인 상태 판단에 `GET /api/v1/auth/me` 가 필요하다.
