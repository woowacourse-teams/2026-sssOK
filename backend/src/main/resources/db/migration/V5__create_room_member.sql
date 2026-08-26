-- #45 방 입장: 어떤 회원이 어떤 방에 참여했는지만 남긴다.
CREATE TABLE room_member (
    id        BIGSERIAL PRIMARY KEY,
    room_id   BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL,
    -- 재입장은 새 행을 만들지 않는다
    CONSTRAINT uk_room_member UNIQUE (room_id, member_id)
);
