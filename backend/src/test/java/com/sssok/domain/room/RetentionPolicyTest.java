package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RetentionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    private final RetentionPolicy retentionPolicy = new RetentionPolicy();

    @Test
    void 보존_기간이_지난_시점을_기준선으로_돌려준다() {
        assertThat(retentionPolicy.threshold(NOW)).isEqualTo(NOW.minus(Duration.ofDays(7)));
    }

    @Test
    void 삭제_시각에_보존_기간을_더해_영구_삭제_예정_시각을_계산한다() {
        Instant deletedAt = NOW.minus(Duration.ofDays(3));

        assertThat(retentionPolicy.purgeAt(deletedAt)).isEqualTo(deletedAt.plus(Duration.ofDays(7)));
    }

    @Test
    void 기준선보다_먼저_삭제된_방만_대상이_된다() {
        Instant threshold = retentionPolicy.threshold(NOW);

        assertThat(NOW.minus(Duration.ofDays(8))).isBefore(threshold);
        assertThat(NOW.minus(Duration.ofDays(6))).isAfter(threshold);
    }

    @Test
    void Room이_계산하는_영구_삭제_예정_시각과_같은_기준을_쓴다() {
        Room room = Room.create(new RoomCode("A3F9K2M7"), new RoomName("우테코 회식"), 1L, NOW);
        room.delete(1L, NOW);

        assertThat(room.purgeAt()).isEqualTo(retentionPolicy.purgeAt(NOW));
    }
}
