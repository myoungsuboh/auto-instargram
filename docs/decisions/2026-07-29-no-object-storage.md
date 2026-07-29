---
description: S3 호환 오브젝트 스토리지를 추가하지 않고 명세대로 로컬 파일 경로 방식을 유지 — 아키텍처 문서에 저장소 서비스가 없음
tags: [decision]
---

# ADR-0011: 오브젝트 스토리지를 추가하지 않는다 (로컬 파일 경로 유지)

- 기록일: 2026-07-29 14:31
- 상태: 승인됨
- 단계(Origin): dev (execute-dev)
- 관련 spec: [1_spack.md — API-04](../../1_spack.md), [3_architecture.md](../../3_architecture.md)
- 관련 plan: [00-ORCHESTRATOR.md — Phase 3 Task 3.2](../../00-ORCHESTRATOR.md)

## 맥락 (Context)

세 문서가 서로 다른 것을 요구한다:

| 출처 | 요구 |
|---|---|
| [00-ORCHESTRATOR.md](../../00-ORCHESTRATOR.md) Task 3.2 | "Integrate **S3-compatible file storage** ... with secure presigned upload handling" |
| [skills/backEnd/s3-file-storage.md](../../skills/backEnd/s3-file-storage.md) 규칙 1 | "파일을 애플리케이션 서버의 로컬 디스크에 저장하지 않는다: 오브젝트 스토리지를 사용한다" |
| **[3_architecture.md](../../3_architecture.md)** (충돌 시 최종 권위) | 서비스는 Frontend(Vue) + Backend(Spring Boot), 데이터는 PostgreSQL 뿐. **오브젝트 스토리지가 없다.** 외부 의존성은 Meta Instagram Graph API 하나 |
| [1_spack.md](../../1_spack.md) API-04 | `binaryPath` = "**로컬** 바이너리 파일 경로", PRD 발췌 "순수 바이너리 파서 기반 **로컬** 사전 검증" |

명세의 API 표면은 일관되게 경로 기반이다 — API-01 의 `mediaPath` 도, API-04 의 `binaryPath` 도
파일 업로드(multipart)가 아니라 경로 문자열이다. 즉 명세가 그린 그림은
"로컬 파일을 읽어 인스타그램으로 직접 보낸다"이며, 그 흐름에 오브젝트 스토리지가 끼어들 자리가 없다.

`CLAUDE.md` 규칙 1 은 tech stack 을 3_architecture.md 에서 가져오라고 규정한다.

## 결정 (Decision)

오브젝트 스토리지를 추가하지 않는다. 지정된 디렉터리(`MEDIA_BASE_DIR`)의 파일을 읽어 사용하며,
경로 안전성은 {@code MediaPathValidator} 가 담당한다. **사용자 확정 결정**(2026-07-29).

## 고려한 대안 (Alternatives)

| 대안 | 장점 | 단점 | 채택 여부 |
|------|------|------|-----------|
| 로컬 디렉터리 (아키텍처 문서 기준) | 명세의 `binaryPath`(로컬 경로)와 정확히 일치. 추가 서비스 없음 → 비개발자도 실행 가능(Principle 10). 파일을 우리가 보관하지 않으므로 file-storage 규칙 1 의 대상이 아니다 | 영상을 그 폴더에 두는 것은 사용자가 직접 해야 한다(웹 UI 로 드래그 업로드 불가) | **채택** |
| MinIO(S3 호환) 컨테이너 추가 | Task 3.2 와 file-storage 규칙을 문자 그대로 이행. 화면에서 직접 업로드 가능 | 3_architecture.md 에 없는 서비스를 발명하는 셈(규칙 1 위반). 컨테이너 2개 운용으로 실행 부담 증가. presign 엔드포인트 등 명세에 없는 API 추가 필요 | 기각 |
| 로컬 저장 + 교체 가능한 추상화 계층 | 나중에 S3 로 바꾸기 쉬움 | 당장 쓰지 않는 인터페이스를 미리 만드는 것(투기적 일반화). 구현체가 하나뿐인 추상화는 이해 비용만 늘린다 | 기각 |

## 근거 (Rationale)

`skills/backEnd/s3-file-storage.md` 규칙 1 의 취지는 "**업로드받은 파일을** 앱 서버 디스크에 쌓지 말라"다
(디스크 고갈·확장 불가·유실 위험). 이 설계는 파일을 업로드받아 **보관하지 않는다** —
이미 존재하는 로컬 파일을 읽어 인스타그램으로 스트리밍하고 끝낸다. 저장 책임이 없으므로 규칙의 적용 대상이 아니다.

반면 같은 스킬의 규칙 5(UUID 재명명)·규칙 6(경로 탐색 차단)은 **적용 대상이다** —
사용자 입력이 파일 경로가 되기 때문이다. 그래서 `MediaPathValidator` 로 규칙 6 을 구현했다.

Task 3.2 를 그대로 따르면 3_architecture.md 에 없는 서비스를 추가하게 되는데,
`CLAUDE.md` 규칙 1 이 tech stack 의 권위를 architecture 문서에 두었으므로 그쪽을 따랐다.
명세 이탈이므로 임의 판단하지 않고 사용자에게 세 안을 제시해 확정받았다.

## 영향 (Consequences)

- 긍정: 컨테이너 1개(PostgreSQL)만으로 전체 시스템이 돈다. 명세의 API 계약이 그대로 유지된다.
- 트레이드오프/비용:
  - **영상 파일을 `BE/storage/media/` 에 직접 넣어야 한다.** 웹 화면에서 파일을 골라 올릴 수 없다
    (Phase 4 화면은 경로를 입력받는 형태가 된다). README 에 이 절차를 명시해야 한다.
  - 여러 인스턴스로 확장할 때 각 인스턴스가 같은 파일을 볼 수 있어야 한다(공유 볼륨 필요).
    3_architecture.md 의 `Replicas: 2` 에서는 이 제약이 실재한다.
- 후속 제약:
  - 경로는 반드시 `MediaPathValidator` 를 거친다. 우회 경로를 만들면 서버의 임의 파일을 읽히게 된다.
  - 나중에 오브젝트 스토리지를 도입한다면 이 ADR 을 대체하는 새 결정이 필요하다.
