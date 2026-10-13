-- #498 방에 만료 기한을 두지 않기로 해 만료 시각 컬럼을 지운다.
-- 만료 시각이 지났는데 저장 상태가 아직 ACTIVE 인 방은, 컬럼이 사라지면 만료 여부를 알 수 없어 다시 열린다.
-- 그래서 컬럼을 지우기 전에 이 방들을 먼저 EXPIRED 로 넘겨 기존 만료 방이 계속 닫혀 있게 한다.
UPDATE room
    SET status = 'EXPIRED', updated_at = now(), version = version + 1
    WHERE status = 'ACTIVE' AND expires_at <= now();

-- 만료 배치가 대상 방을 찾던 부분 인덱스. 배치와 컬럼이 함께 사라지므로 지운다.
DROP INDEX idx_room_active_expires_at;

ALTER TABLE room DROP COLUMN expires_at;
