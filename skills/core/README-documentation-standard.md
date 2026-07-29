---
name: "문서화 표준: README · ADR · 주석 · 동기화 (Documentation Standard)"
description: "프로젝트 문서를 어디에·무엇을·어떻게 쓸지 규정하는 스택 중립 가이드. README·ADR 양식, 주석 작성 기준, 코드 변경 시 문서 동기화를 다룬다. 새 저장소를 시작하거나, 문서를 추가·정리하거나, 중요한 기술 결정을 기록하거나, 온보딩 자료를 점검할 때 읽는다. 키워드: README, ADR, documentation, comments, docstring, onboarding, doc-sync."
---

# 문서화 표준: README · ADR · 주석 · 동기화 (Documentation Standard)

**ID:** `SKL-DOCUMENTATION-STANDARD`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 프로젝트 문서를 어디에·무엇을·어떻게 쓸지 규정하는 스택 중립 가이드. README·ADR 양식, 주석 작성 기준, 코드 변경 시 문서 동기화를 다룬다. 새 저장소를 시작하거나, 문서를 추가·정리하거나, 중요한 기술 결정을 기록하거나, 온보딩 자료를 점검할 때 읽는다. 키워드: README, ADR, documentation, comments, docstring, onboarding, doc-sync.

---

## 지시사항 (Instructions)

1. 문서는 코드와 함께 변경한다: 동작을 바꾸는 PR은 관련 문서도 같은 PR에서 고친다. '나중에'는 오지 않는다.
2. 틀린 문서 > 무문서의 해악: 낡거나 거짓인 문서는 삭제·수정한다. 자신 없는 내용을 추정으로 적지 않는다.
3. 신규자·AI 에이전트 기준으로 쓴다: 사전 지식 없이 README만 읽고 빌드·실행에 도달할 수 있어야 한다.
4. 주석은 '무엇'이 아니라 '왜': 코드가 말하는 것을 반복하지 말고, 코드가 말 못 하는 의도·배경·트레이드오프를 적는다.
5. 문서 위치를 일관되게: 같은 종류의 문서는 늘 같은 곳에 둔다. 흩어진 문서는 없는 문서다.
6. 중요한 기술 결정은 ADR로 남긴다: 코드만 보면 '왜 이렇게 했는지'를 알 수 없는 선택은 근거와 함께 기록한다.

## 태그

`README` `ADR` `documentation` `comments` `docstring` `onboarding` `doc-sync` `documentation-standard` `core` `ai-recommended`
