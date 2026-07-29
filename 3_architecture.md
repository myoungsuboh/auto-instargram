## 0. 명세 충실도 (Lineage Health)
- Service ↔ Aggregate 매핑: `1 / 2`
- Service ↔ Story 매핑: `2 / 2`
- **Service deployment 명시**: `1 / 1`
- **Connection auth 명시**: `2 / 2`
- Lineage confidence 분포: `direct: 1, inferred: 2, none: 0` (Service + Database 합산)
- API ↔ Service 매핑: `5` 항목

## 1. System Overview
본 시스템은 인스타그램 자동 업로드 관리 및 대시보드를 위한 아키텍처로, 총 2개의 서비스(Frontend 1개, Backend 1개)와 1개의 RDBMS 데이터베이스로 구성됩니다. 주요 데이터 흐름은 Mobile Web Frontend가 Instagram Automation Backend와 REST API로 통신하고, Backend는 Primary RDBMS를 통해 도메인 데이터를 영속화하는 구조입니다.

## 2. Service Layer

### Mobile Web Frontend (ID: `SVC-01`, type: `Frontend`)
- **Tech Stack**: `Vue.js`
- **역할**: 인스타그램 자동 업로드 관리 및 대시보드 인터페이스
- **책임 Aggregate (owned_aggregates)**: `(Frontend — 서버 Aggregate 를 소유하지 않음. 화면·상태 모델은 SPACK 문서의 Screens 와 이 서비스가 호출하는 API 응답 스키마를 기준으로 구성)`
- **PRD 추적성**:
  - `confidence`: inferred
  - `related_stories`: `- story_01_1: "자동 업로드 대시보드 및 예약 관리 CLI"`.
- **배포 (Deployment)**:
  - `Port`: `0`
  - `Replicas`: `1`
  - `Health check`: `/`
  - `Required env vars`: `(없음)`
  - `Scaling`: `manual`
- **외부 의존성 (External Dependencies)**: `(없음)`

  | 이름 | 종류 | 용도 |
  |------|------|------|

- **CONNECTS_TO (outgoing)**: 
  - Instagram Automation Backend (HTTPS/REST, auth: `bearer`)
- **수신 (incoming)**: `(없음)`

### Instagram Automation Backend (ID: `SVC-02`, type: `Backend API`)
- **Tech Stack**: `Spring Boot`
- **역할**: 인스타그램 릴스 업로드 파이프라인 및 예약 큐 관리 백엔드 서비스
- **책임 Aggregate (owned_aggregates)**: 
  - `HistoryRecord`
  - `QueueItem`
  - `SecurityCredential`
- **PRD 추적성**:
  - `confidence`: direct
  - `related_stories`: 
    - `- story_01_1: "단계별 구현 페이즈 실행 및 예약 큐 툴"`
    - `- story_06_2: "토큰 자동 갱신 및 안전 저장"`
    - `- story_06_1: "Resumable 바이너리 직접 업로드 및 4단계 파이프라인"`
- **배포 (Deployment)**:
  - `Port`: `8080`
  - `Replicas`: `2`
  - `Health check`: `/actuator/health`
  - `Required env vars`:
    - `DATABASE_URL`
    - `JWT_SECRET`
    - `INSTAGRAM_ACCESS_TOKEN`
  - `Scaling`: `auto-cpu`
- **외부 의존성 (External Dependencies)**: 

  | 이름 | 종류 | 용도 |
  |------|------|------|
  | Meta Instagram Graph API | Social Media API | 인스타그램 릴스 콘텐츠 게시 및 토큰 관리 |

- **CONNECTS_TO (outgoing)**: 
  - Primary RDBMS (JDBC/TCP, auth: `basic`)
- **수신 (incoming)**: 
  - Mobile Web Frontend (HTTPS/REST, auth: `bearer`)

## 3. Data Layer

### Primary RDBMS (ID: `DB-01`, type: `Relational Database`)
- **Tech Stack**: `PostgreSQL`
- **역할**: 예약 큐, 게시 이력 및 보안 자격 증명 데이터 저장소
- **PRD 추적성**: 
  - `confidence`: inferred
  - `related_stories`: 
    - `- story_01_4: "history.json 및 이력 스키마 도입"`
- **접근 서비스 (incoming)**: 
  - Instagram Automation Backend (JDBC/TCP)

## 4. Connection Map
| From | To | Protocol | Auth | 설명 |
|---|---|---|---|---|
| Mobile Web Frontend | Instagram Automation Backend | HTTPS/REST | bearer | 대시보드와 백엔드 API 간 통신 |
| Instagram Automation Backend | Primary RDBMS | JDBC/TCP | basic | 도메인 데이터 읽기 및 쓰기 |

## 5. API ↔ Service Mapping
| API | Service | 배치 사유 |
|---|---|---|
| GET /api/v1/history — 게시 이력 조회 API | Instagram Automation Backend | HistoryRecord Aggregate 기반 게시 이력 및 통계 조회 담당 |
| POST /api/v1/reels/upload — 릴스 업로드 파이프라인 실행 API | Instagram Automation Backend | SecurityCredential Aggregate 토큰 교환 및 갱신 담당 |
| POST /api/v1/tokens/refresh — 토큰 교환 및 갱신 API | Instagram Automation Backend | 릴스 업로드 파이프라인 실행 및 관련 비즈니스 로직 처리 담당 |
| POST /api/v1/queues — 예약 큐 등록 및 관리 API | Instagram Automation Backend | QueueItem Aggregate 관리 및 예약 큐 도메인 비즈니스 로직 담당 |
| GET /api/v1/queues — 예약 큐 목록 조회 API | Instagram Automation Backend | QueueItem Aggregate 조회 및 상태 확인 담당 |

## 6. 구현 체크리스트

### Mobile Web Frontend
- [ ] 프로젝트 셋업 (`Vue.js` 환경 구성)
- [ ] 책임 Aggregate 의 도메인 모델 통합 (DDD 문서 참조)
- [ ] 외부 통신 클라이언트 구현 (CONNECTS_TO 기준)
- [ ] 데이터 영속화 (Database 연결)
- [ ] 헬스체크 / 로깅 / 메트릭
- [ ] 컨테이너화 + CI/CD

### Instagram Automation Backend
- [ ] 프로젝트 셋업 (`Spring Boot` 환경 구성)
- [ ] 책임 Aggregate 의 도메인 모델 통합 (DDD 문서 참조)
- [ ] 외부 통신 클라이언트 구현 (CONNECTS_TO 기준)
- [ ] 데이터 영속화 (Database 연결)
- [ ] 헬스체크 / 로깅 / 메트릭
- [ ] 컨테이너화 + CI/CD

### Primary RDBMS
- [ ] 인스턴스 프로비저닝
- [ ] 스키마 마이그레이션
- [ ] 백업/복구 전략