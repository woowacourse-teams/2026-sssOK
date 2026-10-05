package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.ActiveRoomStatus;
import com.sssok.domain.room.roomstatus.DeletedRoomStatus;
import com.sssok.domain.room.roomstatus.ExpiredRoomStatus;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

// Repository + Service 통합 테스트 (H2).
// @Transactional로 테스트마다 롤백한다 — 대상은 상태와 만료 시각만으로 고르기 때문에,
// 다른 테스트가 남긴 ACTIVE 방이 섞이면 처리 개수가 어긋난다.
@SpringBootTest
@ActiveProfiles("test")
@Transactional
class ExpireRoomsServiceTest {

    private static final Long HOST = 1L;

    @Autowired
    ExpireRoomsService expireRoomsService;

    @Autowired
    RoomRepository roomRepository;

    @Test
    void 만료_시각이_지난_ACTIVE_방을_EXPIRED로_바꾼다() {
        Instant now = Instant.now();
        Room room = 방(now.minus(Duration.ofMinutes(1)));

        int expired = expireRoomsService.expire(now);

        assertThat(expired).isEqualTo(1);
        assertThat(저장된_상태(room)).isSameAs(ExpiredRoomStatus.INSTANCE);
    }

    @Test
    void 만료_시각과_같은_시각이면_만료로_본다() {
        Instant now = Instant.parse("2026-01-01T00:00:00Z");
        Room room = 방(now);

        expireRoomsService.expire(now);

        assertThat(저장된_상태(room)).isSameAs(ExpiredRoomStatus.INSTANCE);
    }

    @Test
    void 만료_시각_전인_방은_ACTIVE로_남는다() {
        Instant now = Instant.now();
        Room room = 방(now.plus(Duration.ofMinutes(1)));

        int expired = expireRoomsService.expire(now);

        assertThat(expired).isZero();
        assertThat(저장된_상태(room)).isSameAs(ActiveRoomStatus.INSTANCE);
    }

    @Test
    void 삭제된_방은_만료_시각이_지나도_DELETED로_남는다() {
        Instant now = Instant.now();
        Room room = 방(now.minus(Duration.ofMinutes(1)));
        room.delete(HOST, now);
        Room deleted = roomRepository.save(room);

        int expired = expireRoomsService.expire(now);

        assertThat(expired).isZero();
        assertThat(저장된_상태(deleted)).isSameAs(DeletedRoomStatus.INSTANCE);
    }

    @Test
    void 다시_돌려도_이미_만료된_방은_대상이_아니다() {
        Instant now = Instant.now();
        Room room = 방(now.minus(Duration.ofMinutes(1)));
        expireRoomsService.expire(now);

        int expiredAgain = expireRoomsService.expire(now);

        assertThat(expiredAgain).isZero();
        assertThat(저장된_상태(room)).isSameAs(ExpiredRoomStatus.INSTANCE);
    }

    private Room 방(Instant expiresAt) {
        Instant createdAt = expiresAt.minus(Duration.ofDays(1));
        return roomRepository.save(Room.create(RoomCode.generate(new SecureRandom()), new RoomName("우테코 회식"),
            HOST, createdAt, UploadPolicy.ANYONE, new RoomExpiration(expiresAt)));
    }

    private RoomStatus 저장된_상태(Room room) {
        return roomRepository.findById(room.getId()).orElseThrow().getStatus();
    }
}
