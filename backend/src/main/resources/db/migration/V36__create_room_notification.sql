-- 브라우저 Web Push 구독. 브라우저(기기)마다 endpoint 가 하나라 endpoint 로 유일하게 식별한다.
-- 같은 브라우저에서 다른 계정으로 다시 등록하면 member_id 만 바꿔 끼운다.
CREATE TABLE push_subscription (
    id BIGSERIAL PRIMARY KEY,
    member_id BIGINT NOT NULL,
    endpoint VARCHAR(1024) NOT NULL,
    p256dh VARCHAR(128) NOT NULL,
    auth VARCHAR(64) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_push_subscription_endpoint UNIQUE (endpoint),
    CONSTRAINT fk_push_subscription_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE NO ACTION
);
CREATE INDEX idx_push_subscription_member ON push_subscription(member_id);

-- 방 참여자별 알림 설정. 행이 없으면 켜진 것으로 본다(입장만 해도 알림을 받는다).
CREATE TABLE room_notification_setting (
    room_id BIGINT NOT NULL,
    member_id BIGINT NOT NULL,
    enabled BOOLEAN NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (room_id, member_id),
    CONSTRAINT fk_room_notification_setting_room FOREIGN KEY (room_id) REFERENCES room(id) ON DELETE NO ACTION,
    CONSTRAINT fk_room_notification_setting_member FOREIGN KEY (member_id) REFERENCES member(id) ON DELETE NO ACTION
);
