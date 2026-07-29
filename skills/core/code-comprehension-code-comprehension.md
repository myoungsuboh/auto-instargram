---
name: "코드 파악 우선: 고치기 전에 이해하기 (Code Comprehension)"
description: "코드를 바꾸기 전에 관련 기존 코드를 먼저 찾아 읽고, 동작·관례·의존·호출부를 이해한 뒤 재사용하고 주변 스타일에 맞추는 스택 중립 가이드. 익숙하지 않은 코드를 수정·확장하거나, 새 함수/유틸을 추가하기 전, 변경의 영향 범위를 가늠할 때 읽는다. 키워드: code-comprehension, read-before-write, reuse, existing-patterns, call-site, blast-radius, conventions, 기존 코드 파악."
---

# 코드 파악 우선: 고치기 전에 이해하기 (Code Comprehension)

**ID:** `SKL-CODE-COMPREHENSION`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 코드를 바꾸기 전에 관련 기존 코드를 먼저 찾아 읽고, 동작·관례·의존·호출부를 이해한 뒤 재사용하고 주변 스타일에 맞추는 스택 중립 가이드. 익숙하지 않은 코드를 수정·확장하거나, 새 함수/유틸을 추가하기 전, 변경의 영향 범위를 가늠할 때 읽는다. 키워드: code-comprehension, read-before-write, reuse, existing-patterns, call-site, blast-radius, conventions, 기존 코드 파악.

---

## 지시사항 (Instructions)

1. 고치기 전에 읽는다: 바꿀 코드와 그 주변을 먼저 찾아 읽고 현재 동작을 이해한 뒤 손댄다. '대충 이럴 것'이라는 가정으로 편집하지 않는다.
2. 재발명보다 재사용: 같은 일을 하는 기존 함수·유틸·패턴이 있는지 먼저 찾는다. AI는 이미 있는 것을 또 만드는 경향이 강하다.
3. 주변 관례를 따른다: 그 파일·모듈의 명명·구조·에러 처리·스타일을 파악하고 거기에 맞춘다. 내 취향의 이질적 패턴을 끼워넣지 않는다.
4. 영향 범위(blast radius)를 먼저 가늠한다: 바꿀 함수·타입·API를 누가 쓰는지(호출부) 찾아, 변경이 어디까지 번지는지 안 뒤 손댄다.
5. 이름이 아니라 실제로 확인한다: 함수가 이름값을 하는지 구현·데이터 흐름으로 확인한다. 이름·주석은 낡거나 거짓일 수 있다.
6. 모르면 멈추고 좁힌다: 핵심을 이해 못 했으면 추측으로 덮지 말고, 더 읽거나 질문해 좁힌 뒤 진행한다.

## 태그

`code-comprehension` `read-before-write` `reuse` `existing-patterns` `call-site` `blast-radius` `conventions` `기존 코드 파악` `core` `ai-recommended`
