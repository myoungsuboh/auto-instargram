# ✅ IMPLEMENTATION-CHECKLIST — auto-instargram

> 이 목록은 설계 그래프에서 **기계적으로 생성**되었습니다 — 명세 요약 과정의 누락이 없습니다.
>
> **감사 결과: 26/26 ✅** (2026-07-29 감사 완료)
> 모든 항목의 `←구현위치:` 경로는 실제로 파일을 열어 내용까지 대조해 확인했습니다.

## APIs (5)
- [x] `POST /api/v1/queues` — 예약 큐 등록 및 관리 API [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/api/QueueController.java` (`create`, 201 Created, Idempotency-Key 지원) / 서비스 `post/service/QueueService.java#register` / 권한 `config/SecurityConfig.java`
- [x] `GET /api/v1/queues` — 예약 큐 목록 조회 API [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/api/QueueController.java` (`list`, page/limit) / 응답 `post/api/dto/QueueListResponse.java` (items/total)
- [x] `GET /api/v1/history` — 게시 이력 조회 API [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/api/HistoryController.java` / 서비스 `post/service/HistoryService.java#findHistory` (startDate·endDate)
- [x] `POST /api/v1/reels/upload` — 릴스 업로드 파이프라인 실행 API [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/api/ReelsController.java` (201, `status: PROCESSING`) / 검증 `post/service/ReelsUploadService.java` / 4단계 파이프라인 `post/service/InstagramReelsPublisher.java`
- [x] `POST /api/v1/tokens/refresh` — 토큰 교환 및 갱신 API [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/security/api/TokenController.java` (system_admin 전용) / 서비스 `security/service/SecurityCredentialService.java#refreshAccessToken`

## Entities (3)
- [x] Entity `HistoryRecord` (속성 4개)  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/HistoryRecord.java` (recordId→`id`, contentHash, status, timestamp→`recorded_at`) / 테이블 `BE/src/main/resources/db/migration/V1__init_schema.sql#history_records`
- [x] Entity `QueueItem` (속성 4개)  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/QueueItem.java` (queueId→`id`, mediaPath, scheduledAt, status) / 테이블 `V1__init_schema.sql#queue_items`
- [x] Entity `SecurityCredential` (속성 3개)  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/security/domain/SecurityCredential.java` (credentialId→`id`, token→`token_encrypted`, expiresAt) / 테이블 `V1__init_schema.sql#security_credentials`

## Policies (비즈니스 규칙) (4)
- [x] Policy `POL-01` — 모든 실패 경로는 누락 없이 로그 및 이력에 기록되어야 함  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/service/HistoryService.java#recordFailure` (단일 창구, 로그+이력 동시) / **append-only 감사** `post/domain/PublishAttempt.java` + `V4__publish_attempts.sql` / 실패 경로 연결 `post/domain/event/QueueItemFailedListener.java` / 회귀 테스트 `BE/src/test/java/com/autoinstagram/backend/post/Pol01AuditTrailTest.java`
- [x] Policy `POL-02` — history.json 쓰기 작업은 원자적(Atomic) 방식을 보장하여 동시성 충돌이나 파일 손상을 방지해야 함  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/service/HistoryService.java` (`@Transactional` — 파일이 아닌 DB 트랜잭션으로 원자성 보장. 파일 기반의 동시성 위험이 RDBMS 이관 이유) / 스키마 `V1__init_schema.sql#history_records`
- [x] Policy `POL-03` — 목록 조회는 결과 0건일 때 빈 배열을 200 으로 정상 반환한다  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/api/dto/QueueListResponse.java` + `dto/HistoryListResponse.java` / 서비스 `post/service/QueueService.java#list`·`HistoryService.java#findHistory` (예외 대신 빈 목록) / 검증 `BE/src/test/java/com/autoinstagram/backend/post/PostApiIntegrationTest.java` (`emptyListReturns200WithEmptyArray`, `emptyHistoryReturns200WithEmptyArray`)
- [x] Policy `POL-05` — 모든 운영 로그 및 에러 메시지에서 토큰 전문 노출률은 0%여야 함  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/common/util/TokenMasker.java` (mask/scrub) / 저장 암호화 `security/service/TokenCipher.java` (AES-256-GCM) / 엔티티 `security/domain/SecurityCredential.java#toString` (토큰 제외) / 로그 설정 `BE/src/main/resources/application.properties` (SQL 바인딩 로그 off) / 검증 `BE/src/test/java/com/autoinstagram/backend/common/util/TokenMaskerTest.java` + 실서버 로그 전수 검색 0건

## Policies — NFR (성능·비기능 규칙) (1)
> 아래 NFR 항목은 단일 구현 파일이 아니라 **검증 방법/증거**(부하테스트 명령·모니터링 설정·측정 문서 경로)를 마커 뒤에 적으세요 — 파일 경로 강요는 거짓 체크를 유도합니다.
- [x] Policy `POL-04` — API 응답 시간은 3초 이내로 유지되어야 함  ←구현위치(검증 증거):
  **측정 명령** `curl -s -b <cookie> -o /dev/null -w '%{time_total}' http://localhost:8080<경로>`
  **실측값 (2026-07-29)** `/actuator/health` 0.014s · `GET /api/v1/queues?limit=6` 0.013s · `GET /api/v1/history` 0.011s · `POST /api/v1/auth/login` 0.427s (BCrypt strength 12 포함) — 전부 3초 대비 충분
  **설계상 보장 수단** ① 커넥션 대기 상한 `application.properties#spring.datasource.hikari.connection-timeout=2500` ② 목록 `limit` 상한 100 (`post/service/QueueService.java#MAX_PAGE_SIZE`) ③ 외부 호출 타임아웃 `app.instagram.connect-timeout=800ms`+`read-timeout=1700ms` ④ 영상 업로드를 동기 구간에서 분리 (`post/service/PublishWorker.java`, 근거 `docs/decisions/2026-07-29-background-publish-worker.md`)

## Screens (화면) (4)
- [x] Screen `자동 업로드 대시보드` (`/dashboard/upload`) (→ API: API-02, API-01)  ←구현위치: `FE/src/views/UploadDashboardView.vue` (등록 폼 + 목록, 실제 API 호출) / 라우트 `FE/src/router/index.js` / 클라이언트 `FE/src/api/endpoints.js#queues`
- [x] Screen `자동 게시 관리 대시보드` (`/dashboard/posts`) (→ API: API-02)  ←구현위치: `FE/src/views/PostsDashboardView.vue` (실패·재시도 상태 중심, retryCount·lastErrorCode 표시)
- [x] Screen `CLI 인터페이스 및 로그 대시보드` (`/dashboard/history`) (→ API: API-03)  ←구현위치: `FE/src/views/HistoryDashboardView.vue` (기간 필터 startDate·endDate)
- [x] Screen `릴스 업로드 제어 화면` (`/dashboard/reels`) (→ API: API-05)  ←구현위치: `FE/src/views/ReelsDashboardView.vue` — **API-05 토큰 갱신(관리자 전용)과 API-04 릴스 업로드를 모두 제공.** 명세의 화면 기재가 어긋나 양쪽을 다 만족시킴 (근거 `docs/decisions/2026-07-29-reels-screen-both-apis.md`)

## Aggregates (정합성 경계) (3)
- [x] Aggregate `HistoryRecord` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/HistoryRecord.java`
  · 불변식 1 `hash value must be unique per media` → `V1__init_schema.sql#ux_history_records_content_hash` (활성 레코드 한정 partial unique) + 해시 형식 강제(소문자 hex 64자)
  · 불변식 2 `status in {SUCCESS, FAILED, RETRY}` → `post/domain/HistoryStatus.java` + `V1#ck_history_records_status`
  · 검증 `BE/src/test/java/com/autoinstagram/backend/post/domain/HistoryRecordTest.java` (11건)
- [x] Aggregate `QueueItem` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/QueueItem.java`
  · 불변식 1 `queue status in {PENDING, RUNNING, COMPLETED, FAILED}` → `post/domain/QueueStatus.java` + `V1#ck_queue_items_status` (상태 전이 메서드만 공개해 임의 전이 차단)
  · 불변식 2 `retryCount >= 0` → 증가 경로만 제공 + `V1#ck_queue_items_retry_count`
  · 검증 `BE/src/test/java/com/autoinstagram/backend/post/domain/QueueItemTest.java` (10건)
- [x] Aggregate `SecurityCredential` (불변식 2개) [→ Instagram Automation Backend]  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/security/domain/SecurityCredential.java`
  · 불변식 1 `token string must be masked in logs` → `toString()` 에서 토큰 제외 + `common/util/TokenMasker.java`
  · 불변식 2 `expiresAt > issuedAt` → `issue()` 검증 + `V1#ck_security_credentials_expiry`
  · 검증 `BE/src/test/java/com/autoinstagram/backend/security/domain/SecurityCredentialTest.java` (6건)

## Domain Events (3)
- [x] Domain Event `QueueItemFailed`  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/event/QueueItemFailed.java` (payload: queueItemId·errorCode·failedAt) / 발행 `post/service/QueueService.java#markFailed` / 수신 `post/domain/event/QueueItemFailedListener.java` (커밋 후 실행, POL-01 이행)
- [x] Domain Event `HistoryRecordCreated`  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/post/domain/event/HistoryRecordCreated.java` (payload: historyId·mediaHash(64자)·occurredAt) / 발행 `post/service/HistoryService.java#recordSuccess` / 수신 `post/domain/event/HistoryRecordCreatedListener.java`
- [x] Domain Event `TokenRefreshed`  ←구현위치: `BE/src/main/java/com/autoinstagram/backend/security/domain/event/TokenRefreshed.java` (payload: credentialId·expiresAt·refreshedAt — 토큰 값은 POL-05 로 제외) / 발행 `security/service/SecurityCredentialService.java#refreshAccessToken` / 수신 `security/domain/event/TokenRefreshedListener.java`

## Services / Databases (3)
- [x] Service `Mobile Web Frontend` (Vue.js)  ←구현위치: `FE/` (Vue 3.5 + Vite 8, `FE/package.json`) / 진입 `FE/src/main.js` / 라우터 `FE/src/router/index.js` / API 클라이언트 `FE/src/api/client.js` (BE 로 HTTPS/REST, 쿠키 인증) / 디자인 시스템 `FE/src/style.css`
- [x] Service `Instagram Automation Backend` (Spring Boot)  ←구현위치: `BE/` (Spring Boot 4.0.7 + Java 17, `BE/build.gradle`) / 진입 `BE/src/main/java/com/autoinstagram/backend/BackendApplication.java` / 포트 8080 · 헬스체크 `/actuator/health` (`BE/src/main/resources/application.properties`) / CORS `config/CorsConfig.java` / 전송 보안 `config/TransportSecurityConfig.java`
- [x] Database `Primary RDBMS` (PostgreSQL)  ←구현위치: `docker-compose.dev.yml` (postgres:17-alpine, healthcheck) / 스키마 `BE/src/main/resources/db/migration/V1__init_schema.sql`·`V2__auth_accounts.sql`·`V3__idempotency_records.sql`·`V4__publish_attempts.sql` / 접속 설정 `BE/src/main/resources/application.properties` (JDBC, 환경변수 주입) / 설계 근거 `_workspace/db_schema.json`

---

## 감사 요약

**26 / 26 ✅**

| 구분 | 항목 수 | 상태 |
|---|---|---|
| APIs | 5 | ✅ |
| Entities | 3 | ✅ |
| Policies (비즈니스) | 4 | ✅ |
| Policies (NFR) | 1 | ✅ (측정 증거 기재) |
| Screens | 4 | ✅ |
| Aggregates | 3 | ✅ (불변식 6개 전부 도메인+DB 이중 강제) |
| Domain Events | 3 | ✅ (핸들러 3개 포함) |
| Services / Databases | 3 | ✅ |

### 명세 외 추가분 (감사 항목 밖)

체크리스트 26개 항목에는 없지만 서비스가 실제로 동작하려면 필요해 **사용자 확정을 받아** 추가한 것들입니다.

| 추가분 | 위치 | 근거 |
|---|---|---|
| 대시보드 로그인 API 4개 + 화면 | `BE/.../auth/**`, `FE/src/views/LoginView.vue` | [ADR-0005](docs/decisions/2026-07-29-dashboard-login-added.md) — 명세의 API 5개가 모두 인증을 요구하나 로그인 창구가 없었음 |
| 멱등성 장치 | `BE/.../common/idempotency/**`, `V3` | [ADR-0016](docs/decisions/2026-07-29-idempotency-separate-table.md) — Task 3.1 요구사항 |
| 게시 시도 감사 테이블 | `BE/.../post/domain/PublishAttempt.java`, `V4` | [ADR-0014](docs/decisions/2026-07-29-publish-attempts-audit-table.md) — POL-01 을 실제로 달성하기 위해 |
| 예약 실행 워커 | `BE/.../post/service/PublishWorker.java` | [ADR-0013](docs/decisions/2026-07-29-background-publish-worker.md) — `scheduledAt` 을 실행하는 주체가 없으면 예약 발행이 죽은 기능 |

### 검증하지 못한 부분 (정직한 고지)

**인스타그램에 실제로 게시하는 마지막 단계**는 검증하지 못했습니다. 실제 인스타그램 비즈니스 계정과 유효한 액세스 토큰이 필요하기 때문입니다.
코드는 Meta 공개 문서의 4단계 흐름대로 작성했으나(`post/service/InstagramReelsPublisher.java`),
자격 증명이 없는 환경에서 **성공을 흉내내지 않고** 명확한 오류로 중단합니다
(`INSTAGRAM_PUBLISH_ENABLED=false` 기본값) — 이력에 "게시됨"이라는 거짓이 남는 것이 실패보다 나쁘기 때문입니다.

그 외 모든 구간(검증·저장·상태 전이·이력·감사·권한)은 실제 PostgreSQL 과 실제 HTTP 호출로 검증했습니다.
