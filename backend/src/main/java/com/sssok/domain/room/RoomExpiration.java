package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import java.time.Duration;
import java.time.Instant;
import java.util.Set;

// 방 만료 시각을 담는 값 객체.
// 방장은 24시간과 72시간 중에서 고를 수 있다
public record RoomExpiration(Instant expiresAt) {

    private static final int DEFAULT_HOURS = 24;
    private static final Set<Integer> ALLOWED_HOURS = Set.of(24, 72);

    public RoomExpiration {
        if (expiresAt == null) {
            throw new InvalidRoomExpirationException(null);
        }
    }

    // 방 생성 시 기본 만료 시각 (지금부터 24시간 뒤)
    public static RoomExpiration defaultFrom(Instant now) {
        return from(now, DEFAULT_HOURS);
    }

    public static RoomExpiration from(Instant now, int expiryHours) {
        if (!ALLOWED_HOURS.contains(expiryHours)) {
            throw new InvalidRoomExpirationException(expiryHours + "시간");
        }
        return new RoomExpiration(now.plus(Duration.ofHours(expiryHours)));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
