package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.ActiveRoomStatus;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.dao.OptimisticLockingFailureException;

// 방 하나가 저장에 실패해도 나머지 만료 처리가 멈추지 않아야 한다.
class ExpireRoomsServiceFailureTest {

    private static final Instant NOW = Instant.parse("2026-10-01T00:00:00Z");

    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final ExpireRoomsService expireRoomsService = new ExpireRoomsService(roomRepository);

    @Test
    void 한_방이_실패해도_나머지는_계속_만료_처리한다() {
        Room conflicted = 방(1L, "23456789");
        Room healthy = 방(2L, "34567892");
        given(roomRepository.findAllExpiredActive(NOW)).willReturn(List.of(conflicted, healthy));
        // 읽은 뒤 방장이 방을 지워 버전이 어긋난 상황
        willThrow(new OptimisticLockingFailureException("버전 불일치")).given(roomRepository).save(conflicted);

        int expired = expireRoomsService.expire(NOW);

        assertThat(expired).isEqualTo(1);
        verify(roomRepository).save(healthy);
    }

    @Test
    void 전부_실패해도_예외를_밖으로_던지지_않는다() {
        given(roomRepository.findAllExpiredActive(NOW)).willReturn(List.of(방(1L, "23456789")));
        willThrow(new OptimisticLockingFailureException("버전 불일치")).given(roomRepository).save(any());

        // 배치가 죽으면 다음 회차까지 아무것도 만료 처리되지 않는다.
        assertThat(expireRoomsService.expire(NOW)).isZero();
    }

    private Room 방(Long id, String code) {
        return Room.reconstruct(id, 0L, new RoomCode(code), new RoomName("우테코 회식"), ActiveRoomStatus.INSTANCE,
            new RoomExpiration(NOW.minus(Duration.ofMinutes(1))), UploadPolicy.ANYONE, 1L,
            NOW.minus(Duration.ofDays(1)), null);
    }
}
