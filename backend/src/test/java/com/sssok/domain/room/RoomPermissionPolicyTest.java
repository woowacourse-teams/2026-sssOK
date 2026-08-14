package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoomPermissionPolicyTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long HOST = 1L;
    private static final Long GUEST = 2L;

    private final RoomPermissionPolicy policy = new RoomPermissionPolicy();

    private Room createRoom() {
        return Room.create(RoomCode.generate(new SecureRandom()), new RoomName("우테코 회식"), HOST, NOW);
    }

    @Test
    void 설정_변경은_방장만_가능하다() {
        Room room = createRoom();

        assertThat(policy.canChangeSettings(room, HOST)).isTrue();
        assertThat(policy.canChangeSettings(room, GUEST)).isFalse();
    }

    @Test
    void 방_삭제는_방장만_가능하다() {
        Room room = createRoom();

        assertThat(policy.canDeleteRoom(room, HOST)).isTrue();
        assertThat(policy.canDeleteRoom(room, GUEST)).isFalse();
    }

    @Test
    void 업로드_권한은_Room의_규칙을_그대로_따른다() {
        Room room = createRoom();
        room.updateSettings(HOST, room.getName(), room.getExpiration(), UploadPolicy.HOST_ONLY);

        assertThat(policy.canUploadTo(room, HOST, NOW)).isTrue();
        assertThat(policy.canUploadTo(room, GUEST, NOW)).isFalse();
    }
}
