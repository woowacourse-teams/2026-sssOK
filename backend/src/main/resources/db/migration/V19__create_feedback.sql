-- #203 사용자가 남긴 의견.
--
-- room_id·member_id 에 외래키를 걸지 않는다. RoomPurger 가 방 만료 7일 뒤 방·참여자·회원을
-- 지우는데, 참조로 묶여 있으면 그때 의견까지 따라 사라지거나 삭제가 막힌다.
-- 의견은 방보다 오래 살아야 하므로 room_name·nickname 을 작성 시점 값으로 복사해 둔다.
CREATE TABLE feedback (
    id         BIGSERIAL PRIMARY KEY,
    content    VARCHAR(1000) NOT NULL,
    room_id    BIGINT NOT NULL,
    room_name  VARCHAR(255) NOT NULL,
    member_id  BIGINT NOT NULL,
    -- 회원이 이미 정리돼 닉네임을 찾지 못한 경우가 있어 NULL 을 허용한다.
    nickname   VARCHAR(255),
    user_agent VARCHAR(512),
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);

-- 관리자 의견 목록은 최신순 조회다(#205).
CREATE INDEX idx_feedback_created_at_desc ON feedback (created_at DESC, id DESC);

-- 연속 등록 제한이 "이 회원이 최근에 남긴 의견"을 찾는다.
CREATE INDEX idx_feedback_member_id_created_at ON feedback (member_id, created_at DESC);
