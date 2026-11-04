-- #659 사진 정리 진행·완료는 요청자에게만 보낸다. NULL 이면 방 전체, 값이 있으면 그 회원에게만 전달한다.
-- 재연결 때 놓친 이벤트를 다시 보낼 때도 같은 조건으로 거른다.
ALTER TABLE room_events ADD COLUMN recipient_member_id BIGINT;
