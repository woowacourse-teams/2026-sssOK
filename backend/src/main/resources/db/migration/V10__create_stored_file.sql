-- #48 폴더 담기/꺼내기에서 미디어 존재 확인에 필요한 최소 영속화.
-- 컬럼은 기존 StoredFile 도메인(#25) 필드를 그대로 옮긴 것이고, 실제 업로드 파이프라인(#16)은 이 작업 범위가 아니다.
-- folder_id는 레거시 컬럼이다 — 미디어가 여러 폴더에 동시에 속할 수 있어야 해서 실제 소속은 folder_media로 관리한다.
CREATE TABLE stored_file (
    id                 BIGSERIAL PRIMARY KEY,
    room_id            BIGINT NOT NULL,
    uploader_id        BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    media_type         VARCHAR(10) NOT NULL,
    file_size_bytes    BIGINT NOT NULL,
    storage_key        VARCHAR(255) NOT NULL UNIQUE,
    folder_id          BIGINT,
    status             VARCHAR(20) NOT NULL,
    created_at         TIMESTAMPTZ NOT NULL
);

CREATE INDEX idx_stored_file_room_id ON stored_file (room_id);
