---
description: 릴스 업로드가 새 엔티티를 만들지 않고 QueueItem 애그리거트를 재사용 — containerId 는 QueueItem 의 id
tags: [decision]
---

# ADR-0012: 릴스 업로드는 QueueItem 애그리거트를 재사용한다

- 기록일: 2026-07-29 14:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — API-04 / ENT-02](../../1_spack.md), [2_ddd.md — AGG-02](../../2_ddd.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 3 Task 3.1](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

API-04 응답은 `containerId` (타입 `uuid`) 를 요구한다 — "생성된 컨테이너 식별자".
그런데 인스타그램이 발급하는 컨테이너 ID 는 숫자 문자열이라 `uuid` 타입에 담을 수 없다.
따라서 `containerId` 는 **우리 쪽 식별자**여야 하고, 의미가 있으려면 어딘가에 영속화되어야 한다.

명세(1_spack.md §2 Entities)에는 엔티티가 3개뿐이고 "ReelsContainer" 같은 것은 없다.

## 결정 (Decision)

새 테이블·엔티티를 만들지 않고 {@code QueueItem}(AGG-02)을 재사용한다.
즉시 업로드는 `scheduledAt = now` 인 예약일 뿐이며, `containerId` 는 그 QueueItem 의 id 다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| QueueItem 재사용 | 명세에 없는 엔티티를 발명하지 않는다. 예약 발행과 즉시 업로드가 같은 실행 경로·같은 상태 기계·같은 이력 연결을 공유한다. `containerId` 로 `GET /api/v1/queues` 에서 진행 상태를 추적할 수 있다 | 같은 UUID 가 API 에 따라 `queueId`·`containerId` 두 이름으로 나간다 | **채택** |
| `reels_containers` 테이블 신설 | 이름이 개념과 일치 | 명세에 없는 4번째 엔티티를 발명하는 셈. 상태 기계·재시도·실패 이력 연결을 QueueItem 과 중복 구현해야 한다(두 벌 유지보수) | 기각 |
| 인스타그램 컨테이너 ID 를 그대로 반환 | 가장 직관적 | 명세가 규정한 `uuid` 타입 위반. 게시 전(4단계 시작 전)에는 아직 존재하지 않아 201 응답에 담을 수 없다 | 기각 |
| 응답만 만들고 저장하지 않음 | 가장 간단 | `containerId` 로 아무것도 조회할 수 없어 의미 없는 값이 된다. 진행 상태 추적 불가 | 기각 |

## 근거 (Rationale)

"즉시 업로드"와 "예약 업로드"는 도메인적으로 같은 일이다 — 발행 시각만 다르다.
AGG-02 는 이미 그 일을 위한 애그리거트이고 상태 기계(PENDING→RUNNING→COMPLETED/FAILED),
재시도 횟수, 실패 이력 연결을 모두 갖고 있다. 별도 엔티티를 만들면 그 전부를 복제해야 한다.

`CLAUDE.md` 규칙 3("요구사항을 절대 지어내지 마세요")에 따라, 명세에 없는 엔티티를 늘리기보다
있는 것을 쓰는 쪽을 골랐다.

## 영향 (Consequences)

- 긍정: 테이블 추가 없음. 예약과 즉시 업로드가 하나의 워커·하나의 상태 기계로 처리된다.
  API-04 로 접수한 작업을 API-02 목록에서 그대로 추적할 수 있다(통합 테스트로 고정).
- 트레이드오프/비용:
  - 같은 값이 두 이름으로 노출된다. `_workspace/api_contracts.md` 와 DTO 주석에 명시했다.
  - API-04 응답의 `status` 는 큐 상태가 아니라 이 엔드포인트 의미대로 `"PROCESSING"` 고정이다
    (명세가 `status` 를 enum 이 아닌 자유 형식 string 으로 두고 예시가 `"PROCESSING"` 이다).
- 후속 제약:
  - 즉시 업로드는 `scheduledAt=now` 로 등록되므로 워커가 켜져 있어야 실제로 게시된다.
  - 중복 게시 차단은 이력(성공 여부) + 진행 중 작업(PENDING/RUNNING) **두 축**으로 봐야 한다 —
    한 축만 보면 게시 전 창에서 같은 영상이 두 번 접수된다(적대적 검토에서 확인된 결함).
