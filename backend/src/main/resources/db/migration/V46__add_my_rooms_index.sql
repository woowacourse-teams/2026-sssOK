CREATE INDEX idx_room_member_member_joined_id
    ON room_member (member_id, joined_at DESC, id DESC);
