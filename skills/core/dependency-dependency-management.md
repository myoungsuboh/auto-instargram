---
name: "의존성 관리: 채택 판단 & 버전 위생 (Dependency Management)"
description: "새 서드파티 의존성을 추가할지(비용 대비 가치) 판단하고, 잠금 파일·버전 핀으로 재현성을 지키며, 정기적 소규모 갱신과 미사용 제거로 트리를 건강하게 유지하는 스택 중립 가이드. 라이브러리를 추가·교체·갱신하거나 의존성 매니페스트를 검토할 때 읽는다. 취약점 스캔·자동 패치는 `dependency-scanning`이 담당한다. 키워드: dependency, lockfile, version pinning, transitive, license, supply-chain, hallucinated-package, dead-dependency."
---

# 의존성 관리: 채택 판단 & 버전 위생 (Dependency Management)

**ID:** `SKL-DEPENDENCY-MANAGEMENT`  
**범위(Scope):** AI Recommended  
**우선순위:** High  
**적용 조건:** 새 서드파티 의존성을 추가할지(비용 대비 가치) 판단하고, 잠금 파일·버전 핀으로 재현성을 지키며, 정기적 소규모 갱신과 미사용 제거로 트리를 건강하게 유지하는 스택 중립 가이드. 라이브러리를 추가·교체·갱신하거나 의존성 매니페스트를 검토할 때 읽는다. 취약점 스캔·자동 패치는 `dependency-scanning`이 담당한다. 키워드: dependency, lockfile, version pinning, transitive, license, supply-chain, hallucinated-package, dead-dependency.

---

## 지시사항 (Instructions)

1. 의존성은 비용이다: 추가 전 번들 크기·유지보수·보안 표면·라이선스를 따지고, 표준 라이브러리나 이미 쓰는 의존성으로 해결되는지 먼저 본다.
2. 최소 의존을 유지한다: 한두 함수 때문에 큰 패키지를 들이지 않고, 안 쓰게 된 의존성은 즉시 제거한다.
3. 재현성을 보장한다: 잠금 파일을 항상 커밋하고, 버전은 핀(또는 좁은 범위)으로 고정해 '내 PC에선 됐다'를 없앤다.
4. 정기적으로 조금씩 갱신한다: 작은 갱신을 자주 하고, 몇 년 치를 한 번에 몰아 올리지 않는다.
5. 라이선스를 확인한다: 채택 전 라이선스를 보고, 회사 정책상 위험한 것(예: 강한 카피레프트)은 들이지 않는다.
6. 환각 패키지명을 경계한다: AI가 생성한 설치 명령의 패키지명은 공식 레지스트리에서 실재·정확 여부를 확인한 뒤 설치한다.

## 태그

`dependency` `lockfile` `version pinning` `transitive` `license` `supply-chain` `hallucinated-package` `dead-dependency` `dependency-management` `core` `ai-recommended`
