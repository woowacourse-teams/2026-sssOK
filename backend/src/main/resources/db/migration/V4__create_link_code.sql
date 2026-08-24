-- #38 연결 코드 발급: 다른 기기로 이어받기 위한 1회용 코드.
-- code는 유일해야 하므로 UNIQUE로 둔다 (6자리 숫자 100만 가지 * 5분 TTL 규모에서는 재사용 가능성이 낮다).
CREATE TABLE link_code (
    id         BIGSERIAL PRIMARY KEY,
    member_id  BIGINT NOT NULL,
    code       VARCHAR(6) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL
);
