package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;

// 방 하나가 실패해도 나머지 정리가 멈추지 않아야 한다.
class PurgeRoomServiceFailureTest {

    private static final Instant NOW = Instant.parse("2026-08-13T00:00:00Z");

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomPurger roomPurger = mock(RoomPurger.class);

    private final PurgeRoomService purgeRoomService = new PurgeRoomService(roomRepository, roomPurger);

    @Test
    void 한_방이_실패해도_나머지는_계속_지운다() {
        Room failing = room(1L);
        Room healthy = room(2L);
        given(roomRepository.findAllPurgeTargets(ArgumentMatchers.any())).willReturn(List.of(failing, healthy));
        willThrow(new IllegalStateException("스토리지 오류")).given(roomPurger).purge(failing);

        int purged = purgeRoomService.purgeAll(NOW);

        assertThat(purged).isEqualTo(1);
        verify(roomPurger, times(1)).purge(healthy);
    }

    @Test
    void 전부_실패해도_예외를_밖으로_던지지_않는다() {
        given(roomRepository.findAllPurgeTargets(ArgumentMatchers.any())).willReturn(List.of(room(1L)));
        willThrow(new IllegalStateException("스토리지 오류")).given(roomPurger).purge(ArgumentMatchers.any());

        // 배치가 죽으면 다음 회차까지 아무것도 정리되지 않는다.
        assertThat(purgeRoomService.purgeAll(NOW)).isZero();
    }

    private Room room(Long id) {
        Instant deletedAt = NOW.minus(Duration.ofDays(10));
        return Room.reconstruct(id, 0L, RoomCode.generate(new java.security.SecureRandom()),
            new RoomName("지난 회식"), RoomStatus.from("DELETED"), new RoomExpiration(deletedAt),
            UploadPolicy.ANYONE, 1L, deletedAt.minus(Duration.ofDays(1)), deletedAt);
    }
}
