package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class RoomExpirationTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

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

    @ParameterizedTest
    @ValueSource(ints = {24, 72})
    void 허용된_시간은_기준_시각으로부터_그만큼_뒤로_계산된다(int expiryHours) {
        RoomExpiration expiration = RoomExpiration.from(NOW, expiryHours);

        assertThat(expiration.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(expiryHours)));
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 23, 48, 168, -24})
    void 허용되지_않은_시간이면_예외(int expiryHours) {
        assertThatThrownBy(() -> RoomExpiration.from(NOW, expiryHours))
            .isInstanceOf(InvalidRoomExpirationException.class);
    }

    @Test
    void 기준_시각이_바뀌면_만료_시각도_그만큼_밀린다() {
        RoomExpiration first = RoomExpiration.from(NOW, 24);
        RoomExpiration later = RoomExpiration.from(NOW.plus(Duration.ofHours(10)), 24);

        assertThat(Duration.between(first.expiresAt(), later.expiresAt())).isEqualTo(Duration.ofHours(10));
    }
}