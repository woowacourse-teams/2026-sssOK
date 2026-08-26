package com.sssok.domain.room;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class RoomMemberTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");
    private static final Long ROOM_ID = 1L;
    private static final Long OTHER_ROOM_ID = 2L;
    private static final Long MEMBER_ID = 100L;

    private RoomMember joinRoom() {
        return RoomMember.join(ROOM_ID, MEMBER_ID, NOW);
    }

    @Test
    void 참여하면_어느_방에_누가_언제_들어왔는지_남는다() {
        RoomMember roomMember = joinRoom();

        assertThat(roomMember.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(roomMember.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(roomMember.getJoinedAt()).isEqualTo(NOW);
    }

    @Test
    void 아직_저장되지_않은_참여는_id가_없다() {
        assertThat(joinRoom().getId()).isNull();
    }

    @Test
    void 어느_방의_참여인지_판별할_수_있다() {
        RoomMember roomMember = joinRoom();

        assertThat(roomMember.belongsTo(ROOM_ID)).isTrue();
        assertThat(roomMember.belongsTo(OTHER_ROOM_ID)).isFalse();
    }

    @Test
    void 저장소에서_불러온_값으로_복원할_수_있다() {
        Instant joinedAt = NOW.minus(Duration.ofHours(3));

        RoomMember roomMember = RoomMember.reconstruct(7L, ROOM_ID, MEMBER_ID, joinedAt);

        assertThat(roomMember.getId()).isEqualTo(7L);
        assertThat(roomMember.getRoomId()).isEqualTo(ROOM_ID);
        assertThat(roomMember.getMemberId()).isEqualTo(MEMBER_ID);
        assertThat(roomMember.getJoinedAt()).isEqualTo(joinedAt);
    }
}