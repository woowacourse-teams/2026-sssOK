-- #45 입장 암호: 평문이 아니라 SHA-256(평문 + 방 코드) 해시를 hex 64자로 저장한다.
-- NULL 이면 암호 없이 들어올 수 있는 방이므로, 기존 방들은 그대로 두면 된다.
ALTER TABLE room
    ADD COLUMN entry_password VARCHAR(64);
