-- 방이 삭제되어도 마지막으로 배정했던 순번은 유효하다.
CREATE TABLE image_thumbnail_room_cursor (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    last_room_id BIGINT NOT NULL
);
INSERT INTO image_thumbnail_room_cursor (id, last_room_id) VALUES (1, 0);
