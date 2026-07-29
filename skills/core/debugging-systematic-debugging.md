---
name: "체계적 디버깅 (Systematic Debugging)"
description: "버그·테스트 실패·예기치 못한 동작을 추측이 아니라 재현→근거→가설 검증으로 좁혀 근본 원인을 잡는 스택 중립 디버깅 절차. 원인이 불분명한 버그를 만나거나, 고쳤는데 또 터지거나, 무엇을 고쳐야 할지 막막할 때, 고치기 전에 읽는다. 키워드: debugging, root-cause, minimal-reproduction, git-bisect, regression-test, hypothesis, troubleshooting."
---

# 체계적 디버깅 (Systematic Debugging)

**ID:** `SKL-SYSTEMATIC-DEBUGGING`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 버그·테스트 실패·예기치 못한 동작을 추측이 아니라 재현→근거→가설 검증으로 좁혀 근본 원인을 잡는 스택 중립 디버깅 절차. 원인이 불분명한 버그를 만나거나, 고쳤는데 또 터지거나, 무엇을 고쳐야 할지 막막할 때, 고치기 전에 읽는다. 키워드: debugging, root-cause, minimal-reproduction, git-bisect, regression-test, hypothesis, troubleshooting.

---

## 지시사항 (Instructions)

1. 추측으로 고치지 않는다: 먼저 재현한다. 100% 재현되는 최소 케이스가 없으면 아직 고칠 단계가 아니다.
2. 증상이 아니라 근본 원인을 추적한다. 에러 메시지를 지우는 게 아니라 왜 그 상태가 됐는지를 찾는다.
3. 한 번에 하나씩: 가설을 하나 세우고, 그것만 검증하고, 변경도 한 번에 하나만 한다.
4. 머리로 단정하지 말고 근거로 좁힌다: 로그·관측·이분 탐색으로 사실을 확인한다.
5. 고치면 끝이 아니다: 같은 버그를 다시 잡아낼 회귀 테스트를 남긴다.
6. 막히면 더 작게 쪼갠다: 문제 범위를 절반씩 줄여 원인 구간을 가둔다.

## 태그

`debugging` `root-cause` `minimal-reproduction` `git-bisect` `regression-test` `hypothesis` `troubleshooting` `systematic-debugging` `core` `ai-recommended`
