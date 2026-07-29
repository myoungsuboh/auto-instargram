-- ═══════════════════════════════════════════════════════════════════════
--  V1: 초기 스키마 — QueueItem / HistoryRecord / SecurityCredential
--
--  근거 문서:
--    1_spack.md  §2 Entities (ENT-01/02/03), §3 Policies (POL-01/02/05)
--    2_ddd.md    §2 Aggregates (AGG-01/02/03) 불변식
--    _workspace/db_schema.json  (Task 1.2 산출물)
--
--  적용 규칙:
--    skills/db/snake_case-db-common-conventions.md
--      · 테이블 복수형 snake_case / 컬럼 단수형 snake_case
--      · PK 는 id, FK 는 참조테이블단수_id
--      · 약어 금지, 공통 감사 컬럼 강제
--    skills/db/soft-delete-soft-delete-audit.md
--      · 물리 삭제 금지 → deleted_at (NULL = 활성)
--      · created/updated/deleted 의 when·who 6개 컬럼 전 테이블 공통
--      · updated_at 은 트리거로 자동 갱신 (애플리케이션 경로 누락 방지)
--      · 유니크 제약은 활성 레코드 한정 (partial unique)
-- ═══════════════════════════════════════════════════════════════════════


-- ── 감사 컬럼 자동 갱신 트리거 함수 ────────────────────────────────────
-- soft-delete-audit 규칙 3: updated_at 을 수동 갱신에 의존하지 않는다.
CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

COMMENT ON FUNCTION set_updated_at() IS
    'UPDATE 시 updated_at 을 자동으로 현재 시각으로 채운다 (soft-delete-audit 규칙 3)';


-- ═══════════════════════════════════════════════════════════════════════
--  queue_items — ENT-02 QueueItem / AGG-02
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE queue_items (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    media_path          varchar(255)    NOT NULL,
    caption             varchar(2200),
    scheduled_at        timestamptz     NOT NULL,
    status              varchar(20)     NOT NULL DEFAULT 'PENDING',
    retry_count         integer         NOT NULL DEFAULT 0,
    last_error_code     varchar(100),
    last_failed_at      timestamptz,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    -- AGG-02 불변식 1: queue status in {PENDING, RUNNING, COMPLETED, FAILED}
    CONSTRAINT ck_queue_items_status
        CHECK (status IN ('PENDING', 'RUNNING', 'COMPLETED', 'FAILED')),

    -- AGG-02 불변식 2: retryCount >= 0
    CONSTRAINT ck_queue_items_retry_count
        CHECK (retry_count >= 0)
);

COMMENT ON TABLE  queue_items                  IS 'ENT-02/AGG-02 예약 발행 큐 항목';
COMMENT ON COLUMN queue_items.id               IS 'API 응답의 queueId';
COMMENT ON COLUMN queue_items.media_path       IS '업로드할 미디어 파일 경로 (len<=255)';
COMMENT ON COLUMN queue_items.caption          IS '게시물 캡션 (len<=2200)';
COMMENT ON COLUMN queue_items.scheduled_at     IS '예약 발행 시각 (UTC)';
COMMENT ON COLUMN queue_items.status           IS '내부 상태 4종. API 응답 시 RUNNING→PENDING, COMPLETED→SUCCESS 로 변환';
COMMENT ON COLUMN queue_items.retry_count      IS '재시도 횟수 (AGG-02 불변식: >= 0)';
COMMENT ON COLUMN queue_items.last_error_code  IS '마지막 실패 원인 코드 (EVT-01 errorCode)';
COMMENT ON COLUMN queue_items.last_failed_at   IS '마지막 실패 시각 (EVT-01 failedAt)';
COMMENT ON COLUMN queue_items.deleted_at       IS 'NULL = 활성. 값이 있으면 논리 삭제됨 (물리 삭제 금지)';

-- 예약 실행 대상 조회 + 목록 정렬 (활성 레코드만)
CREATE INDEX ix_queue_items_status_scheduled_at
    ON queue_items (status, scheduled_at)
    WHERE deleted_at IS NULL;

-- API-02 목록 페이징 (최신순)
CREATE INDEX ix_queue_items_created_at
    ON queue_items (created_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_queue_items_set_updated_at
    BEFORE UPDATE ON queue_items
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ═══════════════════════════════════════════════════════════════════════
--  history_records — ENT-01 HistoryRecord / AGG-01
--  (구 history.json 을 RDBMS 로 이관: POL-02 원자성은 DB 트랜잭션으로 보장)
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE history_records (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    content_hash        varchar(64)     NOT NULL,
    status              varchar(20)     NOT NULL,
    recorded_at         timestamptz     NOT NULL DEFAULT now(),

    -- POL-01: 모든 실패 경로를 누락 없이 이력에 남기기 위한 컬럼들
    queue_item_id       uuid,
    error_code          varchar(100),
    error_message       text,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    -- AGG-01 불변식 2: status in {SUCCESS, FAILED, RETRY}
    CONSTRAINT ck_history_records_status
        CHECK (status IN ('SUCCESS', 'FAILED', 'RETRY')),

    -- ENT-01: contentHash 는 SHA-256 이므로 정확히 64자
    CONSTRAINT ck_history_records_content_hash_length
        CHECK (char_length(content_hash) = 64),

    -- 예약 큐를 거치지 않은 직접 업로드는 NULL 허용
    CONSTRAINT fk_history_records_queue_item
        FOREIGN KEY (queue_item_id) REFERENCES queue_items (id) ON DELETE SET NULL
);

COMMENT ON TABLE  history_records               IS 'ENT-01/AGG-01 게시 이력 및 중복 업로드 방지';
COMMENT ON COLUMN history_records.id            IS 'API 응답의 recordId';
COMMENT ON COLUMN history_records.content_hash  IS 'SHA-256 중복 방지 해시 (정확히 64자)';
COMMENT ON COLUMN history_records.status        IS '내부 상태 3종. API 응답 시 RETRY→FAILED 로 변환';
COMMENT ON COLUMN history_records.recorded_at   IS 'API 응답의 timestamp. (timestamp 는 SQL 예약어이므로 컬럼명은 recorded_at)';
COMMENT ON COLUMN history_records.queue_item_id IS '이 이력을 만든 예약 큐 항목 (직접 업로드면 NULL)';
COMMENT ON COLUMN history_records.error_code    IS 'POL-01 실패 원인 코드';
COMMENT ON COLUMN history_records.error_message IS 'POL-01 실패 상세. POL-05 에 따라 토큰은 마스킹 후 저장';

-- AGG-01 불변식 1: hash value must be unique per media
-- soft-delete-audit 규칙 5: 삭제된 행은 유니크 대상에서 제외 → 재등록 가능
CREATE UNIQUE INDEX ux_history_records_content_hash
    ON history_records (content_hash)
    WHERE deleted_at IS NULL;

-- API-03 startDate/endDate 기간 조회
CREATE INDEX ix_history_records_recorded_at
    ON history_records (recorded_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_history_records_set_updated_at
    BEFORE UPDATE ON history_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ═══════════════════════════════════════════════════════════════════════
--  security_credentials — ENT-03 SecurityCredential / AGG-03
--  POL-05: 토큰은 암호화 저장하며 로그·에러 메시지에 절대 노출하지 않는다.
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE security_credentials (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    token_encrypted     text            NOT NULL,
    issued_at           timestamptz     NOT NULL,
    expires_at          timestamptz     NOT NULL,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    -- AGG-03 불변식 2: expiresAt > issuedAt
    CONSTRAINT ck_security_credentials_expiry
        CHECK (expires_at > issued_at)
);

COMMENT ON TABLE  security_credentials                  IS 'ENT-03/AGG-03 인스타그램 Graph API 액세스 토큰 관리';
COMMENT ON COLUMN security_credentials.id               IS 'API 응답의 credentialId';
COMMENT ON COLUMN security_credentials.token_encrypted  IS 'POL-05 암호화된 액세스 토큰. 평문 저장·로그 출력 금지';
COMMENT ON COLUMN security_credentials.issued_at        IS '발급 시각 (AGG-03 불변식 expires_at > issued_at 검증에 사용)';
COMMENT ON COLUMN security_credentials.expires_at       IS '만료 일시';

-- 만료 임박 자격 증명 조회 (자동 갱신용)
CREATE INDEX ix_security_credentials_expires_at
    ON security_credentials (expires_at)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_security_credentials_set_updated_at
    BEFORE UPDATE ON security_credentials
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
