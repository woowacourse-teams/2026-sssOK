package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2)
@SpringBootTest
@ActiveProfiles("test")
class PurgeRoomServiceTest {

    private static final Duration RETENTION = Duration.ofDays(7);

    @Autowired
    PurgeRoomService purgeRoomService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    DeleteRoomService deleteRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    MemberRepository memberRepository;

    private Long hostId;
    private Long guestId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        guestId = anonymousAuthService.authenticate("민수").userId();
    }

    @Test
    void 보존_기간이_지난_삭제_방은_행까지_사라진다() {
        Room room = 삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));

        int purged = purgeRoomService.purgeAll(Instant.now());

        assertThat(purged).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId())).isEmpty();
    }

    @Test
    void 보존_기간이_아직_안_지난_삭제_방은_남는다() {
        Room room = 삭제된_방(Instant.now().minus(Duration.ofDays(1)));

        int purged = purgeRoomService.purgeAll(Instant.now());

        assertThat(purged).isZero();
        assertThat(roomRepository.findById(room.getId())).isPresent();
    }

    @Test
    void 아직_만료되지_않은_방은_대상이_아니다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();

        int purged = purgeRoomService.purgeAll(Instant.now());

        assertThat(purged).isZero();
        assertThat(roomRepository.findById(room.getId())).isPresent();
    }

    @Test
    void 삭제하지_않아도_만료_후_보존_기간이_지나면_사라진다() {
        Room room = 만료된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));

        int purged = purgeRoomService.purgeAll(Instant.now());

        assertThat(purged).isEqualTo(1);
        assertThat(roomRepository.findById(room.getId())).isEmpty();
    }

    @Test
    void 만료됐지만_보존_기간이_안_지났으면_남는다() {
        Room room = 만료된_방(Instant.now().minus(Duration.ofDays(1)));

        int purged = purgeRoomService.purgeAll(Instant.now());

        assertThat(purged).isZero();
        assertThat(roomRepository.findById(room.getId())).isPresent();
    }

    @Test
    void 삭제된_방과_만료된_방을_함께_지운다() {
        삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));
        만료된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));

        assertThat(purgeRoomService.purgeAll(Instant.now())).isEqualTo(2);
    }

    @Test
    void 방을_지우면_참여_기록도_함께_사라진다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isEmpty();
    }

    @Test
    void 방을_지워도_회원_계정은_남는다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        // 회원은 방에 속하지 않는다 — 방 하나가 사라졌다고 계정까지 지우면 다른 방 참여까지 잃는다.
        assertThat(memberRepository.findById(hostId)).isPresent();
        assertThat(memberRepository.findById(guestId)).isPresent();
    }

    @Test
    void 대상이_여러_개면_모두_지운다() {
        삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));
        삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(2)));

        assertThat(purgeRoomService.purgeAll(Instant.now())).isEqualTo(2);
    }

    @Test
    void 다시_돌려도_같은_결과다() {
        삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));

        assertThat(purgeRoomService.purgeAll(Instant.now())).isEqualTo(1);
        assertThat(purgeRoomService.purgeAll(Instant.now())).isZero();
    }

    // 삭제되지 않은 채 만료 시각만 지난 방. deletedAt 이 없어 expiresAt 이 끝난 시각이 된다.
    private Room 만료된_방(Instant expiresAt) {
        return roomRepository.save(Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("방치된 회식"),
            RoomStatus.initial(),
            new RoomExpiration(expiresAt),
            UploadPolicy.ANYONE,
            hostId,
            expiresAt.minus(Duration.ofHours(24)),
            null
        ));
    }

    // deletedAt 을 과거로 둔 삭제 방. 서비스로는 "지금" 삭제밖에 못 만들어서 직접 복원한다.
    private Room 삭제된_방(Instant deletedAt) {
        return roomRepository.save(Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            RoomStatus.from("DELETED"),
            new RoomExpiration(deletedAt.plus(Duration.ofHours(24))),
            UploadPolicy.ANYONE,
            hostId,
            deletedAt.minus(Duration.ofHours(24)),
            deletedAt
        ));
    }

    private void 오래된_삭제로_되돌리기(Room room) {
        Room stored = roomRepository.findById(room.getId()).orElseThrow();
        roomRepository.save(Room.reconstruct(
            stored.getId(),
            stored.getVersion(),
            stored.getCode(),
            stored.getName(),
            stored.getStatus(),
            stored.getExpiration(),
            stored.getUploadPolicy(),
            stored.getHostId(),
            stored.getCreatedAt(),
            Instant.now().minus(RETENTION).minus(Duration.ofDays(1))
        ));
    }
}
