---
name: "에러 처리 & 회복탄력성 (Error Handling & Resilience)"
description: "예외를 어떻게 잡고·전파하고·복구할지 규정하는 스택 중립 가이드: 에러 삼키기 금지, fail-fast vs graceful degradation, 재시도·타임아웃·회로 차단기·폴백·부분 실패. 외부 API·DB·큐 호출이나 예외 정책을 작성·검토할 때 읽는다(에러 수집·관측은 `error-monitoring`·`async-error-handling`에 위임). 키워드: error-handling, resilience, retry, backoff, timeout, circuit-breaker, fallback, fail-fast."
---

# 에러 처리 & 회복탄력성 (Error Handling & Resilience)

**ID:** `SKL-ERROR-HANDLING-RESILIENCE`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 예외를 어떻게 잡고·전파하고·복구할지 규정하는 스택 중립 가이드: 에러 삼키기 금지, fail-fast vs graceful degradation, 재시도·타임아웃·회로 차단기·폴백·부분 실패. 외부 API·DB·큐 호출이나 예외 정책을 작성·검토할 때 읽는다(에러 수집·관측은 `error-monitoring`·`async-error-handling`에 위임). 키워드: error-handling, resilience, retry, backoff, timeout, circuit-breaker, fallback, fail-fast.

---

## 지시사항 (Instructions)

1. 에러를 삼키지 않는다: 잡았으면 처리하거나, 못 하면 다시 던진다. 로그·처리 없는 빈 catch 금지.
2. 실패 유형을 구분한다: 프로그래밍 오류(버그·잘못된 입력)는 fail-fast(즉시 중단·노출), 외부 의존 실패(네트워크·DB·외부 API)는 graceful degradation(폴백·부분 동작)으로.
3. 일시적 실패는 재시도하되 안전하게: 지수 백오프 + 지터, 타임아웃, 재시도 상한을 항상 함께 둔다. 무한·즉시 재시도 금지.
4. 재시도하려면 멱등성이 필요하다: 부수효과가 있는 작업은 멱등 키로 중복을 막는다 (idempotency 참조).
5. 장애를 격리한다: 반복 실패하는 의존엔 회로 차단기로 호출을 끊고 폴백한다. 한 부분의 실패가 전체로 번지지 않게 한다.
6. 메시지는 청중별로: 사용자에겐 친절하고 안전한 메시지, 로그엔 상세 컨텍스트(단, 비밀번호·토큰·개인정보 제외).

## 태그

`error-handling` `resilience` `retry` `backoff` `timeout` `circuit-breaker` `fallback` `fail-fast` `error-handling-resilience` `core` `ai-recommended`
