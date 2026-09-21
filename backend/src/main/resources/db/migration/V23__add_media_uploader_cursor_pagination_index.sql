-- 내가 올린 미디어를 최신순으로 조회할 때 방 전체를 훑지 않도록 한다.
-- 목록에 노출되는 상태만 포함해 인덱스 크기와 쓰기 비용을 줄인다.
CREATE INDEX idx_stored_file_room_uploader_created_id_visible
    ON stored_file (room_id, uploader_id, created_at DESC, id DESC)
    WHERE status IN ('PROCESSING', 'READY');
