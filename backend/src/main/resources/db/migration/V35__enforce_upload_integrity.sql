-- #557 업로드 도메인 ↔ JPA 매핑에 대응하는 DB 제약.
-- 기존 위반 데이터는 삭제/보정하지 않는다. 위반이 있으면 이 마이그레이션을 실패시켜 먼저 조사한다.
-- 연결을 먼저 삭제하는 기존 서비스 순서를 유지하며, 암묵적인 연쇄 삭제는 추가하지 않는다.
ALTER TABLE stored_file ADD CONSTRAINT fk_stored_file_room
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE NO ACTION;
ALTER TABLE folder ADD CONSTRAINT fk_folder_room
    FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE NO ACTION;
ALTER TABLE folder_media ADD CONSTRAINT fk_folder_media_folder
    FOREIGN KEY (folder_id) REFERENCES folder(id) ON DELETE NO ACTION;
ALTER TABLE folder_media ADD CONSTRAINT fk_folder_media_media
    FOREIGN KEY (media_id) REFERENCES stored_file(id) ON DELETE NO ACTION;

ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_size CHECK (file_size_bytes > 0);
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_retry CHECK (retry_count >= 0);
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_status
    CHECK (status IN ('RESERVED', 'PROCESSING', 'READY', 'FAILED'));
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_type
    CHECK (media_type IN ('JPEG', 'PNG', 'GIF', 'MP4', 'WEBM', 'MOV'));

-- uploader_id는 회원 삭제 후에도 유지하는 식별자다. FK를 걸면 기존 조회 계약이 바뀐다.
-- stored_file.folder_id는 레거시 컬럼이다. 현재 폴더 소속은 folder_media가 관리한다.
-- storage_key UNIQUE와 folder/ folder_media 복합 UNIQUE는 기존 마이그레이션에 이미 존재한다.
