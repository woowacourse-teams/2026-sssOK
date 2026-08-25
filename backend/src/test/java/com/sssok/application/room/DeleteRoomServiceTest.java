package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomAlreadyDeletedException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import com.sssok.domain.room.roomstatus.DeletedRoomStatus;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2)
@SpringBootTest
@ActiveProfiles("test")
class DeleteRoomServiceTest {

    @Autowired
    DeleteRoomService deleteRoomService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    private Long HOST;
    private Long GUEST;

    @BeforeEach
    void setUp() {
        HOST = anonymousAuthService.authenticate("가현").userId();
        GUEST = anonymousAuthService.authenticate("민수").userId();
    }

    private Room createRoom() {
        return createRoomService.create(HOST, "우테코 회식").room();
    }

    @Test
    void 방장이_삭제하면_삭제_시각과_영구_삭제_예정_시각을_받는다() {
        Room room = createRoom();

        DeleteRoomResult result = deleteRoomService.delete(room.getId(), HOST);

        assertThat(result.deletedAt()).isCloseTo(Instant.now(), within(1, ChronoUnit.MINUTES));
        assertThat(result.purgeAt()).isEqualTo(result.deletedAt().plus(Duration.ofDays(7)));
    }

    @Test
    void 삭제해도_행이_사라지지_않고_DELETED_상태로_남는다() {
        Room room = createRoom();

        deleteRoomService.delete(room.getId(), HOST);

        Room reloaded = roomRepository.findByCode(room.getCode()).orElseThrow();
        assertThat(reloaded.getStatus()).isSameAs(DeletedRoomStatus.INSTANCE);
        assertThat(reloaded.getDeletedAt()).isNotNull();
    }

    @Test
    void 삭제하면_즉시_입장할_수_없다() {
        Room room = createRoom();

        deleteRoomService.delete(room.getId(), HOST);

        Room reloaded = roomRepository.findByCode(room.getCode()).orElseThrow();
        assertThat(reloaded.canEnter(Instant.now())).isFalse();
    }

    @Test
    void 방장이_아니면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> deleteRoomService.delete(room.getId(), GUEST))
            .isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> deleteRoomService.delete(-1L, HOST))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 이미_삭제된_방을_다시_삭제하면_예외() {
        Room room = createRoom();
        deleteRoomService.delete(room.getId(), HOST);

        assertThatThrownBy(() -> deleteRoomService.delete(room.getId(), HOST))
            .isInstanceOf(RoomAlreadyDeletedException.class);
    }

    @Test
    void 만료된_방도_방장이_삭제할_수_있다() {
        Room expired = roomRepository.save(expiredRoom());

        DeleteRoomResult result = deleteRoomService.delete(expired.getId(), HOST);

        assertThat(result.deletedAt()).isNotNull();
        assertThat(roomRepository.findById(expired.getId()).orElseThrow().getStatus())
            .isSameAs(DeletedRoomStatus.INSTANCE);
    }

    @Test
    void 만료된_방을_삭제하면_만료_시각_기준으로_영구_삭제_시각이_잡힌다() {
        Room expired = roomRepository.save(expiredRoom());
        Instant expiresAt = expired.getExpiration().expiresAt();

        DeleteRoomResult result = deleteRoomService.delete(expired.getId(), HOST);

        assertThat(result.purgeAt()).isEqualTo(expiresAt.plus(Duration.ofDays(7)));
        assertThat(result.purgeAt()).isBefore(result.deletedAt().plus(Duration.ofDays(7)));
    }

    // CreateRoomService로는 만료된 방을 만들 수 없어 직접 복원한다.
    private Room expiredRoom() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        return Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            RoomStatus.initial(),
            new RoomExpiration(past),
            UploadPolicy.ANYONE,
            HOST,
            past.minus(Duration.ofHours(24)),
            null
        );
    }
}