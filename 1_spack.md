## 0. 명세 충실도 (Lineage Health)
- API ↔ Story 매핑: 5/5 (100%)
- API ↔ Service 매핑: 5/5 (100%)
- API request_body 명시: 3/5 (60%) (API-02, API-03은 GET 요청으로 본문 없음 자연스러움. POST 요청 3개 모두 request body 필드 명시됨)
- API response_body 명시: 5/5 (100%)
- API error_cases 명시: 5/5 (100%)
- API auth 명세: 5/5 (100%) (모든 API `auth.required=true`)
- Entity attribute 명시: 3/3 (100%)
- Attribute 타입 명시율: 11/11 (100%) (모든 attribute type != "unknown")
- Entity lineage confidence 분포: `direct: 3, inferred: 0, none: 0`

## 1. APIs

### `POST /api/v1/queues`
- **설명**: 자동 업로드 대시보드 및 예약 관리 CLI를 통해 예약 큐에 새로운 게시 작업을 등록한다.
- **구현 서비스**: Instagram Automation Backend
- **구현 Story**: `Story-01.1`
- **경로 파라미터 (Path Params)**: (없음)

- **쿼리 파라미터 (Query Params)**: (없음)

- **요청 본문 (Request body)**: 
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `mediaPath` | string | O | len<=255 | 업로드할 미디어 파일 경로 |
  | `caption` | string | - | len<=2200 | 게시물 캡션 |
  | `scheduledAt` | datetime | O | | 예약 발행 시각 (UTC) |

  ```json
  {"mediaPath": "/path/to/reel.mp4", "caption": "테스트 캡션", "scheduledAt": "2026-06-01T10:00:00Z"}
  ```

- **응답 본문 (Response body)**: **Status**: 201 Created
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `queueId` | uuid | O | | 생성된 예약 식별자 |
  | `status` | enum | O | enum: PENDING|SUCCESS|FAILED | 큐 상태 |
  | `createdAt` | datetime | O | | 등록 시각 (UTC) |

  ```json
  {"queueId": "123e4567-e89b-12d3-a456-426614174000", "status": "PENDING", "createdAt": "2026-05-25T00:00:00Z"}
  ```

- **에러 응답 (Error cases)**:
  | Status | Code | 조건 | 메시지 | PRD 발췌 |
  |--------|------|------|--------|----------|
  | 401 | AUTH_REQUIRED | JWT 누락 또는 만료 | 인증이 필요합니다 | |
  | 403 | FORBIDDEN | 관리자 또는 운영자 권한 없음 | 권한이 없습니다 | |
  | 422 | VALIDATION_ERROR | 미디어 경로 누락 또는 잘못된 시간 형식 | 잘못된 입력값입니다 | |

- **인증/권한 (Authorization)**:
  - `required`: `true` (🔒)
  - `required_roles`: `[system_admin, system_operator]`
  - `ownership_check`: 
    ```
    (없음)
    ```
  - `description`: 시스템 관리자 또는 운영자 권한 필요
- **에러 응답 가이드**: 표 형태로 출력
  | HTTP | 의미 | 발생 조건 |
  | 400 | Bad Request | 요청 본문 검증 실패 |
  | 401 | Unauthorized | JWT 누락/만료 |
  | 403 | Forbidden | 권한 부족 (예: 본인 소유 아닌 리소스) |
  | 404 | Not Found | 리소스 없음 (예: 잘못된 path id) |
  | 422 | Unprocessable | 비즈니스 규칙 위반 |
- **연관 Policy**: (전 시스템 정책 모두 적용)

---

### `GET /api/v1/queues`
- **설명**: 등록된 예약 큐 목록과 실패 상태 및 재시도 상태를 조회한다.
- **구현 서비스**: Instagram Automation Backend
- **구현 Story**: `Story-01.2`
- **경로 파라미터 (Path Params)**: (없음)

- **쿼리 파라미터 (Query Params)**:
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `page` | integer | - | >=0 | 페이지 번호 |
  | `limit` | integer | - | >0 | 페이지당 항목 수 |

- **요청 본문 (Request body)**: (본문 없음)

- **응답 본문 (Response body)**: **Status**: 200 OK
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `items` | array | O | item: object | 예약 큐 목록 |
  | `total` | integer | O | >=0 | 전체 항목 수 |

  ```json
  {"items": [], "total": 0}
  ```

- **에러 응답 (Error cases)**:
  | Status | Code | 조건 | 메시지 | PRD 발췌 |
  |--------|------|------|--------|----------|
  | 401 | AUTH_REQUIRED | 인증 정보 누락 | 인증이 필요합니다 | |

- **인증/권한 (Authorization)**:
  - `required`: `true` (🔒)
  - `required_roles`: `[system_operator, system_admin]`
  - `ownership_check`: 
    ```
    (없음)
    ```
  - `description`: 운영자 및 관리자 조회 가능
- **에러 응답 가이드**: 표 형태로 출력
  | HTTP | 의미 | 발생 조건 |
  | 400 | Bad Request | 요청 본문 검증 실패 |
  | 401 | Unauthorized | JWT 누락/만료 |
  | 403 | Forbidden | 권한 부족 (예: 본인 소유 아닌 리소스) |
  | 404 | Not Found | 리소스 없음 (예: 잘못된 path id) |
  | 422 | Unprocessable | 비즈니스 규칙 위반 |
- **연관 Policy**: (전 시스템 정책 모두 적용)

---

### `GET /api/v1/history`
- **설명**: history.json 기반의 게시 이력 및 통계 정보를 조회한다.
- **구현 서비스**: Instagram Automation Backend
- **구현 Story**: `Story-01.5`
- **경로 파라미터 (Path Params)**: (없음)

- **쿼리 파라미터 (Query Params)**:
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `startDate` | date | - | | 조회 시작일 |
  | `endDate` | date | - | | 조회 종료일 |

- **요청 본문 (Request body)**: (본문 없음)

- **응답 본문 (Response body)**: **Status**: 200 OK
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `history` | array | O | item: object | 이력 목록 |

  ```json
  {"history": []}
  ```

- **에러 응답 (Error cases)**:
  | Status | Code | 조건 | 메시지 | PRD 발췌 |
  |--------|------|------|--------|----------|
  | 401 | AUTH_REQUIRED | 인증 토큰 없음 | 인증이 필요합니다 | |

- **인증/권한 (Authorization)**:
  - `required`: `true` (🔒)
  - `required_roles`: `[system_operator, system_admin]`
  - `ownership_check`: 
    ```
    (없음)
    ```
  - `description`: 운영자 권한 필요
- **에러 응답 가이드**: 표 형태로 출력
  | HTTP | 의미 | 발생 조건 |
  | 400 | Bad Request | 요청 본문 검증 실패 |
  | 401 | Unauthorized | JWT 누락/만료 |
  | 403 | Forbidden | 권한 부족 (예: 본인 소유 아닌 리소스) |
  | 404 | Not Found | 리소스 없음 (예: 잘못된 path id) |
  | 422 | Unprocessable | 비즈니스 규칙 위반 |
- **연관 Policy**: (전 시스템 정책 모두 적용)

---

### `POST /api/v1/reels/upload`
- **설명**: Resumable 바이너리 직접 업로드 및 4단계 파이프라인 처리를 수행한다.
- **구현 서비스**: Instagram Automation Backend
- **구현 Story**: `Story-06.1`
- **경로 파라미터 (Path Params)**: (없음)

- **쿼리 파라미터 (Query Params)**: (없음)

- **요청 본문 (Request body)**: 
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `binaryPath` | string | O | | 로컬 바이너리 파일 경로 |
  | `caption` | string | O | len<=2200 | 릴스 캡션 |

  ```json
  {"binaryPath": "/data/reel.mp4", "caption": "릴스 영상 업로드"}
  ```

- **응답 본문 (Response body)**: **Status**: 201 Created
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `containerId` | uuid | O | | 생성된 컨테이너 식별자 |
  | `status` | string | O | | 업로드 진행 상태 |

  ```json
  {"containerId": "123e4567-e89b-12d3-a456-426614174000", "status": "PROCESSING"}
  ```

- **에러 응답 (Error cases)**:
  | Status | Code | 조건 | 메시지 | PRD 발췌 |
  |--------|------|------|--------|----------|
  | 401 | AUTH_REQUIRED | 인증 누락 | 인증이 필요합니다 | |
  | 422 | VALIDATION_ERROR | 바이너리 검증 실패 또는 한도 초과 | 사전 검증 실패 | 순수 바이너리 파서 기반 로컬 사전 검증 및 게시 한도 확인 |

- **인증/권한 (Authorization)**:
  - `required`: `true` (🔒)
  - `required_roles`: `[system_operator, system_admin]`
  - `ownership_check`: 
    ```
    (없음)
    ```
  - `description`: 시스템 운영자 권한 필요
- **에러 응답 가이드**: 표 형태로 출력
  | HTTP | 의미 | 발생 조건 |
  | 400 | Bad Request | 요청 본문 검증 실패 |
  | 401 | Unauthorized | JWT 누락/만료 |
  | 403 | Forbidden | 권한 부족 (예: 본인 소유 아닌 리소스) |
  | 404 | Not Found | 리소스 없음 (예: 잘못된 path id) |
  | 422 | Unprocessable | 비즈니스 규칙 위반 |
- **연관 Policy**: (전 시스템 정책 모두 적용)

---

### `POST /api/v1/tokens/refresh`
- **설명**: 단기 토큰 교환 및 장기 액세스 토큰 수명주기를 관리하고 자동 갱신한다.
- **구현 서비스**: Instagram Automation Backend
- **구현 Story**: `Story-06.2`
- **경로 파라미터 (Path Params)**: (없음)

- **쿼리 파라미터 (Query Params)**: (없음)

- **요청 본문 (Request body)**: 
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `shortLivedToken` | string | O | | 단기 액세스 토큰 |

  ```json
  {"shortLivedToken": "EAAG..."}
  ```

- **응답 본문 (Response body)**: **Status**: 200 OK
  | 이름 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `accessToken` | string | O | | 갱신된 장기 토큰 |
  | `expiresIn` | integer | O | >0 | 만료 시간 (초) |

  ```json
  {"accessToken": "EAAG...", "expiresIn": 5184000}
  ```

- **에러 응답 (Error cases)**:
  | Status | Code | 조건 | 메시지 | PRD 발췌 |
  |--------|------|------|--------|----------|
  | 401 | AUTH_REQUIRED | 인증 정보 누락 | 인증이 필요합니다 | |
  | 422 | INVALID_TOKEN | 만료되었거나 잘못된 토큰 | 유효하지 않은 토큰입니다 | 단기 토큰 교환 및 에러 안내 |

- **인증/권한 (Authorization)**:
  - `required`: `true` (🔒)
  - `required_roles`: `[system_admin]`
  - `ownership_check`: 
    ```
    (없음)
    ```
  - `description`: 시스템 관리자만 토큰 갱신 가능
- **에러 응답 가이드**: 표 형태로 출력
  | HTTP | 의미 | 발생 조건 |
  | 400 | Bad Request | 요청 본문 검증 실패 |
  | 401 | Unauthorized | JWT 누락/만료 |
  | 403 | Forbidden | 권한 부족 (예: 본인 소유 아닌 리소스) |
  | 404 | Not Found | 리소스 없음 (예: 잘못된 path id) |
  | 422 | Unprocessable | 비즈니스 규칙 위반 |
- **연관 Policy**: (전 시스템 정책 모두 적용)

---

## 2. Entities

### HistoryRecord (ID: ENT-01)
- **설명**: history.json에 저장되는 게시 이력 데이터 모델
- **속성 (Attributes)**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `recordId` | uuid | O | | 이력 고유 식별자 |
  | `contentHash` | string | O | len=64 | SHA-256 중복 방지 해시 |
  | `status` | enum | O | enum: SUCCESS\|FAILED | 게시 결과 상태 |
  | `timestamp` | datetime | O | | 기록 시각 (UTC) |

- **PRD 추적성 (Lineage)**:
  - `confidence`: direct
  - `related_stories`: 
    - story_01_4: "history.json 및 이력 스키마 도입"
- **제약 Policy**: 
  - POL-01: 모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함
  - POL-02: history.json 쓰기 작업은 원자적(Atomic) 방식을 보장하여 동시성 충돌이나 파일 손상을 방지해야 함

### QueueItem (ID: ENT-02)
- **설명**: 예약 발행 큐 항목 데이터 모델
- **속성 (Attributes)**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `queueId` | uuid | O | | 예약 큐 고유 식별자 |
  | `mediaPath` | string | O | | 미디어 파일 경로 |
  | `scheduledAt` | datetime | O | | 예약 발행 시각 |
  | `status` | enum | O | enum: PENDING\|SUCCESS\|FAILED | 큐 처리 상태 |

- **PRD 추적성 (Lineage)**:
  - `confidence`: direct
  - `related_stories`: 
    - story_01_1: "예약 큐 툴(queue-tool.mjs add, list, remove)을 설계"
- **제약 Policy**: 
  (없음)

### SecurityCredential (ID: ENT-03)
- **설명**: 인스타그램 API 액세스 토큰 및 시크릿 정보 관리 모델
- **속성 (Attributes)**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | `credentialId` | uuid | O | | 인증 정보 식별자 |
  | `token` | string | O | | 암호화된 액세스 토큰 |
  | `expiresAt` | datetime | O | | 만료 일시 |

- **PRD 추적성 (Lineage)**:
  - `confidence`: direct
  - `related_stories`: 
    - story_06_2: "토큰 자동 갱신 및 안전 저장 (refresh --save)"
- **제약 Policy**: 
  - POL-05: 모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함

---

## 3. Policies
- **POL-01**: Audit — 모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함 (적용 대상: `ENT-01`)
- **POL-02**: Concurrency — history.json 쓰기 작업은 원자적(Atomic) 방식을 보장하여 동시성 충돌이나 파일 손상을 방지해야 함 (적용 대상: `ENT-01`)
- **POL-03**: EdgeCase — 목록 조회는 결과 0건일 때 빈 배열을 200 으로 정상 반환한다 (적용 대상: 전 시스템)
- **POL-04**: Performance — API 응답 시간은 3초 이내로 유지되어야 함 (적용 대상: 전 시스템)
- **POL-05**: Security — 모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함 (적용 대상: `ENT-03`)

---

## 4. 전 시스템 적용 정책 (요약)
- **POL-03**: 목록 조회는 결과 0건일 때 빈 배열을 200 으로 정상 반환한다
- **POL-04**: API 응답 시간은 3초 이내로 유지되어야 함

---

## 5. Screens (FE 코드 contract)

### 자동 업로드 대시보드 (ID: `SCREEN-01`)
- **경로**: `/dashboard/upload`
- **설명**: 단계별 구현 페이즈 제어 및 예약 큐 관리 화면
- **구현 Story**: `Story-01.1`
- **호출 API**: 
  | API ID | Method | Endpoint |
  |--------|--------|----------|
  | API-02 | GET | `/api/v1/queues` |
  | API-01 | POST | `/api/v1/queues` |

- **다음 화면**: `/dashboard/history`

### 자동 게시 관리 대시보드 (ID: `SCREEN-02`)
- **경로**: `/dashboard/posts`
- **설명**: 예약 관리 및 실패 정책 상태 조회 화면
- **구현 Story**: `Story-01.2`
- **호출 API**: 
  | API ID | Method | Endpoint |
  |--------|--------|----------|
  | API-02 | GET | `/api/v1/queues` |

- **다음 화면**: (없음 — 종착 화면 또는 미명시)

### CLI 인터페이스 및 로그 대시보드 (ID: `SCREEN-03`)
- **경로**: `/dashboard/history`
- **설명**: 게시 이력 및 history.json 조회 화면
- **구현 Story**: `Story-01.4`
- **호출 API**: 
  | API ID | Method | Endpoint |
  |--------|--------|----------|
  | API-03 | GET | `/api/v1/history` |

- **다음 화면**: (없음 — 종착 화면 또는 미명시)

### 릴스 업로드 제어 화면 (ID: `SCREEN-04`)
- **경로**: `/dashboard/reels`
- **설명**: Resumable 바이너리 직접 업로드 및 4단계 파이프라인 처리 화면
- **구현 Story**: `Story-06.1`
- **호출 API**: 
  | API ID | Method | Endpoint |
  |--------|--------|----------|
  | API-05 | POST | `/api/v1/tokens/refresh` |

- **다음 화면**: (없음 — 종착 화면 또는 미명시)

---

## 6. 구현 체크리스트

### `POST /api/v1/queues`
- [ ] 엔드포인트 정의 및 라우팅
- [ ] 요청 검증 (Bean Validation 등)
- [ ] 비즈니스 로직 구현
- [ ] 영속화 (Repository ↔ DB)
- [ ] 인증/인가 처리
- [ ] 에러 응답 매핑 (위 표 기준)
- [ ] 단위/통합 테스트

### `GET /api/v1/queues`
- [ ] 엔드포인트 정의 및 라우팅
- [ ] 요청 검증 (Bean Validation 등)
- [ ] 비즈니스 로직 구현
- [ ] 영속화 (Repository ↔ DB)
- [ ] 인증/인가 처리
- [ ] 에러 응답 매핑 (위 표 기준)
- [ ] 단위/통합 테스트

### `GET /api/v1/history`
- [ ] 엔드포인트 정의 및 라우팅
- [ ] 요청 검증 (Bean Validation 등)
- [ ] 비즈니스 로직 구현
- [ ] 영속화 (Repository ↔ DB)
- [ ] 인증/인가 처리
- [ ] 에러 응답 매핑 (위 표 기준)
- [ ] 단위/통합 테스트

### `POST /api/v1/reels/upload`
- [ ] 엔드포인트 정의 및 라우팅
- [ ] 요청 검증 (Bean Validation 등)
- [ ] 비즈니스 로직 구현
- [ ] 영속화 (Repository ↔ DB)
- [ ] 인증/인가 처리
- [ ] 에러 응답 매핑 (위 표 기준)
- [ ] 단위/통합 테스트

### `POST /api/v1/tokens/refresh`
- [ ] 엔드포인트 정의 및 라우팅
- [ ] 요청 검증 (Bean Validation 등)
- [ ] 비즈니스 로직 구현
- [ ] 영속화 (Repository ↔ DB)
- [ ] 인증/인가 처리
- [ ] 에러 응답 매핑 (위 표 기준)
- [ ] 단위/통합 테스트