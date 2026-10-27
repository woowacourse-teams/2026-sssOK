package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.EmptyPatchException;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.InvalidRoomNameException;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import com.sssok.domain.room.exception.RoomHostRequiredException;
import com.sssok.domain.room.roomstatus.ExpiredRoomStatus;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.support.H2IntegrationTest;

// Repository + Service 통합 테스트 (H2)
@H2IntegrationTest
class UpdateRoomServiceTest {

    @Autowired
    UpdateRoomService updateRoomService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    private Long HOST;
    private Long GUEST;

    @BeforeEach
    void setUp() {
        HOST = anonymousAuthService.authenticate("가현").userId();
        GUEST = anonymousAuthService.authenticate("민수").userId();
    }

    private Room createRoom() {
        return createRoomService.create(HOST, "우테코 회식", null).room();
    }

    @Test
    void 이름만_보내면_이름만_바뀐다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null));

        assertThat(updated.room().getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
    }

    @Test
    void 업로드_권한만_보내면_권한만_바뀐다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, "host"));

        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
        assertThat(updated.room().getName()).isEqualTo(new RoomName("우테코 회식"));
    }

    @Test
    void 두_항목을_한꺼번에_바꿀_수_있다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", "host"));

        assertThat(updated.room().getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(updated.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 변경한_내용은_저장소에_반영된다() {
        Room room = createRoom();

        updateRoomService.update(room.getId(), HOST, new UpdateRoomCommand("2차 회식", "host"));

        Room reloaded = roomRepository.findByCode(room.getCode()).orElseThrow();
        assertThat(reloaded.getName()).isEqualTo(new RoomName("2차 회식"));
        assertThat(reloaded.getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 응답에_방장_표시_이름이_담긴다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null));

        assertThat(updated.hostName()).isEqualTo("가현");
    }

    @Test
    void 방장은_생성_시점에_자동으로_입장돼_있어_joined가_참이다() {
        Room room = createRoom();

        RoomDetail updated = updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null));

        assertThat(updated.joined()).isTrue();
    }

    @Test
    void 수정하면_room_updated_이벤트가_발행된다() {
        Room room = createRoom();

        updateRoomService.update(room.getId(), HOST, new UpdateRoomCommand("2차 회식", "host"));

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(room.getId(), 0L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("room.updated");
        assertThat(events.get(0).getPayload()).contains("2차 회식").contains("host");
    }

    @Test
    void 아무_항목도_보내지_않으면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, null)))
            .isInstanceOf(EmptyPatchException.class);
    }

    @Test
    void 방장이_아니면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), GUEST,
            new UpdateRoomCommand("2차 회식", null)))
            .isInstanceOf(RoomHostRequiredException.class);
    }

    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> updateRoomService.update(-1L, HOST,
            new UpdateRoomCommand("2차 회식", null)))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 만료된_방이면_예외() {
        Room expired = roomRepository.save(expiredRoom());

        assertThatThrownBy(() -> updateRoomService.update(expired.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null)))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 삭제된_방이면_예외() {
        Room room = createRoom();
        room.delete(HOST, Instant.now());
        roomRepository.save(room);

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("2차 회식", null)))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 알_수_없는_업로드_권한이면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand(null, "nobody")))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }

    @Test
    void 빈_이름으로_바꾸려_하면_예외() {
        Room room = createRoom();

        assertThatThrownBy(() -> updateRoomService.update(room.getId(), HOST,
            new UpdateRoomCommand("", null)))
            .isInstanceOf(InvalidRoomNameException.class);
    }

    // 만료 기한이 있던 시절에 만료된 방. 지금은 만료되는 방이 생기지 않아 저장된 상태로 직접 복원한다.
    private Room expiredRoom() {
        return Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            ExpiredRoomStatus.INSTANCE,
            UploadPolicy.ANYONE,
            HOST,
            Instant.now().minus(Duration.ofDays(2)),
            null
        );
    }
}