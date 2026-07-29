---
name: "멱등성 (Idempotency) 보장"
description: "중복 요청·재시도에도 같은 결과를 보장하는 멱등성 키 패턴과 구현 표준. 결제·주문·이메일 발송 등 부작용이 있는 POST 엔드포인트를 만들거나, 재시도·중복 요청 처리를 정할 때 읽는다. 키워드: idempotency, idempotent, Idempotency-Key, retry, payment, duplicate, redis."
---

# 멱등성 (Idempotency) 보장

**ID:** `SKL-IDEMPOTENCY`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 중복 요청·재시도에도 같은 결과를 보장하는 멱등성 키 패턴과 구현 표준. 결제·주문·이메일 발송 등 부작용이 있는 POST 엔드포인트를 만들거나, 재시도·중복 요청 처리를 정할 때 읽는다. 키워드: idempotency, idempotent, Idempotency-Key, retry, payment, duplicate, redis.

---

## 지시사항 (Instructions)

1. 결제·주문·이메일 발송 등 부작용이 있는 POST 엔드포인트는 Idempotency-Key 헤더를 지원한다.
2. 동일 키의 첫 요청 결과를 캐시(Redis·DB)에 저장하고, 재요청 시 저장된 결과를 반환한다.
3. 멱등성 키는 클라이언트가 생성한 UUID v4를 사용하고, TTL은 24시간 이상으로 설정한다.
4. 처리 중인 요청의 중복 도달은 409 Conflict로 응답하거나, 완료까지 polling 방식을 제공한다.
5. PUT/DELETE는 URI+파라미터가 동일하면 멱등이므로 별도 키가 불필요하다.

## 태그

`idempotency` `idempotent` `Idempotency-Key` `retry` `payment` `duplicate` `redis` `backEnd` `ai-recommended`
