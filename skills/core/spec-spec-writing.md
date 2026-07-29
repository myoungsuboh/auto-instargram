---
name: "명세 작성: 무엇을 만들지 합의하기 (Spec Writing)"
description: "코딩 전에 '무엇을' 만들지와 '완료 기준'을 글로 합의하는 스택 중립 가이드. 사용자 스토리·수용 기준(Given-When-Then)·범위/비범위·엣지/에러 케이스를 포함한다. 새 기능을 시작하기 전이나 AI 에이전트에게 작업을 시키기 전 요구사항을 명확히 적어야 할 때 읽는다(구현 단계 계획은 `implementation-plan`). 키워드: spec, requirements, user-story, acceptance-criteria, given-when-then, scope, PRD."
---

# 명세 작성: 무엇을 만들지 합의하기 (Spec Writing)

**ID:** `SKL-SPEC-WRITING`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 코딩 전에 '무엇을' 만들지와 '완료 기준'을 글로 합의하는 스택 중립 가이드. 사용자 스토리·수용 기준(Given-When-Then)·범위/비범위·엣지/에러 케이스를 포함한다. 새 기능을 시작하기 전이나 AI 에이전트에게 작업을 시키기 전 요구사항을 명확히 적어야 할 때 읽는다(구현 단계 계획은 `implementation-plan`). 키워드: spec, requirements, user-story, acceptance-criteria, given-when-then, scope, PRD.

---

## 지시사항 (Instructions)

1. 코딩 전에 '무엇을' 만들지 먼저 합의한다: 작은 명세가 큰 재작업을 막는다.
2. 완료 기준을 검증 가능하게 적는다: 사람·AI 모두 '다 됐다'를 같은 뜻으로 읽어야 한다.
3. 범위(In)와 비범위(Out)를 함께 적는다: 안 하는 것을 못 박아야 스코프가 안 샌다.
4. 정상 흐름뿐 아니라 엣지·에러 케이스를 명세에 포함한다: AI는 적지 않으면 만들지 않는다.
5. 모호어('빠르게·적절히·잘')를 측정 가능한 수치·조건으로 바꾼다.
6. 명세는 '무엇/완료 기준'까지만. '어떻게 단계별로 만들지'는 implementation-plan으로 분리한다.

## 태그

`spec` `requirements` `user-story` `acceptance-criteria` `given-when-then` `scope` `PRD` `spec-writing` `core` `ai-recommended`
