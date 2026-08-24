package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.EmptyPatchException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import com.sssok.domain.room.exception.InvalidRoomNameException;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
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
class UpdateRoomServiceTest {

    @Autowired
    UpdateRoomService updateRoomService;

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
    void 이름만_보내면_이름만_바뀐다() {
        Room room = createRoom();
        Instant originalExpiresAt = storedExpiresAt(room.getId());

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null, null));

        assertThat(updated.room().getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
        assertThat(updated.room().getExpiration().expiresAt()).isEqualTo(originalExpiresAt);
    }

    @Test
    void 업로드_권한만_보내면_권한만_바뀐다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, "host", null));

        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
        assertThat(updated.room().getName()).isEqualTo(new RoomName("우테코 회식"));
    }

    @Test
    void 만료_시간만_보내면_만료_시각만_바뀐다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, null, 72));

        assertThat(updated.room().getName()).isEqualTo(new RoomName("우테코 회식"));
        assertThat(updated.room().getExpiration().expiresAt())
            .isCloseTo(Instant.now().plus(Duration.ofHours(72)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 세_항목을_한꺼번에_바꿀_수_있다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", "host", 72));

        assertThat(updated.room().getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
        assertThat(updated.room().getExpiration().expiresAt())
            .isCloseTo(Instant.now().plus(Duration.ofHours(72)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 만료_시간은_기존_만료_시각이_아니라_요청_시각_기준으로_다시_계산된다() {
        Room room = createRoom();
        Instant originalExpiresAt = room.getExpiration().expiresAt();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, null, 24));

        assertThat(updated.room().getExpiration().expiresAt())
            .isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(1, ChronoUnit.MINUTES))
            .isBefore(originalExpiresAt.plus(Duration.ofHours(1)));
    }

    @Test
    void 변경한_내용은_저장소에_반영된다() {
        Room room = createRoom();

        updateRoomService.update(room.getId(), HOST, new UpdateRoomCommand("2차 회식", "host", null));

        Room reloaded = roomRepository.findByCode(room.getCode()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(reloaded.getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 응답에_방장_표시_이름이_담긴다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null, null));

        assertThat(updated.hostName()).isEqualTo("가현");
    }

    @Test
    void 방장이_아직_입장하지_않았으면_joined는_거짓이다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null, null));

        assertThat(updated.joined()).isFalse();
    }

    @Test
    void 아무_항목도_보내지_않으면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, null, null)))
            .isInstanceOf(EmptyPatchException.class);
    }

    @Test
    void 방장이_아니면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), GUEST,
            new UpdateRoomCommand("2차 회식", null, null)))
            .isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> updateRoomService.update(-1L, HOST,
            new UpdateRoomCommand("2차 회식", null, null)))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 만료된_방이면_예외() {
        Room expired = roomRepository.save(expiredRoom());

        assertThatThrownBy(() -> updateRoomService.update(expired.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null, null)))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 삭제된_방이면_예외() {
        Room room = createRoom();
        room.delete(HOST, Instant.now());
        roomRepository.save(room);

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null, null)))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 허용되지_않은_만료_시간이면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, null, 48)))
            .isInstanceOf(InvalidRoomExpirationException.class);
    }

    @Test
    void 알_수_없는_업로드_권한이면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, "nobody", null)))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }

    @Test
    void 빈_이름으로_바꾸려_하면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("", null, null)))
            .isInstanceOf(InvalidRoomNameException.class);
    }

    // 저장된 값과 비교해야 한다. DB는 마이크로초까지만 담아서, 저장 전 값과는 정밀도가 다를 수 있다.
    private Instant storedExpiresAt(Long roomId) {
        return roomRepository.findById(roomId).orElseThrow().getExpiration().expiresAt();
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