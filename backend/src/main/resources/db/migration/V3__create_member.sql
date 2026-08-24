-- #38 익명 인증: 닉네임 기반 전역 회원(계정) 테이블.
-- 특정 방에 속하지 않는다 — id(userId)가 방장·업로더 판정의 유일한 기준이 된다.
CREATE TABLE member (
    id         BIGSERIAL PRIMARY KEY,
    nickname   VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);
