---
description: DB PK 컬럼명을 규약대로 id 로 두고, 명세의 recordId·queueId·credentialId 는 API 응답에서만 매핑
tags: [decision]
---

# ADR-0004: DB PK 컬럼명은 `id`, 명세의 식별자 이름은 API 응답에서 매핑

- 기록일: 2026-07-29 11:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — §2 Entities](../../1_spack.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 1 Task 1.2](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

[1_spack.md](../../1_spack.md) §2 는 각 엔티티의 식별자를 `recordId`(ENT-01), `queueId`(ENT-02), `credentialId`(ENT-03) 로 규정하고,
API-01 응답 예시도 `{"queueId": "..."}` 로 되어 있다.
반면 Task 1.2 의 지정 스킬 [skills/db/snake_case-db-common-conventions.md](../../skills/db/snake_case-db-common-conventions.md) 규칙 2 는
**"PK 는 id, FK 는 참조테이블단수_id 형식으로 통일한다"** 고 못 박는다.
`CLAUDE.md` 규칙 4 는 skills 규칙을 위반하지 말라고 요구하므로, 두 요구를 동시에 만족시켜야 했다.

## 결정 (Decision)

DB 컬럼명은 규약대로 `id` 로 두고, 명세의 `recordId`/`queueId`/`credentialId` 는 API 응답 DTO 에서만 그 이름으로 노출한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| DB 는 `id`, API 는 명세 이름으로 매핑 | skills 규약과 API 계약을 **동시에** 만족. FK 네이밍(`queue_item_id`)이 규약과 일관됨. JPA `@Id` 관용구와 자연스럽게 맞음 | 컬럼명과 응답 필드명이 다르다는 것을 매핑 계층에서 알고 있어야 함 | **채택** |
| DB 컬럼도 `queue_id` 등 명세 이름 사용 | DB 와 API 필드명이 1:1 로 같아 추적이 쉬움 | skills 규약 정면 위반(CLAUDE.md 규칙 4). `queue_items.queue_id` 처럼 테이블명이 컬럼에 중복됨. FK 와 PK 네이밍이 뒤섞임 | 기각 |
| API 응답도 `id` 로 통일 | 매핑 불필요, 가장 단순 | API-01 응답 예시(`queueId`)와 어긋나 공개 계약 위반. 체크리스트의 API 항목이 문서와 불일치 | 기각 |

## 근거 (Rationale)

ADR-0003 과 같은 구조의 문제이며 같은 방식으로 푼다 — **저장 계층의 규약**과 **공개 계약**은 다른 층위이므로
매핑 한 겹으로 둘 다 지킬 수 있다. 어느 쪽도 위반하지 않는 유일한 안이었고,
`skills/db` 규약을 지켜야 FK 네이밍(`history_records.queue_item_id`)까지 일관되게 유지된다.
컬럼 ↔ API 필드 대응은 [_workspace/db_schema.json](../../_workspace/db_schema.json) 각 컬럼의 `maps_to_api_field` 에 기록해,
Phase 3 에서 DTO 를 만들 때 기억이 아니라 문서를 근거로 매핑하도록 했다.

같은 판단을 `timestamp` 컬럼에도 적용했다: ENT-01 은 필드명을 `timestamp` 로 규정하지만 SQL 예약어와 충돌하므로
컬럼명은 `recorded_at`(규약 4·의미를 드러내는 이름)으로 두고 API 응답 필드명은 `timestamp` 를 유지한다.

## 영향 (Consequences)

- 긍정: DB 스키마가 규약과 100% 일치하고, API 응답은 명세와 100% 일치한다. FK 네이밍이 일관된다.
- 트레이드오프/비용: 엔티티를 컨트롤러에서 그대로 반환하면 `id` 라는 이름으로 응답이 나가 계약이 깨진다 — DTO 를 반드시 거쳐야 한다(ADR-0003 과 동일한 제약).
- 후속으로 따라오는 결정·제약:
  - Phase 2/3 의 응답 DTO 에서 `id → queueId/recordId/credentialId`, `recorded_at → timestamp` 매핑을 명시한다.
  - 새 테이블을 추가할 때도 PK 는 `id`, FK 는 `<참조테이블단수>_id` 를 따른다.
