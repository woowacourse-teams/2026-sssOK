-- 썸네일 워커가 만든 축소본의 스토리지 키.
-- 완성된 URL 이 아니라 키를 저장한다. 도메인이나 서빙 방식이 바뀌어도 데이터를 고치지 않아도 된다.
ALTER TABLE stored_file
    ADD COLUMN thumbnail_key VARCHAR(255);
