package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import java.time.Duration;
import java.time.Instant;

// 방 만료 시각을 담는 값 객체.
// 기획상 만료 시간은 생성 시 24시간으로 고정되지만, 방장이 설정 변경으로 늘릴 수 있는 여지를 둔다.
public record RoomExpiration(Instant expiresAt) {

    private static final Duration DEFAULT_DURATION = Duration.ofHours(24);

    public RoomExpiration {
        if (expiresAt == null) {
            throw new InvalidRoomExpirationException(null);
        }
    }

    // 방 생성 시 기본 만료 시각 (지금부터 24시간 뒤)
    public static RoomExpiration defaultFrom(Instant now) {
        return new RoomExpiration(now.plus(DEFAULT_DURATION));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
