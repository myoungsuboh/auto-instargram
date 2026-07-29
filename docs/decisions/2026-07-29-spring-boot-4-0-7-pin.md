---
description: Spring Boot 를 Initializr 기본값 4.1.0 대신 4.0.7 로 핀 (지식 커버리지 확보 목적, AI 자체 판단)
tags: [decision]
---

# ADR-0002: Spring Boot 버전을 4.0.7 로 핀

- 기록일: 2026-07-29 11:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [3_architecture.md — §2 SVC-02 Tech Stack](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 1 Task 1.1](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

[3_architecture.md](../../3_architecture.md) 는 SVC-02 의 Tech Stack 을 `Spring Boot` 로만 지정하고 **버전은 명시하지 않는다.**
Spring Initializr 메타데이터를 조회하니 선택 가능한 것은 `4.1.1.BUILD-SNAPSHOT`, `4.1.0.RELEASE`(기본값), `4.0.8.BUILD-SNAPSHOT`, `4.0.7.RELEASE` 뿐이었고
Spring Boot 3.x 는 더 이상 제공되지 않았다. 즉 "Boot 3 로 간다"는 선택지가 애초에 없었다.
설치된 JDK 는 17 이고, Boot 4 의 baseline 도 Java 17 이라 요구사항은 충족된다.

## 결정 (Decision)

`bootVersion=4.0.7` 로 고정해 스캐폴딩한다 (Initializr 기본값 4.1.0 을 쓰지 않는다).

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 4.0.7.RELEASE | 4.0 계열의 성숙한 패치. 에이전트 지식 커버리지 안쪽이라 관용구·API 오사용 위험이 낮음. Java 17 지원 | 최신 기능 미포함. 4.1 로 올릴 때 별도 마이그레이션 필요 | **채택** |
| 4.1.0.RELEASE (Initializr 기본값) | 최신 안정판. 지원 기간이 더 김 | 에이전트 지식 커버리지 밖(2026-05 이후 릴리스)이라 변경점을 모른 채 코드를 쓸 위험. 첫 스캐폴딩에서 컴파일·설정 오류를 디버깅할 비용이 큼 | 기각 |
| 4.x BUILD-SNAPSHOT | 최신 수정 반영 | 스냅샷은 재현성이 없고 언제든 깨질 수 있음. 인수 산출물에 부적합 | 기각 |
| Boot 3.5.x 를 build.gradle 에 수동 핀 | 학습 데이터가 가장 두꺼운 세대 | Initializr 가 제공하지 않아 Gradle 래퍼를 수작업 구성해야 함. 이미 지원 축소된 세대로 역행 | 기각 |

## 근거 (Rationale)

버전이 명세에 없으므로 "조용한 기본값"을 쓰지 않고 근거 있는 선택을 해야 했다.
결정 기준은 **첫 빌드가 실제로 통과할 확률**이었다. 4.1.0 은 에이전트 지식 커버리지 밖이라
Boot 4.1 고유의 변경점을 모른 채 코드를 작성해 디버깅 루프(ORCHESTRATOR 상 최대 3회)를 소진할 위험이 컸다.
4.0.7 은 Boot 4 세대의 구조 변경(아래 참조)을 이미 포함하면서도 검증된 패치판이다.
실제로 `./gradlew compileJava` · `assemble` 이 첫 시도에 BUILD SUCCESSFUL 로 통과했고, `bootRun` 에서 Flyway 11.14.1 까지 정상 동작을 확인했다.

## 영향 (Consequences)

- 긍정: 첫 빌드 통과. Boot 4 세대의 스타터 재편을 실측으로 확인했다 —
  `spring-boot-starter-web` → **`spring-boot-starter-webmvc`**, Flyway 전용 스타터 **`spring-boot-starter-flyway`** 신설,
  통합 `spring-boot-starter-test` 대신 **모듈별 test 스타터**(`...-webmvc-test`, `...-data-jpa-test`, `...-security-test` 등).
  이 이름들을 모르고 Boot 3 관용구로 작성하면 의존성 해석부터 실패한다.
- 트레이드오프/비용: 4.1 의 신기능을 쓰지 못한다. 4.0 계열 지원 종료 시점에 업그레이드 작업이 필요하다.
- 후속 제약:
  - Phase 2 이후 모든 백엔드 코드는 Boot 4 / Spring Security 7 / Jakarta EE 관용구로 작성한다 (Boot 3 예제 복붙 금지).
  - 테스트 의존성을 추가할 때 `spring-boot-starter-test` 를 찾지 말고 모듈별 `-test` 스타터를 쓴다.
  - Gradle 래퍼는 9.5.1 로 고정되어 있다 (Boot 4 요구사항).
