-- ═══════════════════════════════════════════════════════════════════════
--  V4: 게시 시도 기록 (append-only) — POL-01 의 누락 없는 이행
--
--  ▶ 왜 필요한가 (실측으로 드러난 결함)
--  history_records 는 AGG-01 불변식 1("hash value must be unique per media")을 지키기 위해
--  미디어(콘텐츠 해시)당 1행만 유지한다. 그래서 서로 다른 예약 항목이 같은 영상을 가리키면
--  둘의 결과가 같은 행을 덮어쓴다.
--
--  Phase 3 검증에서 실제로 관측된 수치:
--      실패한 큐 항목 5건  vs  실패 이력 3행  vs  이력에 연결된 큐 2건
--  → 실패 3건이 이력에서 사라졌다. POL-01("모든 실패 경로는 누락 없이 로그 및 이력에
--    기록되어야 함")의 위반이다.
--
--  ▶ 해결
--  skills/db/soft-delete-soft-delete-audit.md 규칙 6:
--    "변경 이력은 별도 테이블: 중요한 데이터의 변경 추적이 필요하면
--     이력 테이블(audit_log 또는 *_history 접미사)을 별도로 운영한다."
--
--  역할을 둘로 나눈다:
--    history_records   — 미디어당 현재 결과 (중복 업로드 방지 + API-03 조회). 유니크 유지.
--    publish_attempts  — 모든 시도를 하나도 빠뜨리지 않고 쌓는다 (append-only). 유니크 없음.
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE publish_attempts (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 어떤 예약/업로드 작업의 시도였는지. 작업이 물리 삭제되면 기록은 남기되 연결만 끊는다.
    queue_item_id       uuid,

    -- 어떤 미디어였는지. history_records 와 대조할 수 있게 같은 해시를 남긴다.
    -- ⚠️ 유니크 제약을 걸지 않는다 — 같은 미디어의 여러 시도가 모두 남아야 한다.
    content_hash        varchar(64)     NOT NULL,

    -- 시도 결과. history_records 와 같은 3종 + 시작 시점을 나타내는 STARTED
    status              varchar(20)     NOT NULL,

    error_code          varchar(100),
    error_message       text,
    attempted_at        timestamptz     NOT NULL DEFAULT now(),

    -- 몇 번째 시도였는지 (queue_items.retry_count 의 그 시점 값)
    attempt_number      integer         NOT NULL DEFAULT 0,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    CONSTRAINT ck_publish_attempts_status
        CHECK (status IN ('STARTED', 'SUCCESS', 'FAILED', 'RETRY')),

    CONSTRAINT ck_publish_attempts_content_hash_length
        CHECK (char_length(content_hash) = 64),

    CONSTRAINT ck_publish_attempts_attempt_number
        CHECK (attempt_number >= 0),

    -- 실패·재시도 기록에는 원인이 있어야 한다 (POL-01 의 추적 가능성)
    CONSTRAINT ck_publish_attempts_failure_has_code
        CHECK (status NOT IN ('FAILED', 'RETRY') OR error_code IS NOT NULL),

    CONSTRAINT fk_publish_attempts_queue_item
        FOREIGN KEY (queue_item_id) REFERENCES queue_items (id) ON DELETE SET NULL
);

COMMENT ON TABLE  publish_attempts                IS 'POL-01 감사 추적: 모든 게시 시도를 누락 없이 쌓는다 (append-only, 유니크 없음)';
COMMENT ON COLUMN publish_attempts.content_hash   IS 'history_records.content_hash 와 대조용. 의도적으로 유니크가 아니다';
COMMENT ON COLUMN publish_attempts.status         IS 'STARTED(시작) / SUCCESS / FAILED / RETRY';
COMMENT ON COLUMN publish_attempts.attempt_number IS '그 시점의 queue_items.retry_count';
COMMENT ON COLUMN publish_attempts.error_message  IS 'POL-05 에 따라 토큰을 마스킹한 뒤 저장';

-- 특정 작업의 시도 내역 추적
CREATE INDEX ix_publish_attempts_queue_item
    ON publish_attempts (queue_item_id, attempted_at DESC)
    WHERE deleted_at IS NULL;

-- 특정 미디어의 시도 내역 추적
CREATE INDEX ix_publish_attempts_content_hash
    ON publish_attempts (content_hash, attempted_at DESC)
    WHERE deleted_at IS NULL;

-- 기간별 감사 조회
CREATE INDEX ix_publish_attempts_attempted_at
    ON publish_attempts (attempted_at DESC)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_publish_attempts_set_updated_at
    BEFORE UPDATE ON publish_attempts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
