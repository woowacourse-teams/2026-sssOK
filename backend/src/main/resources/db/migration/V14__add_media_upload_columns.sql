-- #76 미디어 업로드: 서명 URL 재발급과 고아 정리에 필요한 값을 stored_file 에 더한다.
-- V12·V13 은 감사 컬럼(#69)이 선점하고 있어 번호를 비워 두고 V14 로 잡았다.

-- 서명 URL 을 마지막으로 내준 시각. 고아 정리 배치(#77)가 이 값을 기준으로 회수한다.
-- created_at 을 갱신하지 않는 이유는 BaseEntity 가 updatable = false 로 잠가서다.
ALTER TABLE stored_file ADD COLUMN reserved_at TIMESTAMPTZ;
UPDATE stored_file SET reserved_at = created_at;
ALTER TABLE stored_file ALTER COLUMN reserved_at SET NOT NULL;

-- 서명 URL 무한 발급을 막는 레이트 리밋 값.
ALTER TABLE stored_file ADD COLUMN retry_count INT NOT NULL DEFAULT 0;

-- 상태값을 업로드 흐름에 맞게 바꾼다 (PENDING/UPLOADING/COMPLETED → RESERVED/PROCESSING/READY).
UPDATE stored_file SET status = 'RESERVED' WHERE status = 'PENDING';
UPDATE stored_file SET status = 'PROCESSING' WHERE status = 'UPLOADING';
UPDATE stored_file SET status = 'READY' WHERE status = 'COMPLETED';

-- 고아 정리 배치가 RESERVED 만 훑으므로 상태와 시각을 함께 건다.
CREATE INDEX idx_stored_file_status_reserved_at ON stored_file (status, reserved_at);
