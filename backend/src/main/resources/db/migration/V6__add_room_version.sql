-- #45 방 설정 변경·삭제가 동시에 들어올 때 마지막 요청이 앞선 변경을 덮어쓰지 않도록
-- 낙관적 락용 버전 컬럼을 둔다. 읽은 시점의 버전과 다르면 UPDATE 가 0행이 되어 충돌을 감지한다.
ALTER TABLE room
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
