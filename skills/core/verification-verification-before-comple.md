---
name: "완료 전 검증: 증거 기반 보고 (Verification Before Completion)"
description: "\"완료/수정됨/통과\"라고 주장하기 전에 실제 실행·테스트로 증거를 확보하고 완료 기준(DoD)을 충족했는지 확인하는 스택 중립 가이드. 작업을 끝냈다고 보고하거나 커밋·PR을 만들기 직전에 읽는다. DoD·CI 그린 게이트·완료 증명의 단일 소유 문서다(AI가 추측으로 \"됐다\"고 단언하는 것을 막는다). 키워드: verification, definition-of-done, DoD, evidence, build, test, lint, acceptance-criteria, honest-reporting, no-assumptions."
---

# 완료 전 검증: 증거 기반 보고 (Verification Before Completion)

**ID:** `SKL-VERIFICATION-BEFORE-COMPLE`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** "완료/수정됨/통과"라고 주장하기 전에 실제 실행·테스트로 증거를 확보하고 완료 기준(DoD)을 충족했는지 확인하는 스택 중립 가이드. 작업을 끝냈다고 보고하거나 커밋·PR을 만들기 직전에 읽는다. DoD·CI 그린 게이트·완료 증명의 단일 소유 문서다(AI가 추측으로 "됐다"고 단언하는 것을 막는다). 키워드: verification, definition-of-done, DoD, evidence, build, test, lint, acceptance-criteria, honest-reporting, no-assumptions.

---

## 지시사항 (Instructions)

1. 증거가 먼저, 주장은 나중. 성공 단언 앞에는 항상 그 근거가 되는 실제 명령 출력이 있어야 한다.
2. '완료'는 느낌이 아니라 기준(DoD)이다: 빌드 통과·테스트 통과·lint 통과·수용 기준 충족을 모두 만족해야 완료다.
3. 변경 범위를 전수 확인한다: 내가 건드린 모든 파일·경로·케이스가 실제로 동작하는지 확인하고, 안 본 부분을 '됐을 것'으로 넘기지 않는다.
4. 실패는 실패로 정직하게 보고한다: 부분 성공, 미확인, 막힌 지점을 숨기거나 미화하지 않는다.
5. 추측 금지: '아마 될 것', '보통 이러면 동작함' 같은 표현으로 검증을 대체하지 않는다.
6. 못 돌렸으면 '못 돌렸다'고 말한다: 환경 제약으로 실행이 불가능하면 그 사실과 한계를 명시한다(검증한 척 금지).

## 태그

`verification` `definition-of-done` `DoD` `evidence` `build` `test` `lint` `acceptance-criteria` `honest-reporting` `no-assumptions` `verification-before-completion` `core` `ai-recommended`
