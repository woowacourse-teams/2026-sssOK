-- #46 방 이벤트 구독(SSE): 실시간 전달과는 별개로, 재연결 시 Last-Event-ID로 놓친 이벤트를
-- 따라잡기 위한 기록이다. id 순번이 SSE 이벤트 id로도 그대로 쓰인다.
CREATE TABLE room_events (
    id         BIGSERIAL PRIMARY KEY,
    room_id    BIGINT      NOT NULL,
    event_type VARCHAR(50) NOT NULL,
    payload    TEXT        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_room_events_room_id_id ON room_events (room_id, id);
