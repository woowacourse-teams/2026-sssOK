-- #48 방 안에서 사진을 묶는 폴더. 1depth 구조라 부모/자식 관계 없이 room_id로만 소속을 표현한다.
CREATE TABLE folder (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT NOT NULL,
    name       VARCHAR(12) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_folder_room_id_name UNIQUE (room_id, name)
);
