package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import java.time.Duration;
import java.time.Instant;

// 방 만료 시각을 담는 값 객체.
// 방장은 1일부터 14일까지 하루 단위로 고를 수 있다
public record RoomExpiration(Instant expiresAt) {

    private static final int DEFAULT_DAYS = 1;
    private static final int MIN_DAYS = 1;
    private static final int MAX_DAYS = 14;

    public RoomExpiration {
        if (expiresAt == null) {
            throw new InvalidRoomExpirationException();
        }
    }

    // 방 생성 시 기본 만료 시각 (지금부터 1일 뒤)
    public static RoomExpiration defaultFrom(Instant now) {
        return from(now, DEFAULT_DAYS);
    }

    public static RoomExpiration from(Instant now, int expiryDays) {
        if (expiryDays < MIN_DAYS || expiryDays > MAX_DAYS) {
            throw new InvalidRoomExpirationException();
        }
        return new RoomExpiration(now.plus(Duration.ofDays(expiryDays)));
    }

    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }
}
