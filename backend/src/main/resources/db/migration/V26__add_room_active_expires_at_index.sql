-- 만료 배치가 "ACTIVE 인데 만료 시각이 지난 방"을 주기적으로 찾는다.
-- 방은 영구 삭제되지 않고 계속 쌓이므로, 대상이 되는 ACTIVE 방만 담는 부분 인덱스를 둔다.
CREATE INDEX idx_room_active_expires_at ON room (expires_at) WHERE status = 'ACTIVE';
