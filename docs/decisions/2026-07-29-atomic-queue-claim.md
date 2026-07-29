---
description: 예약 선점을 조건부 UPDATE 로 원자화 — Replicas 2 에서 두 인스턴스가 같은 영상을 두 번 게시하는 것을 락 없이 막는다
tags: [decision]
---

# ADR-0015: 예약 선점은 조건부 UPDATE 로 원자화한다

- 기록일: 2026-07-29 14:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — SVC-02 Replicas: 2](../../3_architecture.md), [2_ddd.md — AGG-02](../../2_ddd.md)
- 선행 결정: [ADR-0013](2026-07-29-background-publish-worker.md)

## 맥락 (Context)

[3_architecture.md](../../3_architecture.md) 는 SVC-02 를 `Replicas: 2` 로 배포한다고 명시한다.
[ADR-0013](2026-07-29-background-publish-worker.md) 에 따라 각 인스턴스에서 워커가 돌므로,
두 워커가 거의 같은 시각에 발화한다.

처음 구현은 "읽어서 상태를 확인하고 → 저장"하는 방식이었다:

```java
QueueItem item = requireById(id);   // status == PENDING 확인
item.markRunning();                 // 메모리에서 상태 변경
repository.save(item);              // UPDATE ... WHERE id = ?
```

두 인스턴스가 모두 PENDING 을 읽으면 둘 다 검증을 통과하고, UPDATE 조건절이 `id` 뿐이라
충돌하지 않는다. **결과: 같은 영상이 인스타그램에 두 번 게시된다.**
게시는 되돌릴 수 없으므로(비멱등, [ADR-0009](2026-07-29-no-retry-on-token-exchange.md) 참조)
피해가 확정적이다.

적대적 코드 검토에서 이 시나리오가 확정 결함으로 판정됐다.

## 결정 (Decision)

조건부 UPDATE 로 선점한다. 영향 행 수가 1이면 선점 성공, 0이면 다른 인스턴스가 이미 가져간 것이다.

```sql
UPDATE queue_items SET status = 'RUNNING'
 WHERE id = ? AND status = 'PENDING' AND deleted_at IS NULL
```

함께 도입: 처리 중 인스턴스가 죽어 RUNNING 으로 멈춘 항목을 일정 시간 후 대기로 회수한다.

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 조건부 UPDATE (영향 행 수로 판정) | 추가 인프라·의존성 없이 DB 가 직렬화해 준다. 한 번의 왕복으로 끝난다 | "선점"이라는 개념이 코드에 드러나야 한다(주석 필요) | **채택** |
| 낙관적 락(`@Version`) | JPA 표준 방식 | 컬럼 추가 + 마이그레이션 필요. 충돌 시 예외를 잡아 처리해야 해서 흐름이 복잡해진다. 결과는 조건부 UPDATE 와 동일 | 기각 |
| 비관적 락(`SELECT ... FOR UPDATE`) | 확실함 | 워커가 배치를 훑는 동안 행을 잠가 다른 인스턴스를 대기시킨다(처리량 저하). 데드락 위험 | 기각 |
| 분산 락(Redis 등) | 여러 자원에 걸친 조정 가능 | 3_architecture.md 에 Redis 가 없다 — 아키텍처에 없는 구성요소 발명. 이 문제에는 과하다 | 기각 |
| 인스턴스를 1대로 줄인다 | 문제가 사라진다 | 3_architecture.md 의 `Replicas: 2` 를 어긴다. 가용성 저하 | 기각 |

## 근거 (Rationale)

이 문제는 "여러 프로세스가 한 행을 두고 경합"하는 가장 단순한 형태이고,
관계형 DB 는 이미 그것을 원자적으로 해결하는 수단(조건부 UPDATE)을 제공한다.
낙관적 락은 같은 결과를 얻으면서 컬럼과 예외 처리를 더하고, 분산 락은 없는 인프라를 요구한다.
가장 적은 것을 더해 정확성을 얻는 선택을 했다.

회수(reclaim)를 함께 넣은 이유: 선점만 있으면 처리 중 인스턴스가 죽었을 때 그 항목이
RUNNING 으로 영구히 멈춘다. 게다가 [ADR-0003](2026-07-29-status-enum-internal-vs-api.md) 의 변환 때문에
API 에서는 PENDING 으로 보여, 운영자는 "아직 대기 중"이라고 오해한다.

회수 시 `markFailed` 를 쓰지 않고 전용 메서드(`reclaimFromStalled`)를 만든 이유:
인스턴스가 죽은 것은 그 예약의 실패가 아니므로 재시도 횟수를 소모시키면 안 된다.
그렇게 하면 인프라 문제로 재시도 한도가 소진된다.

## 영향 (Consequences)

- 긍정: 같은 항목을 두 번 선점하려 하면 두 번째가 false 를 받는 것을 테스트로 고정했다.
  멈춘 항목이 회수되고, 그때 재시도 횟수가 늘지 않는 것도 고정했다.
- 트레이드오프/비용:
  - 회수 임계값(15분)이 너무 짧으면 **정상 처리 중인 항목을 회수해 이중 게시를 유발한다.**
    파이프라인 최대 소요(업로드 타임아웃 5분 + 인코딩 대기 60초)보다 충분히 크게 잡았다.
  - 선점에 실패한 인스턴스는 그 항목을 조용히 건너뛴다(로그는 debug 레벨).
- 후속 제약:
  - **`tryClaimForPublishing` 을 "읽고 저장" 방식으로 되돌리면 이중 게시가 재발한다.**
  - 파이프라인의 최대 소요 시간을 늘리면 `STALLED_AFTER` 도 함께 늘려야 한다.
  - 회수는 워커 주기마다 실행되므로, 워커가 꺼져 있으면 회수도 멈춘다.
