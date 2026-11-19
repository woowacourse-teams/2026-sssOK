ALTER TABLE stored_file DROP CONSTRAINT ck_stored_file_type;
ALTER TABLE stored_file ADD CONSTRAINT ck_stored_file_type
    CHECK (media_type IN ('JPEG', 'PNG', 'GIF', 'WEBP', 'HEIC', 'HEIF', 'MP4', 'WEBM', 'MOV'));
