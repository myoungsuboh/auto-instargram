---
description: 명세에 없는 대시보드 로그인 API 4개를 추가 — 5개 API 가 모두 인증을 요구하는데 인증을 얻을 창구가 없었음
tags: [decision]
---

# ADR-0005: 대시보드 로그인 기능 추가 (명세 외 API)

- 기록일: 2026-07-29 12:30
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — §1 APIs / §5 Screens](../../1_spack.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 2 Task 2.1 / Verify 2](../../00-ORCHESTRATOR.md)
- 계약 문서: [_workspace/api_contracts.md — §3 로그인 API](../../_workspace/api_contracts.md)

## 맥락 (Context)

[1_spack.md](../../1_spack.md) 의 API 5개는 **모두** `auth.required=true` 이고
`required_roles` 로 `[system_admin, system_operator]` 를 요구한다.
[3_architecture.md](../../3_architecture.md) Connection Map 도 FE → BE 인증을 `bearer` 로 규정한다.
그런데 **그 토큰을 발급해 주는 API 가 명세에 없다.** 화면이 인증을 얻을 방법이 존재하지 않는다.

`POST /api/v1/tokens/refresh` (API-05) 가 이름만 보면 후보 같지만, 명세를 읽으면 이것은
**인스타그램 Graph API 의 단기→장기(60일) 토큰 교환**이고 그 자체가 `system_admin` 인증을 요구한다.
즉 로그인 대체가 될 수 없다(닭과 달걀).

여기에 두 방향의 요구가 더 겹친다:
- [00-ORCHESTRATOR.md](../../00-ORCHESTRATOR.md) Verify 2 — "seed initial **admin credentials**",
  Verify 5 — "실제 FE 로 주요 흐름을 BE 에 대해 구동". 로그인 없이는 둘 다 불가능하다.
- [skills/security/JWT-authn-authz.md](../../skills/security/JWT-authn-authz.md) 규칙 2(갱신 토큰)와
  규칙 5(**로그인 실패** 제한)는 로그인 엔드포인트의 존재를 전제한다.

## 결정 (Decision)

명세에 없는 로그인 API 4개(`login` / `refresh` / `logout` / `me`)와 `app_accounts`·`auth_refresh_tokens` 테이블을 추가한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 로그인 API 추가 | 실제로 쓸 수 있는 서비스가 된다. 명세의 권한 구분(admin/operator)이 의미를 갖는다. skills 의 보안 규칙 2·5 를 충족할 수 있다. Verify 2·5 를 정직하게 통과한다 | 명세에 없는 API·테이블이 늘어난다. 체크리스트 26개 항목 외의 코드가 생긴다 | **채택** |
| 설정 파일에 고정 관리자 토큰 | 명세의 API 5개만 유지 — 문서에 가장 충실 | 토큰 유출 시 폐기 수단이 없다. 사용자 구분이 불가능해 감사 컬럼(`created_by`)이 무의미해진다. 규칙 2(갱신)·5(로그인 실패 제한)를 구현할 대상이 없다. 운영 불가 | 기각 |
| 인증을 끈다 | 가장 빠르다 | 명세가 전 API 에 `auth.required=true` 로 못 박은 것과 정면 충돌. `required_roles` 가 死문이 되고, API-05(관리자 전용)의 권한 분리가 사라진다 | 기각 |
| 외부 IdP(OAuth/OIDC) 도입 | 비밀번호를 직접 다루지 않는다 | 3_architecture.md 에 IdP 서비스가 없다 — 아키텍처 문서에 없는 구성요소를 발명하는 셈. 로컬 실행(Principle 10)도 어려워진다 | 기각 |

## 근거 (Rationale)

명세의 **누락**이지 금지가 아니라고 판단했다. 5개 API 가 인증을 요구한다는 것은
"인증 수단이 있다"는 전제이며, 그 수단만 문서화되지 않은 것이다.
그럼에도 API 를 늘리는 것은 명세 이탈이므로 **추측하지 않고 사용자에게 세 안을 제시해 확정받았다.**

기각한 "고정 토큰" 안의 결정적 문제는 폐기 불가능성이었다. 유출 시 대응 수단이 없는 인증은
운영에 올릴 수 없고, 이 프로젝트의 목표는 문서가 아니라 실제로 도는 서비스다.

## 영향 (Consequences)

- 긍정:
  - 화면이 실제로 로그인해 API 를 쓸 수 있다 — Verify 5 의 FE→BE→DB 왕복이 성립한다.
  - 명세의 권한 분리가 실동작한다(운영자가 API-05 호출 시 403 — curl 로 확인).
  - 감사 컬럼 `created_by`/`updated_by` 가 실제 사용자 이름으로 채워진다.
  - skills 보안 규칙 2·5 를 구현·검증할 수 있다(계정 잠금은 DB, IP 제한은 메모리).
- 트레이드오프/비용:
  - `IMPLEMENTATION-CHECKLIST.md` 의 26개 항목 밖에 코드가 존재한다.
    최종 감사에서 "명세 외 추가분"으로 구분해 보고해야 한다.
  - 비밀번호를 직접 보관하게 되어 BCrypt·잠금·시드 관리 책임이 생긴다.
- 후속으로 따라오는 결정·제약:
  - 토큰 전달 방식은 [ADR-0006](2026-07-29-auth-token-transport.md) 에서 별도로 정했다.
  - 시드 계정은 멱등하게 생성하며 비밀번호는 환경변수에서만 주입한다(`.env.example` 참조).
  - IP 단위 로그인 제한은 인스턴스 메모리에 있다 — 3_architecture.md 의 `Replicas: 2` 에서는
    인스턴스별로 계산되므로 실효 한도가 2배가 된다. 위조 불가능한 방어선은 DB 의 계정 잠금이다.
