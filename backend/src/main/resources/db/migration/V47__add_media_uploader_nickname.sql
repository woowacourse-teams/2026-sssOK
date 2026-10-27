ALTER TABLE stored_file ADD COLUMN uploader_nickname VARCHAR(12);

-- 과거 업로드 당시 이름은 복원할 수 없어 현재 확인 가능한 방 닉네임 또는 기본 이름으로 보완한다.
UPDATE stored_file f
SET uploader_nickname = COALESCE(
    (SELECT rm.nickname FROM room_member rm
     WHERE rm.room_id = f.room_id AND rm.member_id = f.uploader_id),
    (SELECT m.nickname FROM member m WHERE m.id = f.uploader_id)
);
