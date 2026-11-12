-- #808 전체 미디어 목록에 소속 폴더를 붙이는 media_id IN (...) 조회를 지원한다.
-- 기존 UNIQUE(folder_id, media_id)는 폴더 기준 조회와 중복 연결 방지를 위해 유지한다.
-- 반환할 folder_id까지 포함해 가시성 조건이 맞으면 Index Only Scan이 가능하다.
CREATE INDEX idx_folder_media_media_id_folder_id
    ON folder_media (media_id, folder_id);
