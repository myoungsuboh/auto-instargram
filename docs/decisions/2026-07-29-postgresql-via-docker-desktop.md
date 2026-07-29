---
description: PostgreSQL 로컬 기동 수단으로 Docker Desktop 을 채택 (네이티브 설치·기존 원격 DB 기각)
tags: [decision]
---

# ADR-0001: PostgreSQL 기동 수단으로 Docker Desktop 채택

- 기록일: 2026-07-29 11:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — §3 Data Layer (DB-01)](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 1 Task 1.3](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

[3_architecture.md](../../3_architecture.md) §3 은 DB-01 Primary RDBMS 의 Tech Stack 을 `PostgreSQL` 로 명시하고,
[00-ORCHESTRATOR.md](../../00-ORCHESTRATOR.md) Task 1.3 은 이를 `docker-compose.dev.yml` 로 세우라고 지시한다.
그런데 개발 환경(Windows 11)을 점검한 결과 Java 17 · Node 24 · Git 은 있으나 **Docker 도 PostgreSQL 도 설치되어 있지 않았다.**
DB 를 실제로 띄울 수단이 없으면 Phase 1 의 Verify(마이그레이션으로 연결 확인)를 통과할 수 없어 진행이 막힌다.
또한 Task 6.3/6.4 는 최종 산출물이 `docker compose up -d` 를 포함한 실행 스크립트로 **깨끗한 상태에서 기동**되어야 한다고 요구한다.

## 결정 (Decision)

로컬 PostgreSQL 은 Docker Desktop + `docker-compose.dev.yml` 로 기동한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| Docker Desktop + docker-compose | 설계서 지시와 정확히 일치. 버전(PG17) 고정으로 재현성 확보. `down -v` 로 깨끗한 상태 검증 가능(Task 6.4). 다른 개발자·다른 PC 에서 동일하게 동작 | Docker Desktop 설치·실행이 전제. 상시 메모리 점유 | **채택** |
| PostgreSQL 네이티브 설치 | Docker 불필요. 상시 리소스 점유가 적음 | 설치·초기화가 수동. 포트/비밀번호를 사람이 맞춰야 함. Task 6.3 의 `docker compose up -d` 기반 실행 스크립트를 설계대로 만들 수 없음. PC 마다 환경이 갈림 | 기각 |
| 이미 있는 원격 PostgreSQL 사용 | 추가 설치 전혀 없음 | 이 프로젝트엔 그런 DB 가 없었음. 있더라도 ORCHESTRATOR 가 원격 DB 쓰기 전 STOP 을 요구하며, 마이그레이션·`down -v` 검증을 원격에 할 수 없음 | 기각 |
| 테스트용 임베디드 PostgreSQL(zonky 등) 병행 | Docker 없이도 자동 테스트 가능 | 설계서에 없는 의존성 추가. 운영 경로와 테스트 경로가 갈려 "설계대로 도는지" 증명이 약해짐 | 기각(현 시점) |

## 근거 (Rationale)

세 가지가 동시에 걸린다: ① Tech Stack 최종 권위인 `3_architecture.md` 가 PostgreSQL 을 지정, ② Task 1.3 이 docker-compose 로 세우라고 지정,
③ Task 6.3/6.4 가 `docker compose up -d` 를 포함한 무인 기동과 `down -v` 클린 검증을 요구.
네이티브 설치는 ①만 만족하고 ②③을 만족시킬 수 없어, 최종 단계에서 설계와 어긋난 실행 스크립트를 만들게 된다.
**사용자에게 세 안을 제시해 Docker Desktop 으로 확정받았다** — 추측으로 다른 DB(H2 등)로 갈아타지 않았다.

## 영향 (Consequences)

- 긍정: PG17 버전 고정으로 로컬/CI/타 PC 재현성 확보. `down -v` 로 진짜 클린 기동 검증 가능. 비밀값은 `.env` 주입 + `${VAR:?}` 로 fail-fast.
- 트레이드오프/비용: Docker Desktop 설치·실행이 진행의 전제 조건이 됨. 설치 전까지 Phase 1 Verify 의 DB 항목이 대기 상태로 남는다(실제로 이번에 대기 발생).
- 후속 제약:
  - `run.sh` / `run.bat`(Task 6.3)은 compose 기동 + healthcheck 대기를 포함해야 한다.
  - 마이그레이션 대상은 항상 `localhost` 여야 하며, 원격이면 STOP 후 사용자 확인(ORCHESTRATOR 규칙).
  - Docker 가 막힌 환경으로 바뀌면 이 ADR 을 대체하는 새 ADR 이 필요하다.
