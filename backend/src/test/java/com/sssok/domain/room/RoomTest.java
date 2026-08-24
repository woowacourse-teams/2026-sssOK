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
import com.sssok.domain.room.roomstatus.PurgedRoomStatus;
import org.junit.jupiter.api.Test;

class RoomTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long HOST = 1L;
    private static final Long GUEST = 2L;

    private Room createRoom() {
        RoomCode code = RoomCode.generate(new SecureRandom());
        return Room.create(code, new RoomName("우테코 회식"), HOST, NOW);
    }

    @Test
    void 생성_직후엔_ACTIVE_상태이고_만료_시간은_24시간_뒤이며_업로드_권한은_ANYONE이다() {
        Room room = createRoom();

        assertThat(room.getStatus()).isSameAs(ActiveRoomStatus.INSTANCE);
        assertThat(room.getExpiration().expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(24)));
        assertThat(room.getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
    }

    @Test
    void 방장_여부를_판별할_수_있다() {
        Room room = createRoom();

        assertThat(room.isHost(HOST)).isTrue();
        assertThat(room.isHost(GUEST)).isFalse();
    }

    @Test
    void ACTIVE_상태이고_만료_전이면_입장할_수_있다() {
        Room room = createRoom();

        assertThat(room.canEnter(NOW.plus(Duration.ofHours(1)))).isTrue();
    }

    @Test
    void 만료_시각이_지나면_상태_전이와_무관하게_입장할_수_없다() {
        Room room = createRoom();

        // 배치가 아직 expire()를 호출하지 않아 상태는 여전히 ACTIVE 이지만,
        // 실제 만료 시각은 지난 상황을 가정한다.
        boolean canEnterAfterExpiry = room.canEnter(NOW.plus(Duration.ofHours(25)));

        assertThat(room.getStatus()).isSameAs(ActiveRoomStatus.INSTANCE);
        assertThat(canEnterAfterExpiry).isFalse();
    }

    @Test
    void ANYONE_방은_방장도_게스트도_업로드할_수_있다() {
        Room room = createRoom();
        Instant soon = NOW.plus(Duration.ofMinutes(1));

        assertThat(room.canUpload(HOST, soon)).isTrue();
        assertThat(room.canUpload(GUEST, soon)).isTrue();
    }

    @Test
    void HOST_ONLY_방은_방장만_업로드할_수_있다() {
        Room room = createRoom();
        Instant soon = NOW.plus(Duration.ofMinutes(1));
        room.updateSettings(HOST, room.getName(), room.getExpiration(), UploadPolicy.HOST_ONLY);

        assertThat(room.canUpload(HOST, soon)).isTrue();
        assertThat(room.canUpload(GUEST, soon)).isFalse();
    }

    @Test
    void 만료된_방은_업로드_권한과_무관하게_업로드할_수_없다() {
        Room room = createRoom();
        Instant afterExpiry = NOW.plus(Duration.ofHours(25));

        assertThat(room.canUpload(HOST, afterExpiry)).isFalse();
    }

    @Test
    void 방장만_설정을_변경할_수_있다() {
        Room room = createRoom();
        RoomName newName = new RoomName("2차 회식");

        room.updateSettings(HOST, newName, room.getExpiration(), UploadPolicy.HOST_ONLY);

        assertThat(room.getName()).isEqualTo(newName);
        assertThat(room.getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 방장이_아니면_설정을_변경할_수_없다() {
        Room room = createRoom();

        assertThatThrownBy(() ->
            room.updateSettings(GUEST, room.getName(), room.getExpiration(), UploadPolicy.HOST_ONLY)
        ).isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 방장은_설정_변경으로_만료_시간을_늘릴_수_있다() {
        Room room = createRoom();
        RoomExpiration extended = new RoomExpiration(NOW.plus(Duration.ofHours(48)));

        room.updateSettings(HOST, room.getName(), extended, room.getUploadPolicy());

        assertThat(room.getExpiration()).isEqualTo(extended);
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
        assertThat(room.canEnter(NOW.plus(Duration.ofMinutes(11)))).isFalse();
    }

    @Test
    void 삭제_후_보존_기간이_지나지_않으면_퍼지_대상이_아니다() {
        Room room = createRoom();
        Instant deletedAt = NOW;
        room.delete(HOST, deletedAt);

        assertThat(room.isPurgeable(deletedAt.plus(Duration.ofDays(6)))).isFalse();
    }

    @Test
    void 삭제_후_보존_기간이_지나면_퍼지_대상이다() {
        Room room = createRoom();
        Instant deletedAt = NOW;
        room.delete(HOST, deletedAt);

        assertThat(room.isPurgeable(deletedAt.plus(Duration.ofDays(7)))).isTrue();
    }

    @Test
    void 삭제되지_않은_방은_퍼지_대상이_아니다() {
        Room room = createRoom();

        assertThat(room.isPurgeable(NOW.plus(Duration.ofDays(365)))).isFalse();
    }

    @Test
    void expire_호출하면_EXPIRED_상태로_전이한다() {
        Room room = createRoom();

        room.expire();

        assertThat(room.getStatus()).isSameAs(ExpiredRoomStatus.INSTANCE);
    }

    @Test
    void purge는_DELETED_상태에서만_가능하다() {
        Room room = createRoom();

        assertThatThrownBy(room::purge).isInstanceOf(IllegalRoomStatusTransitionException.class);

        room.delete(HOST, NOW);
        room.purge();

        assertThat(room.getStatus()).isSameAs(PurgedRoomStatus.INSTANCE);
    }

    @Test
    void 삭제되지_않은_방은_영구_삭제_예정_시각이_없다() {
        Room room = createRoom();

        assertThat(room.purgeAt()).isNull();
    }

    @Test
    void 삭제하면_영구_삭제_예정_시각은_삭제_시각의_보존_기간_뒤다() {
        Room room = createRoom();
        Instant deletedAt = NOW.plus(Duration.ofMinutes(10));

        room.delete(HOST, deletedAt);

        assertThat(room.purgeAt()).isEqualTo(deletedAt.plus(Duration.ofDays(7)));
    }




}
