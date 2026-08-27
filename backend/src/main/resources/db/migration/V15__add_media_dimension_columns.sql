-- #76 완료 등록: 썸네일·최적화 워커가 채울 자리를 미리 만든다.
-- 등록 시점에는 알 수 없어 비어 있고, 워커가 처리한 뒤 채운다.
ALTER TABLE stored_file ADD COLUMN width INT;
ALTER TABLE stored_file ADD COLUMN height INT;
ALTER TABLE stored_file ADD COLUMN duration_seconds INT;
