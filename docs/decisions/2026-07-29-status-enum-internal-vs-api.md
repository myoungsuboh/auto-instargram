---
description: spack 과 ddd 의 status enum 충돌을 "내부는 DDD 의 넓은 상태 저장, API 응답은 spack enum 으로 변환"으로 해소
tags: [decision]
---

# ADR-0003: status enum 충돌 — 내부 상태와 API 응답을 분리

- 기록일: 2026-07-29 11:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — §2 Entities](../../1_spack.md) / [2_ddd.md — §2 Aggregates](../../2_ddd.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 1 Task 1.2](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

두 명세 문서가 같은 필드에 대해 **서로 다른 상태 값 집합**을 규정한다.

| 대상 | [1_spack.md](../../1_spack.md) | [2_ddd.md](../../2_ddd.md) |
|------|-------------------------------|---------------------------|
| QueueItem.status | ENT-02 / API-01 응답: `PENDING\|SUCCESS\|FAILED` | AGG-02 불변식: `{PENDING, RUNNING, COMPLETED, FAILED}` |
| HistoryRecord.status | ENT-01: `SUCCESS\|FAILED` | AGG-01 불변식: `{SUCCESS, FAILED, RETRY}` |

`CLAUDE.md` 는 "spec 이 충돌할 때 [3_architecture.md](../../3_architecture.md) 가 최종 권위"라고 정하지만,
아키텍처 문서는 엔티티 상태 값을 **다루지 않는다.** 따라서 문서만으로는 해소되지 않는다.
동시에 이 값은 DB CHECK 제약 · Java enum · API 응답 · 화면 표시에 모두 걸려 있어 나중에 바꾸기 비싸다.
`IMPLEMENTATION-CHECKLIST.md` 는 "Aggregate QueueItem (불변식 2개)" 구현도 완료 조건으로 요구하므로 DDD 불변식을 그냥 버릴 수도 없다.

## 결정 (Decision)

도메인·DB 는 DDD 의 넓은 상태를 저장하고, API 응답 직전에 spack 의 좁은 enum 으로 변환해 내보낸다 (도메인 모델 ↔ DTO 분리).

변환 규칙:
- QueueItem: `PENDING→PENDING`, `RUNNING→PENDING`, `COMPLETED→SUCCESS`, `FAILED→FAILED`
- HistoryRecord: `SUCCESS→SUCCESS`, `FAILED→FAILED`, `RETRY→FAILED`

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 내부는 넓게 + API 는 좁게 (도메인/DTO 분리) | 두 문서를 **동시에** 만족. AGG-02/AGG-01 불변식을 그대로 구현 가능. 공개 API 계약이 문서와 어긋나지 않음. 4단계 업로드 파이프라인의 '진행 중'을 내부적으로 표현 가능 | 변환 계층(매핑) 한 겹이 늘어남. 내부 상태와 화면 표시가 1:1 이 아니라는 걸 알고 있어야 함 | **채택** |
| spack 기준으로 통일 | 가장 단순. 매핑 불필요 | AGG-02 불변식(`RUNNING`, `COMPLETED`)을 구현할 수 없어 체크리스트 항목이 거짓 체크가 됨. 업로드 '진행 중'과 '시작 전'을 구분 불가 | 기각 |
| ddd 기준으로 통일 | 상태가 가장 자상함. 매핑 불필요 | API 응답이 문서에 적힌 enum(`SUCCESS`)과 달라짐 → 공개 계약 위반. FE 및 외부 소비자가 문서대로 코딩하면 깨짐 | 기각 |
| 합집합(PENDING/RUNNING/SUCCESS/COMPLETED/FAILED/RETRY) | 양쪽 값을 모두 수용 | `SUCCESS` 와 `COMPLETED` 는 같은 개념의 다른 이름 — 둘 다 두는 것은 명세에 없는 요구사항 발명. 소비자가 어느 쪽을 볼지 모호 | 기각 |

## 근거 (Rationale)

두 문서는 **다른 층위**를 기술하고 있었다. `2_ddd.md` 는 애그리거트의 내부 생애주기(처리 중 상태 포함)를,
`1_spack.md` 는 외부에 공개하는 API 계약을 규정한다. 그래서 "어느 문서가 이기는가"가 아니라
**층위를 분리하면 둘 다 참**이 된다 — 어느 쪽도 위반하지 않고 요구사항을 발명하지도 않는 유일한 안이었다.

문서가 서로 모순하는 경우라 추측하지 않고 **사용자에게 세 안을 제시해 이 안으로 확정받았다.**
결정 근거와 매핑 표는 [_workspace/db_schema.json](../../_workspace/db_schema.json) 의 `status_enum_decision` 에도 기계가 읽을 형태로 남겼다
(ORCHESTRATOR 는 각 Phase 시작 시 이 파일을 다시 읽으라고 지시한다 — 기억에 의존하면 이 결정이 유실된다).

## 영향 (Consequences)

- 긍정: DB CHECK 제약이 DDD 불변식을 그대로 강제한다(`ck_queue_items_status`, `ck_history_records_status`).
  공개 API 는 문서와 100% 일치하므로 FE·외부 소비자가 문서대로 작성해도 깨지지 않는다.
- 트레이드오프/비용: 응답 매핑 계층이 필요하다. 내부 상태를 그대로 응답에 흘리면 계약이 깨지므로 **엔티티를 컨트롤러에서 직접 반환하면 안 된다.**
- 후속으로 따라오는 결정·제약:
  - Phase 3 에서 QueueItem/HistoryRecord 의 응답 DTO 와 변환 함수를 반드시 거치게 한다 (엔티티 직접 노출 금지).
  - 변환은 정보 손실이 있다(`RUNNING`/`PENDING` 구분, `RETRY`/`FAILED` 구분이 응답에서 사라짐).
    화면에서 재시도 상태를 보여줘야 하는 API-02 요구는 `status` 가 아니라 별도 필드(`retry_count`, `last_error_code`)로 충족한다.
  - 이 매핑에 대한 단위 테스트를 Phase 3 에 둔다 (계약 회귀 방지).
