package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoomExpirationTest {

    @Test
    void 기본_만료_시각은_생성_시점으로부터_24시간_뒤다() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");

        RoomExpiration expiration = RoomExpiration.defaultFrom(now);

        assertThat(expiration.expiresAt()).isEqualTo(now.plus(Duration.ofHours(24)));
    }

    @Test
    void 만료_시각_이전이면_만료가_아니다() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        RoomExpiration expiration = RoomExpiration.defaultFrom(now);

        assertThat(expiration.isExpired(now.plus(Duration.ofHours(23)))).isFalse();
    }

    @Test
    void 만료_시각과_같거나_지나면_만료다() {
        Instant now = Instant.parse("2026-08-13T00:00:00Z");
        RoomExpiration expiration = RoomExpiration.defaultFrom(now);

        assertThat(expiration.isExpired(now.plus(Duration.ofHours(24)))).isTrue();
        assertThat(expiration.isExpired(now.plus(Duration.ofHours(25)))).isTrue();
    }

    @Test
    void 만료_시각이_null이면_예외() {
        assertThatThrownBy(() -> new RoomExpiration(null))
            .isInstanceOf(InvalidRoomExpirationException.class);
    }
}
