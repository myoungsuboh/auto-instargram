---
name: "리팩토링: 동작 보존 개선 (Refactoring)"
description: "겉보기 동작을 바꾸지 않고 코드 내부 구조만 안전하게 개선하는 스택 중립 가이드. 코드 냄새(중복·긴 함수·강결합)를 정리하거나 기능 추가 전후로 구조를 다듬을 때 읽는다(완료 전 검증은 `verification-before-completion`). 키워드: refactoring, 리팩토링, code-smell, technical-debt, boy-scout-rule, behavior-preserving, big-bang-rewrite."
---

# 리팩토링: 동작 보존 개선 (Refactoring)

**ID:** `SKL-REFACTORING`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 겉보기 동작을 바꾸지 않고 코드 내부 구조만 안전하게 개선하는 스택 중립 가이드. 코드 냄새(중복·긴 함수·강결합)를 정리하거나 기능 추가 전후로 구조를 다듬을 때 읽는다(완료 전 검증은 `verification-before-completion`). 키워드: refactoring, 리팩토링, code-smell, technical-debt, boy-scout-rule, behavior-preserving, big-bang-rewrite.

---

## 지시사항 (Instructions)

1. 리팩토링은 동작 보존이다: 입력/출력·부수효과·공개 API가 그대로여야 한다. 동작이 바뀌면 그건 리팩토링이 아니라 기능 변경이다.
2. 테스트가 안전망이다: 리팩토링 전에 해당 동작을 덮는 테스트가 있는지 확인하고, 없으면 먼저 추가한 뒤 시작한다.
3. 작은 단계로, 자주 검증한다: 한 번에 하나씩 바꾸고 매 단계마다 테스트·빌드로 초록불을 확인한다.
4. 기능 변경과 리팩토링을 같은 커밋에 섞지 않는다: 리뷰·되돌리기·원인 추적이 불가능해진다. 커밋을 분리한다.
5. 보이스카웃 룰: 만진 코드는 처음보다 조금 더 깨끗하게 두고 떠난다. 단, 지금 작업과 무관한 대규모 정리는 별도로 뺀다.
6. 빅뱅 재작성을 지양한다: 통째로 새로 쓰기보다 점진적으로 개선한다. 당장 못 갚을 기술부채는 코드에 숨기지 말고 티켓으로 가시화한다.

## 태그

`refactoring` `리팩토링` `code-smell` `technical-debt` `boy-scout-rule` `behavior-preserving` `big-bang-rewrite` `core` `ai-recommended`
