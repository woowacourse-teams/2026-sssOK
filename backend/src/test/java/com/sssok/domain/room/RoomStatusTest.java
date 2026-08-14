package com.sssok.domain.room;

import com.sssok.domain.room.exception.IllegalRoomStatusTransitionException;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.room.roomstatus.*;
import org.junit.jupiter.api.Test;

class RoomStatusTest {

    @Test
    void 초기_상태는_ACTIVE다() {
        assertThat(RoomStatus.initial()).isSameAs(ActiveRoomStatus.INSTANCE);
    }

    @Test
    void 같은_이름으로_찾은_상태는_항상_같은_인스턴스다_싱글톤() {
        assertThat(RoomStatus.from("ACTIVE")).isSameAs(ActiveRoomStatus.INSTANCE);
        assertThat(RoomStatus.from("ACTIVE")).isSameAs(RoomStatus.from("ACTIVE"));
        assertThat(RoomStatus.from("EXPIRED")).isSameAs(ExpiredRoomStatus.INSTANCE);
        assertThat(RoomStatus.from("DELETED")).isSameAs(DeletedRoomStatus.INSTANCE);
        assertThat(RoomStatus.from("PURGED")).isSameAs(PurgedRoomStatus.INSTANCE);
    }

    @Test
    void 알수없는_이름이면_예외() {
        assertThatThrownBy(() -> RoomStatus.from("UNKNOWN"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void ACTIVE는_EXPIRED와_DELETED로만_전이할_수_있다() {
        RoomStatus active = ActiveRoomStatus.INSTANCE;

        assertThat(active.toExpired()).isSameAs(ExpiredRoomStatus.INSTANCE);
        assertThat(active.toDeleted()).isSameAs(DeletedRoomStatus.INSTANCE);
        assertThatThrownBy(active::toPurged).isInstanceOf(IllegalRoomStatusTransitionException.class);
    }

    @Test
    void EXPIRED는_DELETED로만_전이할_수_있다() {
        RoomStatus expired = ExpiredRoomStatus.INSTANCE;

        assertThat(expired.toDeleted()).isSameAs(DeletedRoomStatus.INSTANCE);
        assertThatThrownBy(expired::toExpired).isInstanceOf(IllegalRoomStatusTransitionException.class);
        assertThatThrownBy(expired::toPurged).isInstanceOf(IllegalRoomStatusTransitionException.class);
    }

    @Test
    void DELETED는_PURGED로만_전이할_수_있다() {
        RoomStatus deleted = DeletedRoomStatus.INSTANCE;

        assertThat(deleted.toPurged()).isSameAs(PurgedRoomStatus.INSTANCE);
        assertThatThrownBy(deleted::toExpired).isInstanceOf(IllegalRoomStatusTransitionException.class);
        assertThatThrownBy(deleted::toDeleted).isInstanceOf(IllegalRoomStatusTransitionException.class);
    }

    @Test
    void PURGED는_종단_상태라_더_전이할_수_없다() {
        RoomStatus purged = PurgedRoomStatus.INSTANCE;

        assertThatThrownBy(purged::toExpired).isInstanceOf(IllegalRoomStatusTransitionException.class);
        assertThatThrownBy(purged::toDeleted).isInstanceOf(IllegalRoomStatusTransitionException.class);
        assertThatThrownBy(purged::toPurged).isInstanceOf(IllegalRoomStatusTransitionException.class);
    }

    @Test
    void ACTIVE만_입장_및_업로드가_가능하다() {
        assertThat(ActiveRoomStatus.INSTANCE.canEnter()).isTrue();
        assertThat(ExpiredRoomStatus.INSTANCE.canEnter()).isFalse();
        assertThat(DeletedRoomStatus.INSTANCE.canEnter()).isFalse();
        assertThat(PurgedRoomStatus.INSTANCE.canEnter()).isFalse();

        assertThat(ActiveRoomStatus.INSTANCE.canUpload(UploadPolicy.ANYONE, false)).isTrue();
        assertThat(ExpiredRoomStatus.INSTANCE.canUpload(UploadPolicy.ANYONE, false)).isFalse();
        assertThat(DeletedRoomStatus.INSTANCE.canUpload(UploadPolicy.ANYONE, false)).isFalse();
        assertThat(PurgedRoomStatus.INSTANCE.canUpload(UploadPolicy.ANYONE, false)).isFalse();
    }
}
