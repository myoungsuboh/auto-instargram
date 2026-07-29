-- ═══════════════════════════════════════════════════════════════════════
--  V2: 대시보드 로그인 계정 및 갱신 토큰
--
--  ⚠️ 설계 명세(1_spack.md)에 없는 추가 테이블입니다.
--     1_spack.md 의 API 5개는 모두 auth.required=true 이고 required_roles 로
--     [system_admin, system_operator] 를 요구하는데, 정작 로그인 창구가 명세에
--     없어 화면이 인증을 얻을 방법이 없었습니다.
--     사용자 확정 결정(2026-07-29)에 따라 로그인 기능을 추가합니다.
--     결정 근거: docs/decisions/2026-07-29-dashboard-login-added.md
--
--  적용 규칙:
--    skills/security/JWT-authn-authz.md
--      · 규칙 2: Access Token 15분 이하 + Refresh Token 자동 갱신
--                → 갱신 토큰을 저장해 회전(rotation)·폐기(revoke) 가능하게 한다
--      · 규칙 5: 로그인 실패를 계정별로 제한 → failed_login_count / locked_until
--    skills/db/snake_case-db-common-conventions.md (복수형 테이블 / PK id / FK 참조테이블단수_id)
--    skills/db/soft-delete-soft-delete-audit.md (감사 컬럼 6종 / deleted_at / 활성 한정 유니크)
-- ═══════════════════════════════════════════════════════════════════════


-- ═══════════════════════════════════════════════════════════════════════
--  app_accounts — 대시보드 운영자 계정
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE app_accounts (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    username            varchar(100)    NOT NULL,
    -- BCrypt 해시($2a$10$... 60자). 평문 비밀번호는 어디에도 저장하지 않는다.
    password_hash       varchar(100)    NOT NULL,
    role                varchar(30)     NOT NULL,

    -- SKL-AUTHN-AUTHZ 규칙 5: 계정별 로그인 실패 제한
    failed_login_count  integer         NOT NULL DEFAULT 0,
    locked_until        timestamptz,
    last_login_at       timestamptz,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    -- 1_spack.md 의 required_roles [system_admin, system_operator] 를 그대로 반영
    CONSTRAINT ck_app_accounts_role
        CHECK (role IN ('SYSTEM_ADMIN', 'SYSTEM_OPERATOR')),

    CONSTRAINT ck_app_accounts_failed_login_count
        CHECK (failed_login_count >= 0)
);

COMMENT ON TABLE  app_accounts                    IS '대시보드 로그인 계정 (명세 외 추가 — ADR 참조)';
COMMENT ON COLUMN app_accounts.username           IS '로그인 아이디';
COMMENT ON COLUMN app_accounts.password_hash      IS 'BCrypt 해시. 평문 비밀번호 저장 금지';
COMMENT ON COLUMN app_accounts.role               IS 'SYSTEM_ADMIN = 명세의 system_admin, SYSTEM_OPERATOR = system_operator';
COMMENT ON COLUMN app_accounts.failed_login_count IS '연속 로그인 실패 횟수 (성공 시 0 으로 초기화)';
COMMENT ON COLUMN app_accounts.locked_until       IS '이 시각까지 로그인 차단 (SKL-AUTHN-AUTHZ 규칙 5)';

-- soft-delete-audit 규칙 5: 활성 계정 한정 유니크 → 삭제 후 같은 아이디 재사용 가능
CREATE UNIQUE INDEX ux_app_accounts_username
    ON app_accounts (username)
    WHERE deleted_at IS NULL;

CREATE TRIGGER tr_app_accounts_set_updated_at
    BEFORE UPDATE ON app_accounts
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();


-- ═══════════════════════════════════════════════════════════════════════
--  auth_refresh_tokens — 갱신 토큰 (회전 + 폐기 가능하게 저장)
--
--  Access Token 을 15분으로 짧게 두는 대신 갱신 토큰으로 재발급한다.
--  토큰 원문은 저장하지 않고 SHA-256 해시만 저장한다 —
--  DB 가 유출되어도 그것만으로 로그인할 수 없게 한다(POL-05 의 취지와 동일).
-- ═══════════════════════════════════════════════════════════════════════
CREATE TABLE auth_refresh_tokens (
    id                  uuid            PRIMARY KEY DEFAULT gen_random_uuid(),

    app_account_id      uuid            NOT NULL,
    -- 토큰 원문이 아니라 SHA-256 해시(64자 hex)
    token_hash          varchar(64)     NOT NULL,
    expires_at          timestamptz     NOT NULL,
    -- 회전·로그아웃 시 채워진다. NULL = 아직 유효
    revoked_at          timestamptz,

    -- 공통 감사 컬럼 (soft-delete-audit 규칙 2)
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),
    deleted_at          timestamptz,
    created_by          varchar(100),
    updated_by          varchar(100),
    deleted_by          varchar(100),

    CONSTRAINT ck_auth_refresh_tokens_token_hash_length
        CHECK (char_length(token_hash) = 64),

    CONSTRAINT fk_auth_refresh_tokens_app_account
        FOREIGN KEY (app_account_id) REFERENCES app_accounts (id) ON DELETE CASCADE
);

COMMENT ON TABLE  auth_refresh_tokens             IS '갱신 토큰. 원문 대신 SHA-256 해시만 보관';
COMMENT ON COLUMN auth_refresh_tokens.token_hash  IS '갱신 토큰의 SHA-256 (64자 hex). 원문은 쿠키에만 존재';
COMMENT ON COLUMN auth_refresh_tokens.revoked_at  IS 'NULL = 유효. 회전·로그아웃 시 채워짐';

CREATE UNIQUE INDEX ux_auth_refresh_tokens_token_hash
    ON auth_refresh_tokens (token_hash)
    WHERE deleted_at IS NULL;

-- 계정별 유효 토큰 조회 / 로그아웃 시 일괄 폐기
CREATE INDEX ix_auth_refresh_tokens_account
    ON auth_refresh_tokens (app_account_id, expires_at)
    WHERE deleted_at IS NULL AND revoked_at IS NULL;

CREATE TRIGGER tr_auth_refresh_tokens_set_updated_at
    BEFORE UPDATE ON auth_refresh_tokens
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();
