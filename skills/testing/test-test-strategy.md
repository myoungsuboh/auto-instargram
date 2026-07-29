---
name: "테스트 전략 & 피라미드"
description: "테스트 피라미드(Unit→Integration→E2E) 기반의 균형 잡힌 테스트 전략 표준. 팀의 테스트 작성 방식을 정하거나 새 기능에 어떤 테스트를 얼마나 둘지 결정할 때 읽는다. 키워드: test, describe, it(, expect, mock, assert, beforeEach, afterEach, given, 테스트 피라미드, Given-When-Then."
---

# 테스트 전략 & 피라미드

**ID:** `SKL-TEST-STRATEGY`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 테스트 피라미드(Unit→Integration→E2E) 기반의 균형 잡힌 테스트 전략 표준. 팀의 테스트 작성 방식을 정하거나 새 기능에 어떤 테스트를 얼마나 둘지 결정할 때 읽는다. 키워드: test, describe, it(, expect, mock, assert, beforeEach, afterEach, given, 테스트 피라미드, Given-When-Then.

---

## 지시사항 (Instructions)

1. 테스트 피라미드를 따른다: 단위 테스트(70%)가 가장 많고, 통합 테스트(20%), E2E(10%) 순으로 구성한다.
2. 테스트는 Given-When-Then(Arrange-Act-Assert) 패턴으로 작성해 의도를 명확히 한다.
3. 테스트 이름은 '무엇을 테스트하는지'와 '어떤 조건에서'를 포함하는 명확한 설명문으로 작성한다.
4. 테스트는 서로 독립적이어야 하며, 실행 순서나 다른 테스트의 상태에 의존하지 않는다.
5. 외부 의존성(DB·API·파일시스템)은 단위 테스트에서 Mock·Stub으로 격리하고, 통합 테스트에서는 실제 의존성을 사용한다.

## 태그

`test` `describe` `it(` `expect` `mock` `assert` `beforeEach` `afterEach` `given` `테스트 피라미드` `Given-When-Then` `test-strategy` `testing` `ai-recommended`
