---
name: "코드 리뷰 (Code Review)"
description: "코드 리뷰를 요청·수행·응답하는 방식의 스택 중립 표준. 셀프리뷰(디버그 흔적·죽은 코드 제거 등) 체크의 단일 소유 문서이며, 리뷰 관점(정확성·가독성·설계·테스트·보안·일관성)·blocker/nit 구분·건설적 피드백을 규정한다. PR을 올리기 전, 남의 PR을 리뷰할 때, 리뷰 코멘트에 답할 때 읽는다(PR 크기·머지 기준은 `git-workflow`). 키워드: code-review, pull-request, self-review, blocker, nit, feedback, reviewer."
---

# 코드 리뷰 (Code Review)

**ID:** `SKL-CODE-REVIEW`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 코드 리뷰를 요청·수행·응답하는 방식의 스택 중립 표준. 셀프리뷰(디버그 흔적·죽은 코드 제거 등) 체크의 단일 소유 문서이며, 리뷰 관점(정확성·가독성·설계·테스트·보안·일관성)·blocker/nit 구분·건설적 피드백을 규정한다. PR을 올리기 전, 남의 PR을 리뷰할 때, 리뷰 코멘트에 답할 때 읽는다(PR 크기·머지 기준은 `git-workflow`). 키워드: code-review, pull-request, self-review, blocker, nit, feedback, reviewer.

---

## 지시사항 (Instructions)

1. 리뷰는 사람이 아니라 코드를 본다: 피드백은 코드와 동작을 향하고, 관찰·근거·제안을 함께 적는다.
2. PR은 리뷰 가능한 크기로 작게: 한 PR = 한 가지 변경. 작을수록 리뷰가 빠르고 결함이 더 걸린다.
3. 올리기 전 셀프 리뷰를 먼저: diff를 직접 읽고 군더더기·디버그 흔적·빠진 테스트를 작성자가 먼저 거른다.
4. 모든 코멘트에 심각도(blocker/nit) 를 표기: 막는 문제와 취향 제안을 명확히 나눈다.
5. 작성자는 방어하지 않고 근거로 응답: 반영하거나, 왜 그대로 두는지 이유를 적는다.
6. 머지는 승인 + CI 통과 가 모두 충족될 때만.

## 태그

`code-review` `pull-request` `self-review` `blocker` `nit` `feedback` `reviewer` `core` `ai-recommended`
