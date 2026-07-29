-- ═══════════════════════════════════════════════════════════════════════
--  V3: 멱등성 기록 (Idempotency)
--
--  근거: 00-ORCHESTRATOR Task 3.1 "Ensure idempotency for queue registration"
--        skills/backEnd/idempotency-idempotency.md
--          · 규칙 1: 부작용 있는 POST 는 Idempotency-Key 헤더를 지원한다
--          · 규칙 2: 첫 요청 결과를 저장하고 재요청 시 저장된 결과를 반환한다
--          · 규칙 3: 키는 클라이언트가 만든 UUID v4, TTL 은 24시간 이상
--          · 규칙 4: 처리 중인 요청의 중복 도달은 409 Conflict
--
--  설계 메모 — _workspace/db_schema.json 의 deferred_to_later_phase 는
--  "queue_items 에 멱등성 키 컬럼 추가"로 적어 두었으나, 별도 테이블로 바꿨다.
--  이유: ① 규칙 2 는 "저장된 결과를 반환"하라고 요구하므로 응답 본문을 보관할 곳이 필요하다
--        ② 규칙 3 의 TTL(만료)을 도메인 테이블에 섞으면 예약 큐 데이터가 만료 대상이 되어 버린다
--        ③ POST /api/v1/reels/upload 등 다른 엔드포인트도 같은 장치를 재사용할 수 있다
-- ═══════════════════════════════════════════════════════════════════════

CREATE TABLE idempotency_records (
    id                      uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 규칙 3: 클라이언트가 생성한 UUID v4 (형식은 애플리케이션에서 검증)
    idempotency_key         varchar(100)    NOT NULL,

    -- 같은 키가 다른 엔드포인트에 쓰이는 것을 구분한다
    request_method          varchar(10)     NOT NULL,
    request_path            varchar(200)    NOT NULL,

    -- 요청 본문의 SHA-256. 같은 키로 "다른 내용"을 보내는 것은 클라이언트 버그이거나
    -- 키 재사용 공격이므로 구분해서 거부해야 한다.
    request_fingerprint     varchar(64)     NOT NULL,

    -- IN_PROGRESS: 처리 중 (중복 도달 시 409) / COMPLETED: 완료 (저장된 응답 반환)
    state                   varchar(20)     NOT NULL,

    -- 규칙 2: 재요청 시 그대로 돌려줄 첫 응답
    response_status         integer,
    response_body           text,

    -- 규칙 3: TTL. 24시간 이상 (구현은 48시간)
    expires_at              timestamptz     NOT NULL,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at              timestamptz     NOT NULL DEFAULT now(),
    updated_at              timestamptz     NOT NULL DEFAULT now(),
    deleted_at              timestamptz,
    created_by              varchar(100),
    updated_by              varchar(100),
    deleted_by              varchar(100),

    CONSTRAINT ck_idempotency_records_state
        CHECK (state IN ('IN_PROGRESS', 'COMPLETED')),

    CONSTRAINT ck_idempotency_records_fingerprint_length
        CHECK (char_length(request_fingerprint) = 64),

    -- 완료 상태면 응답이 반드시 있어야 한다 (규칙 2 를 지킬 수 없는 행이 생기지 않게)
    CONSTRAINT ck_idempotency_records_completed_has_response
        CHECK (state <> 'COMPLETED' OR response_status IS NOT NULL)
);

COMMENT ON TABLE  idempotency_records                     IS '멱등성 키별 첫 요청 결과 보관 (skills/backEnd/idempotency-idempotency.md)';
COMMENT ON COLUMN idempotency_records.idempotency_key     IS '클라이언트가 만든 UUID v4 (Idempotency-Key 헤더)';
COMMENT ON COLUMN idempotency_records.request_fingerprint IS '요청 본문 SHA-256. 같은 키 + 다른 본문 = 422 로 거부';
COMMENT ON COLUMN idempotency_records.state               IS 'IN_PROGRESS = 처리 중(중복 도달 시 409) / COMPLETED = 저장된 응답 반환';
COMMENT ON COLUMN idempotency_records.expires_at          IS 'TTL. 이 시각 이후의 같은 키는 새 요청으로 취급';

-- 같은 키 + 같은 엔드포인트는 하나만 존재해야 한다.
-- 동시에 두 요청이 들어오면 두 번째 INSERT 가 이 제약에 걸려 실패하고, 그것이 곧 중복 감지다
-- (애플리케이션 락 없이 DB 가 직렬화해 준다).
CREATE UNIQUE INDEX ux_idempotency_records_key
    ON idempotency_records (idempotency_key, request_method, request_path)
    WHERE deleted_at IS NULL;

-- 만료분 정리용
CREATE INDEX ix_idempotency_records_expires_at
    ON idempotency_records (expires_at)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_idempotency_records_set_updated_at
    BEFORE UPDATE ON idempotency_records
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
