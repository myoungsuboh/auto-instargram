## 0. 명세 충실도 (Lineage Health)
- Aggregate ↔ Story 매핑: 3 / 3 (100%)
- Event ↔ Story 매핑: 3 / 3 (100%)
- **Aggregate invariants 명시**: 3 / 3 (100%) (도메인 규칙)
- **DomainEntity attributes 명시**: 0 / 0 (N/A — 정의된 엔티티 없음)
- **DomainEvent payload 명시**: 3 / 3 (100%) (핸들러 데이터)
- Lineage confidence 분포: direct: 3, inferred: 0, none: 0 (Aggregate + Entity 합산)
- **⚠️ 경고 조건**:
  - DomainEntity attributes 미정의 — 입력 데이터에 `domain_entities`가 비어 있음 (`0개`).

## 1. Domain Overview
- 인스타그램 게시 관리 컨텍스트 (CTX-01): 인스타그램 자동 업로드 이력 및 예약 큐 관리를 담당하는 바운디드 컨텍스트
- 인스타그램 보안 관리 컨텍스트 (CTX-02): 인스타그램 API 토큰 및 인증 정보 관리를 담당하는 바운디드 컨텍스트

## 2. Bounded Context별 상세

### 인스타그램 게시 관리 컨텍스트
- **책임 범위**: 인스타그램 자동 업로드 이력 및 예약 큐 관리를 담당하는 바운디드 컨텍스트

#### Aggregates
- **`HistoryRecord`** (ID: `AGG-01`)
- 책임: 게시 이력 및 중복 업로드 방지를 관리하는 애그리거트 루트
- **PRD 추적성**:
  - `confidence`: direct
  - `related_stories`: 
    - story_01_4: "history.json 및 이력 스키마 도입"
    - story_01_6: "SHA-256 기반 중복 업로드 방지"
- **도메인 규칙 (Invariants)**:
  - `hash value must be unique per media`
  - `status in {SUCCESS, FAILED, RETRY}`
- 소속 Domain Entities: (없음)
- 발행 Domain Events: HistoryRecordCreated

- **`QueueItem`** (ID: `AGG-02`)
- 책임: 예약 큐 항목 및 실행 상태를 관리하는 애그리거트 루트
- **PRD 추적성**:
  - `confidence`: direct
  - `related_stories`: 
    - story_01_2: "예약 실행 및 실패 정책(failed 마킹 및 재시도 상태 조회)을 수행"
    - story_01_1: "예약 큐 툴(queue-tool.mjs add, list, remove)을 설계"
- **도메인 규칙 (Invariants)**:
  - `queue status in {PENDING, RUNNING, COMPLETED, FAILED}`
  - `retryCount >= 0`
- 소속 Domain Entities: (없음)
- 발행 Domain Events: QueueItemFailed

#### Domain Entities
*(이 컨텍스트에 등록된 Domain Entities가 없습니다)*

#### Domain Events
- **`QueueItemFailed`** (ID: `EVT-01`)
- 설명: 예약 큐 작업이 실패하여 failed 상태로 마킹됨
- 발행 Aggregate: QueueItem
- 트리거 Story: Story-01.2
- **Payload 필드**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | queueItemId | uuid | true | | 큐 항목 식별자 |
  | errorCode | string | true | | 에러 코드 |
  | failedAt | datetime | true | | 실패 시각 (UTC) |

- **`HistoryRecordCreated`** (ID: `EVT-02`)
- 설명: 게시 이력이 성공적으로 기록됨
- 발행 Aggregate: HistoryRecord
- 트리거 Story: Story-01.4
- **Payload 필드**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | historyId | uuid | true | | 이력 식별자 |
  | mediaHash | string | true | length: 64 | 미디어 SHA-256 해시값 |
  | occurredAt | datetime | true | | 기록 시각 (UTC) |

---

### 인스타그램 보안 관리 컨텍스트
- **책임 범위**: 인스타그램 API 토큰 및 인증 정보 관리를 담당하는 바운디드 컨텍스트

#### Aggregates
- **`SecurityCredential`** (ID: `AGG-03`)
- 책임: 인스타그램 API 액세스 토큰 및 시크릿 정보를 관리하는 애그리거트 루트
- **PRD 추적성**:
  - `confidence`: direct
  - `related_stories`: 
    - story_06_2: "토큰 자동 갱신 및 안전 저장 (refresh --save)을 수행"
    - story_04_2: "토큰 및 시크릿 출력 보안을 적용"
- **도메인 규칙 (Invariants)**:
  - `token string must be masked in logs`
  - `expiresAt > issuedAt`
- 소속 Domain Entities: (없음)
- 발행 Domain Events: TokenRefreshed

#### Domain Entities
*(이 컨텍스트에 등록된 Domain Entities가 없습니다)*

#### Domain Events
- **`TokenRefreshed`** (ID: `EVT-03`)
- 설명: 인스타그램 액세스 토큰이 자동으로 갱신됨
- 발행 Aggregate: SecurityCredential
- 트리거 Story: Story-06.2
- **Payload 필드**:
  | 필드 | 타입 | 필수 | 제약 | 설명 |
  |------|------|------|------|------|
  | credentialId | uuid | true | | 자격 증명 식별자 |
  | expiresAt | datetime | true | | 만료 시각 (UTC) |
  | refreshedAt | datetime | true | | 갱신 시각 (UTC) |

## 3. 구현 체크리스트

### 인스타그램 게시 관리 컨텍스트 (CTX-01)
- [ ] Repository 인터페이스 (HistoryRecord, QueueItem)
- [ ] Domain Service 클래스
- [ ] Domain Event 발행 메커니즘 (in-process / Kafka 등 — Architecture 문서 참조)
- [ ] Event Handler (이벤트 수신 측이 있는 경우)
- [ ] 도메인 단위 테스트

### 인스타그램 보안 관리 컨텍스트 (CTX-02)
- [ ] Repository 인터페이스 (SecurityCredential)
- [ ] Domain Service 클래스
- [ ] Domain Event 발행 메커니즘 (in-process / Kafka 등 — Architecture 문서 참조)
- [ ] Event Handler (이벤트 수신 측이 있는 경우)
- [ ] 도메인 단위 테스트