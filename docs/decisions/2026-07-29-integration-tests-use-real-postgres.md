---
description: 통합 테스트를 인메모리 DB 로 바꾸지 않고 실제 PostgreSQL 에 대해 돌린다 — 마이그레이션의 제약이 실제로 검증되도록
tags: [decision]
---

# ADR-0010: 통합 테스트는 실제 PostgreSQL 에 대해 실행한다

- 기록일: 2026-07-29 12:32
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — DB-01](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Verify 2](../../00-ORCHESTRATOR.md)
- 선행 결정: [ADR-0001](2026-07-29-postgresql-via-docker-desktop.md)

## 맥락 (Context)

V1/V2 마이그레이션은 도메인 규칙의 상당 부분을 **DB 제약**으로 강제한다:
AGG-01~03 불변식 CHECK, 활성 레코드 한정 partial unique index, `updated_at` 자동 갱신 트리거.
이들은 PostgreSQL 고유 기능이거나 방언 차이가 큰 기능이다.

통합 테스트를 H2 등 인메모리 DB 로 돌리면 테스트가 빠르고 Docker 없이 실행되지만,
**위 제약이 하나도 검증되지 않는다** — partial index 는 문법이 다르고, plpgsql 트리거는 아예 안 돈다.

## 결정 (Decision)

`@SpringBootTest` 통합 테스트는 `docker-compose.dev.yml` 의 실제 PostgreSQL 17 에 대해 실행한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 실제 PostgreSQL (docker compose) | 마이그레이션·CHECK·partial unique·트리거가 실제로 검증된다. 운영과 같은 엔진 | 테스트 실행에 Docker + 컨테이너 기동이 필요. 테스트 간 데이터가 공유돼 격리가 약함 | **채택** |
| H2/HSQLDB 인메모리 | 빠르고 Docker 불필요, 완전 격리 | PostgreSQL 전용 문법(partial index, plpgsql)이 동작하지 않아 마이그레이션을 아예 적용할 수 없거나 제약이 조용히 빠진다 → "테스트는 통과하는데 운영에서 깨지는" 상태 | 기각 |
| Testcontainers | 테스트별 격리 + 실제 엔진 | 여전히 Docker 필요(이점 없음). 매 실행마다 컨테이너 기동으로 느려짐. 의존성 추가 | 기각(현 시점) |

## 근거 (Rationale)

이 프로젝트는 도메인 불변식을 **의도적으로** DB 계층에 내려놨다(애플리케이션 버그가 있어도
잘못된 데이터가 저장되지 않게 하려고). 그렇게 설계했으면 테스트도 그 계층을 실제로 때려야 한다.
인메모리 DB 로 바꾸는 순간 이 설계의 핵심이 검증 범위에서 사라지고,
Phase 1 에서 위반 시도 11건으로 확인한 방어가 회귀 방지를 못 받게 된다.

Docker 는 ADR-0001 에 따라 이미 필수 전제이므로 추가 부담이 아니다.

## 영향 (Consequences)

- 긍정: 마이그레이션·제약·트리거가 매 테스트 실행마다 검증된다. 운영과 같은 엔진·버전(PG 17.10).
- 트레이드오프/비용:
  - **PostgreSQL 이 떠 있지 않으면 통합 테스트가 실패한다.** 실행 전
    `docker compose -f docker-compose.dev.yml up -d` 가 필요하다(테스트 클래스 주석에 명시).
  - 테스트가 DB 상태를 공유한다 — 시드 계정·갱신 토큰 행이 누적된다. 그래서 단정은
    절대 개수가 아니라 "실행 전후 비교"나 "존재 여부"로 작성했다.
- 후속 제약:
  - **테스트가 느리다는 이유로 H2 로 바꾸면 도메인 제약 검증이 조용히 사라진다.** 바꾸지 말 것.
  - 테스트 격리가 더 필요해지면 H2 가 아니라 Testcontainers 또는 트랜잭션 롤백을 검토한다.
