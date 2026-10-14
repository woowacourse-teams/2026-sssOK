package com.sssok.domain.room;

import com.sssok.domain.room.exception.RoomHostRequiredException;
import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

import com.sssok.domain.room.roomstatus.ActiveRoomStatus;
import com.sssok.domain.room.roomstatus.DeletedRoomStatus;
import com.sssok.domain.room.roomstatus.ExpiredRoomStatus;
import org.junit.jupiter.api.Test;

class RoomTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long HOST = 1L;
    private static final Long GUEST = 2L;

    private Room createRoom() {
        RoomCode code = RoomCode.generate(new SecureRandom());
        return Room.create(code, new RoomName("우테코 회식"), HOST, NOW);
    }

    private Room expiredRoom() {
        return Room.reconstruct(1L, 0L, RoomCode.generate(new SecureRandom()), new RoomName("지난 회식"),
            ExpiredRoomStatus.INSTANCE, UploadPolicy.ANYONE, HOST, NOW, null);
    }

    @Test
    void 생성_직후엔_ACTIVE_상태이고_업로드_권한은_ANYONE이다() {
        Room room = createRoom();

        assertThat(room.getStatus()).isSameAs(ActiveRoomStatus.INSTANCE);
        assertThat(room.getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
    }

    @Test
    void 방장_여부를_판별할_수_있다() {
        Room room = createRoom();

        assertThat(room.isHost(HOST)).isTrue();
        assertThat(room.isHost(GUEST)).isFalse();
    }

    @Test
    void ACTIVE_상태면_입장할_수_있다() {
        Room room = createRoom();

        assertThat(room.canEnter()).isTrue();
    }

    @Test
    void EXPIRED_상태로_저장된_방은_입장할_수_없다() {
        Room room = expiredRoom();

        assertThat(room.canEnter()).isFalse();
    }

    @Test
    void ANYONE_방은_방장도_게스트도_업로드할_수_있다() {
        Room room = createRoom();

        assertThat(room.canUpload(HOST)).isTrue();
        assertThat(room.canUpload(GUEST)).isTrue();
    }

    @Test
    void HOST_ONLY_방은_방장만_업로드할_수_있다() {
        Room room = createRoom();
        room.updateSettings(HOST, room.getName(), UploadPolicy.HOST_ONLY);

        assertThat(room.canUpload(HOST)).isTrue();
        assertThat(room.canUpload(GUEST)).isFalse();
    }

    @Test
    void EXPIRED_상태로_저장된_방은_업로드_권한과_무관하게_업로드할_수_없다() {
        Room room = expiredRoom();

        assertThat(room.canUpload(HOST)).isFalse();
    }

    @Test
    void EXPIRED_상태로_저장된_방도_방장이_삭제할_수_있다() {
        Room room = expiredRoom();

        room.delete(HOST, NOW);

        assertThat(room.getStatus()).isSameAs(DeletedRoomStatus.INSTANCE);
    }

    @Test
    void 방장만_설정을_변경할_수_있다() {
        Room room = createRoom();
        RoomName newName = new RoomName("2차 회식");

        room.updateSettings(HOST, newName, UploadPolicy.HOST_ONLY);

        assertThat(room.getName()).isEqualTo(newName);
        assertThat(room.getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 방장이_아니면_설정을_변경할_수_없다() {
        Room room = createRoom();

        assertThatThrownBy(() ->
            room.updateSettings(GUEST, room.getName(), UploadPolicy.HOST_ONLY)
        ).isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 방장만_방을_삭제할_수_있다() {
        Room room = createRoom();

        assertThatThrownBy(() -> room.delete(GUEST, NOW))
            .isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 방을_삭제하면_즉시_입장할_수_없고_DELETED_상태가_된다() {
        Room room = createRoom();

        room.delete(HOST, NOW.plus(Duration.ofMinutes(10)));

        assertThat(room.getStatus()).isSameAs(DeletedRoomStatus.INSTANCE);
        assertThat(room.canEnter()).isFalse();
    }
}
