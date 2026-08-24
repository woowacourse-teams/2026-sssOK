-- #45 방 이름 최대 길이를 30자에서 12자로 줄인다
-- 이미 12자를 넘는 이름이 있으면 타입 변경이 실패하므로 먼저 잘라낸다.
UPDATE room SET name = left(name, 12) WHERE length(name) > 12;

ALTER TABLE room ALTER COLUMN name TYPE VARCHAR(12);
