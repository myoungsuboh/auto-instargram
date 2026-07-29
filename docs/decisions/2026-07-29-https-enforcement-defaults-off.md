---
description: HTTPS 강제·HSTS 를 기본값 off 로 두고 환경변수로 켠다 — 로컬에서 켜면 브라우저 HSTS 캐시 때문에 되돌리기가 매우 어렵다
tags: [decision]
---

# ADR-0021: HTTPS 강제·HSTS 는 기본값 off, 환경변수로 켠다

- 기록일: 2026-07-29 15:35
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — §4 Connection Map (HTTPS/REST)](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 5 Task 5.1](../../00-ORCHESTRATOR.md)
- 적용 스킬: [skills/security/HTTPS-transport-security.md](../../skills/security/HTTPS-transport-security.md)
- 구현: [BE/.../config/TransportSecurityConfig.java](../../BE/src/main/java/com/autoinstagram/backend/config/TransportSecurityConfig.java)

## 맥락 (Context)

`skills/security/HTTPS-transport-security.md` 는 강한 요구를 한다:

- 규칙 1 — "모든 트래픽은 HTTPS로만 허용하고, HTTP 요청은 301로 HTTPS로 전환한다"
- 규칙 2 — "HSTS로 평문 재접속을 차단한다 ... 리다이렉트만으로는 최초 평문 요청이 노출되므로 HSTS가 필수다"

그런데 이 프로젝트의 실행 환경은 **로컬 개발이 기본**이다
([ADR-0001](2026-07-29-postgresql-via-docker-desktop.md), 그리고 ORCHESTRATOR Principle 10 —
"비개발자가 실행할 수 있어야 한다"). 로컬은 `http://localhost` 이고 TLS 인증서가 없다.

여기서 **비대칭적인 위험**이 생긴다:

- HTTPS 강제를 켠 채 `http://localhost` 로 접속하면 → 모든 요청이 거부되어 앱이 아예 동작하지 않는다.
- 더 심각한 것은 HSTS 다. 브라우저가 HSTS 헤더를 한 번 받으면 **그 뒤로는 서버 설정과 무관하게**
  해당 호스트를 https 로만 접속한다. `localhost` 에 HSTS 가 걸리면
  **그 브라우저에서 다른 로컬 프로젝트까지 함께 깨진다.**
  서버 설정을 되돌려도 낫지 않고, 사용자가 브라우저의 HSTS 캐시를 직접 지워야 한다
  (chrome://net-internals/#hsts). 원인을 모르면 며칠을 헤맬 수 있다.

## 결정 (Decision)

HTTPS 강제와 HSTS 를 환경변수 `HTTPS_ENFORCED` 로 분리하고 **기본값을 false** 로 둔다.
false 일 때는 기동 로그에 경고를 남겨 운영 배포 시 켜야 함을 알린다.
나머지 보안 헤더(CSP·X-Frame-Options·nosniff·Referrer-Policy·캐시 금지)는 **항상 켠다** —
이들은 http 에서도 부작용이 없다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 환경변수로 분리 + 기본 off | 로컬이 즉시 동작한다(Principle 10). 스킬 규칙 7("설정은 코드가 아닌 환경으로")에 정확히 부합 | 운영에서 켜는 것을 잊으면 인증 쿠키가 평문 전송된다 — 기동 경고로 완화 | **채택** |
| 항상 켠다 (기본 on) | 규칙 1·2 를 문자 그대로 이행 | 로컬 개발이 불가능해지고, HSTS 가 브라우저에 각인되어 **다른 로컬 프로젝트까지 깨뜨린다.** 서버를 되돌려도 복구되지 않는다 | 기각 |
| 프로파일로 분기 (dev/prod) | 스프링 관례에 부합 | 프로파일을 잊고 실행하면 어느 쪽이 적용됐는지 알기 어렵다. 환경변수 하나가 더 명시적이다 | 기각 |
| 로컬에 자체 서명 인증서 도입 | 로컬도 https 로 통일 | 인증서 생성·신뢰 등록을 사용자가 해야 한다 — 비개발자 실행(Principle 10)이 불가능해진다 | 기각 |

## 근거 (Rationale)

두 실패의 성질이 다르다:

- **운영에서 켜지 않은 실패**: 되돌릴 수 있다. 환경변수 하나를 바꾸고 재시작하면 끝난다.
- **로컬에서 켠 실패**: 서버 쪽에서 되돌릴 수 없다. 브라우저에 각인된 HSTS 는 사용자가
  브라우저 설정을 직접 손봐야 하고, 피해가 이 프로젝트를 넘어 같은 호스트(`localhost`)를
  쓰는 다른 프로젝트로 번진다.

되돌릴 수 없고 피해 범위가 넓은 쪽을 기본값에서 배제하는 것이 맞다.
그 대신 잊어버릴 위험은 **기동 시 경고 로그**와 이 ADR·README 로 보완한다.

부작용이 없는 헤더는 환경과 무관하게 항상 켰다 — 개발에서 꺼 두면
"개발에서는 통과했는데 운영에서 헤더가 달라 깨지는" 상황이 생긴다.

## 영향 (Consequences)

- 긍정: 로컬에서 `docker compose up` + 서버 기동만으로 즉시 동작한다.
  개발·운영에서 CSP·클릭재킹·MIME·리퍼러·캐시 헤더가 동일하게 적용된다(실측 확인).
- 트레이드오프/비용:
  - **운영 배포 시 `HTTPS_ENFORCED=true` 를 반드시 설정해야 한다.** 하지 않으면
    인증 쿠키(`ai_access`/`ai_refresh`)가 평문으로 전송된다.
  - 같은 이유로 `JWT_COOKIE_SECURE=true` 도 함께 켜야 한다([ADR-0006](2026-07-29-auth-token-transport.md)).
    두 값은 항상 같이 움직인다.
- 후속 제약:
  - **기본값을 true 로 "안전하게" 바꾸지 말 것.** 로컬 개발이 깨지고, HSTS 가 브라우저에
    각인되면 서버 설정으로 복구할 수 없다.
  - README 의 실행 안내와 운영 체크리스트에 두 환경변수를 함께 명시한다.
