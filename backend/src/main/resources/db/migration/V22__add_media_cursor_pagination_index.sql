-- 방별 최신순 커서 페이지 조회가 정렬 없이 인덱스를 순서대로 읽도록 한다.
-- 실제 목록에 노출되는 상태만 포함해 인덱스 크기와 쓰기 비용을 줄인다.
CREATE INDEX idx_stored_file_room_created_id_visible
    ON stored_file (room_id, created_at DESC, id DESC)
    WHERE status IN ('PROCESSING', 'READY');
