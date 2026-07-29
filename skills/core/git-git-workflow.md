---
name: "Git 워크플로우: 브랜치 전략 & 협업 (Git Workflow)"
description: "트렁크 기반 브랜치 전략, 작은 PR, 머지 기준(리뷰·CI 통과 후), rebase/merge·충돌 해결, force-push·시크릿 제외 규칙을 정한 스택 중립 협업 가이드. 새 작업 브랜치를 따거나 PR을 올리거나 머지·충돌을 처리할 때 읽는다. 작은 PR·브랜치·머지 기준의 단일 소유 문서다(셀프리뷰는 `code-review`, 완료 증명은 `verification-before-completion`). 키워드: git, branch, trunk-based, pull-request, CI, rebase, merge, force-push, gitignore, conflict, main."
---

# Git 워크플로우: 브랜치 전략 & 협업 (Git Workflow)

**ID:** `SKL-GIT-WORKFLOW`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 트렁크 기반 브랜치 전략, 작은 PR, 머지 기준(리뷰·CI 통과 후), rebase/merge·충돌 해결, force-push·시크릿 제외 규칙을 정한 스택 중립 협업 가이드. 새 작업 브랜치를 따거나 PR을 올리거나 머지·충돌을 처리할 때 읽는다. 작은 PR·브랜치·머지 기준의 단일 소유 문서다(셀프리뷰는 `code-review`, 완료 증명은 `verification-before-completion`). 키워드: git, branch, trunk-based, pull-request, CI, rebase, merge, force-push, gitignore, conflict, main.

---

## 지시사항 (Instructions)

1. 트렁크 기반으로: main은 항상 배포 가능 상태로 두고, 작업은 단기(이상적으로 1~2일) 브랜치에서 하고 빨리 머지한다. 오래 사는 브랜치는 충돌을 키운다.
2. main에 직접 push하지 않는다: 모든 변경은 브랜치 → PR을 거친다. main은 브랜치 보호로 직접 push·force-push를 막는다.
3. PR은 작게: 한 PR은 한 가지 일만. 작은 PR이 리뷰가 빠르고 버그를 덜 숨긴다.
4. 머지 전 게이트는 둘 다 통과: 최소 1인 리뷰 승인 + CI(빌드·테스트·린트) 그린. 둘 중 하나라도 빨간 채로 머지하지 않는다.
5. 공유 브랜치는 히스토리를 덮어쓰지 않는다: 남이 보는 브랜치(main·공동 작업 브랜치)에 force-push 금지. rebase는 내 로컬 브랜치에서만.
6. 시크릿·빌드 산출물은 커밋하지 않는다: .gitignore로 처음부터 제외한다. 한 번 올라간 비밀은 새는 것으로 간주한다.

## 태그

`git` `branch` `trunk-based` `pull-request` `CI` `rebase` `merge` `force-push` `gitignore` `conflict` `main` `git-workflow` `core` `ai-recommended`
