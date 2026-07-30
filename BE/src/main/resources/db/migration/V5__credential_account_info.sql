-- ═══════════════════════════════════════════════════════════════════════
--  V5: 자격 증명에 인스타그램 계정 정보를 함께 보관한다
--
--  ▶ 왜 필요한가
--  게시할 때 필요한 "인스타그램 계정 번호"를 지금은 사람이 손으로 알아내
--  INSTAGRAM_USER_ID 환경변수에 적어야 한다. 절차는 이렇다:
--      브라우저에 GET https://graph.instagram.com/v25.0/me?fields=user_id,username
--      &access_token=단기토큰 을 넣고, 응답의 user_id 를 복사해 .env 에 붙여넣기
--
--  이 절차는 불편하고, 실제로 틀린 값이 들어갔다(2026-07-30: 숫자여야 하는 자리에
--  23자의 숫자 아닌 값). 그런데 그 값은 토큰만 있으면 API 가 알려주는 값이다.
--
--  ▶ 해결
--  토큰을 교환하는 그 순간 서버가 /me 를 호출해 user_id·username 을 함께 저장한다.
--  사람이 개입할 단계가 사라지고, 틀린 값이 들어갈 여지도 없어진다.
--
--  ▶ 명세와의 관계 (중요)
--  1_spack.md ENT-03 은 속성을 3개(credentialId, token, expiresAt)로 규정한다.
--  아래 두 컬럼은 그보다 많다 — 의도된 이탈이며 근거는 ADR-0024 에 있다.
--  다음 감사에서 "명세 초과 컬럼"으로 판정하지 말 것.
--
--  ▶ NULL 을 허용하는 이유
--  1) 이 마이그레이션 전에 저장된 기존 행에는 값이 없다.
--  2) /me 호출이 실패해도 <b>토큰 교환 자체는 살려야 한다</b>. 교환은 비멱등이라
--     (ADR-0009) 다시 시도할 수 없으므로, 부가 정보 조회 실패로 토큰을 잃으면 안 된다.
--     값이 없으면 게시 시 INSTAGRAM_USER_ID 환경변수로 대체한다.
-- ═══════════════════════════════════════════════════════════════════════

ALTER TABLE security_credentials
    ADD COLUMN ig_user_id  varchar(64),
    ADD COLUMN ig_username varchar(64);

COMMENT ON COLUMN security_credentials.ig_user_id  IS
    '토큰 교환 시 GET /me 로 받아온 인스타그램 계정 번호. 게시 대상 식별에 사용. '
    'NULL 이면 INSTAGRAM_USER_ID 환경변수로 대체 (ADR-0024)';
COMMENT ON COLUMN security_credentials.ig_username IS
    '토큰 교환 시 함께 받아온 계정 이름. 화면에 "어느 계정에 연결됐는지" 보여주는 용도. '
    '식별에는 쓰지 않는다 — 사용자가 언제든 바꿀 수 있는 값이다';

-- 계정 번호로 현재 자격 증명을 찾는 조회는 아직 없다. 인덱스를 미리 만들지 않는다
-- (skills 의 "필요해질 때 만든다" 원칙 — 쓰이지 않는 인덱스는 쓰기 비용만 늘린다).
-- 사용자별 연결(docs/plans/2026-07-30-per-user-instagram-connect.md)을 구현할 때
-- app_account_id 기준 부분 유니크 인덱스와 함께 재검토한다.
