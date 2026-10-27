ALTER TABLE room_member ADD COLUMN nickname VARCHAR(12);
UPDATE room_member rm SET nickname = m.nickname FROM member m WHERE rm.member_id = m.id;
