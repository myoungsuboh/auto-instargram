---
description: POL-01 의 "누락 없이"를 위해 게시 시도를 append-only 별도 테이블에 쌓는다 — 이력 테이블은 미디어당 1행이라 실패가 소실됐다
tags: [decision]
---

# ADR-0014: POL-01 감사 추적을 별도 append-only 테이블로 분리

- 기록일: 2026-07-29 14:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — POL-01 / ENT-01](../../1_spack.md), [2_ddd.md — AGG-01](../../2_ddd.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 3 Verify 3](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

두 요구가 정면으로 부딪힌다:

- **AGG-01 불변식 1** (2_ddd.md): `hash value must be unique per media`
  → `history_records.content_hash` 에 활성 레코드 한정 유니크 인덱스. 미디어당 1행.
- **POL-01** (1_spack.md): "모든 실패 경로는 **누락 없이** 로그 및 이력에 기록되어야 함"
  → 실패는 하나도 사라져선 안 된다.

미디어당 1행이면 같은 영상의 여러 시도가 한 행을 덮어쓴다.
**Phase 3 검증에서 실제로 관측된 수치**:

```
실패한 큐 항목 5건  vs  실패 이력 3행  vs  이력에 연결된 큐 2건
```

실패 3건이 이력에서 사라졌다 — POL-01 의 실질적 위반이다.
(원인: 테스트가 만든 두 파일 `upload-ok.mp4`·`trackable.mp4` 가 내용이 같아 해시가 같았다.
운영에서도 같은 영상을 두 번 예약하면 동일하게 발생한다.)

## 결정 (Decision)

역할을 두 테이블로 나눈다:

| 테이블 | 단위 | 유니크 | 갱신 | 목적 |
|---|---|---|---|---|
| `history_records` | 미디어당 1행 | content_hash 유니크 | 결과를 덮어씀 | 중복 업로드 방지 + API-03 조회 |
| `publish_attempts` | 시도당 1행 | **없음(의도적)** | append-only | POL-01 누락 없는 감사 추적 |

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| append-only 감사 테이블 분리 | AGG-01 불변식과 POL-01 을 <b>둘 다</b> 지킨다. skills/db/soft-delete-soft-delete-audit.md 규칙 6("변경 이력은 별도 테이블")이 지정한 방식 | 테이블 1개 추가. 실패 조사 시 두 테이블을 봐야 한다 | **채택** |
| 유니크를 (content_hash, queue_item_id) 로 완화 | 테이블 추가 없음 | AGG-01 불변식 1("per media")을 깨뜨린다. 같은 미디어가 여러 행을 갖게 되어 중복 업로드 방지의 근거가 사라진다 | 기각 |
| 유니크를 제거하고 모든 시도를 history_records 에 | 가장 단순 | AGG-01 불변식 1 직접 위반. API-03 조회 결과가 시도 횟수만큼 중복 표시된다 | 기각 |
| 로그만으로 POL-01 충족 | 코드 변경 없음 | POL-01 은 "로그 **및** 이력"을 요구한다. 로그는 보존 기간이 짧고 조회·감사가 어렵다 | 기각 |

## 근거 (Rationale)

두 요구가 서로 다른 질문에 답한다:
- "이 영상은 게시됐는가?" → 미디어당 하나의 답 (history_records)
- "무슨 일이 있었는가?" → 시도마다 하나의 기록 (publish_attempts)

한 테이블로 두 질문에 답하려 하면 어느 한쪽이 반드시 손상된다.
`skills/db/soft-delete-soft-delete-audit.md` 규칙 6 이 정확히 이 상황을 위한 규정이다.

`PublishAttempt` 를 불변 객체로 만든 이유: 감사 기록이 나중에 수정 가능하면 감사 가치가 없다.
상태 변경 메서드를 아예 제공하지 않고, 그것이 유지되는지 테스트로 고정했다.

## 영향 (Consequences)

- 긍정: 실패 5건이 모두 감사 기록에 남는 것을 테스트로 고정했다(`Pol01AuditTrailTest`).
  재시도 횟수·각 시도의 원인 코드가 보존된다. 성공 후 실패가 와도 양쪽 정보가 모두 남는다.
- 트레이드오프/비용:
  - 테이블이 계속 커진다(append-only). `_workspace/db_schema.json` 의 retention_policy 에 맞춘
    정리 배치가 필요하지만 명세에 배치 요구사항이 없어 정책만 문서화했다.
  - `queue_item_id` 에 외래키가 있어 감사 기록을 남기려면 큐 항목이 먼저 존재해야 한다
    (큐 항목이 삭제되면 `ON DELETE SET NULL` 로 기록만 남는다).
- 후속 제약:
  - **`publish_attempts` 에 유니크 제약을 추가하면 안 된다** — 중복처럼 보이는 것이 이 테이블의 존재 이유다.
  - `PublishAttempt` 에 setter/update 메서드를 추가하면 append-only 원칙이 깨진다(테스트가 막는다).
  - 실패를 기록하는 경로는 `HistoryService.recordFailure` 하나로 유지한다 —
    다른 경로를 만들면 감사 기록이 다시 누락된다.
