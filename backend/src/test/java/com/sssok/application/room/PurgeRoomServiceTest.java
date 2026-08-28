package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.IssueLinkCodeService;
import com.sssok.application.auth.LinkCodeResult;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileStoragePort;
import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.domain.auth.LinkCodeValue;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.ProcessedMedia;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import jakarta.persistence.EntityManager;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

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

    @Autowired
    JoinRoomService joinRoomService;

    @Autowired
    IssueLinkCodeService issueLinkCodeService;

    @Autowired
    LinkCodeRepository linkCodeRepository;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Autowired
    FileRepository fileRepository;

    @MockitoBean
    FileStoragePort fileStoragePort;

    @Autowired
    EntityManager entityManager;

    private Long hostId;
    private Long guestId;

    @BeforeEach
    void setUp() {
        hostId = anonymousAuthService.authenticate("가현").userId();
        guestId = anonymousAuthService.authenticate("민수").userId();
    }

    // 원본과 썸네일은 서로 다른 키다. 하나만 지우면 남은 쪽이 스토리지에 영영 남아 요금을 먹는다.
    @Test
    void 방을_지우면_원본과_썸네일을_모두_스토리지에서_지운다() {
        Room room = 삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));
        StoredFile file = StoredFile.reserve(room.getId(), hostId, "사진.jpg", "image/jpeg",
            new FileSize(1024), Instant.now());
        file.startProcessing();
        StorageKey original = file.getStorageKey();
        StorageKey thumbnail = original.thumbnail();
        file.completeProcessing(new ProcessedMedia(thumbnail, 1200, 900, null, null));
        fileRepository.save(file);

        purgeRoomService.purgeAll(Instant.now());

        verify(fileStoragePort).delete(original);
        verify(fileStoragePort).delete(thumbnail);
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
    void 방을_지우면_그_방의_회원도_함께_사라진다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        // 방마다 익명 인증을 새로 하므로, 방이 사라지면 그 회원으로 다시 인증할 방법이 없다.
        assertThat(memberRepository.findById(guestId)).isEmpty();
    }

    @Test
    void 방장도_함께_사라진다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(memberRepository.findById(hostId)).isEmpty();
    }

    @Test
    void 참여_기록이_없는_방장도_함께_사라진다() {
        // 방장을 참여자로 등록하기 전에 만들어진 방은 host_id 로만 방장을 안다.
        Room room = 삭제된_방(Instant.now().minus(RETENTION).minus(Duration.ofDays(1)));

        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomRepository.findById(room.getId())).isEmpty();
        assertThat(memberRepository.findById(hostId)).isEmpty();
    }

    @Test
    void 방을_지우면_이벤트_기록도_함께_사라진다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        deleteRoomService.delete(room.getId(), hostId);
        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(room.getId(), 0L))
            .isNotEmpty();
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(room.getId(), 0L))
            .isEmpty();
    }

    // 새 테이블이 생겼는데 정리 대상에 넣는 걸 잊으면 여기서 걸린다.
    @Test
    void 방을_지우면_그_방과_이어진_행이_어느_테이블에도_남지_않는다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        // joinRoomService 는 PostgreSQL 전용 ON CONFLICT 를 써서 H2 에서 돌지 않는다.
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        issueLinkCodeService.issue(hostId);
        issueLinkCodeService.issue(guestId);
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(countBy("room", "id", room.getId())).isZero();
        assertThat(countBy("room_member", "room_id", room.getId())).isZero();
        assertThat(countBy("room_events", "room_id", room.getId())).isZero();
        assertThat(countBy("member", "id", hostId)).isZero();
        assertThat(countBy("member", "id", guestId)).isZero();
        assertThat(countBy("link_code", "member_id", hostId)).isZero();
        assertThat(countBy("link_code", "member_id", guestId)).isZero();
    }

    private long countBy(String table, String column, Long value) {
        return ((Number) entityManager
            .createNativeQuery("SELECT COUNT(*) FROM " + table + " WHERE " + column + " = :value")
            .setParameter("value", value)
            .getSingleResult()).longValue();
    }

    @Test
    void 지워진_회원의_토큰은_더_쓸_수_없다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        // JWT 는 서명만 검증해서 회원을 지워도 토큰 자체는 계속 열린다.
        // 회원 행이 사라진 것으로 무효가 되어야 한다.
        Room another = createRoomService.create(hostId, "다음 회식", null, null).room();
        assertThatThrownBy(() -> joinRoomService.join(another.getId(), guestId))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 방을_지우면_그_회원의_연결_코드도_사라진다() {
        Room room = createRoomService.create(hostId, "우테코 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(room.getId(), guestId, Instant.now()));
        LinkCodeResult code = issueLinkCodeService.issue(guestId);
        deleteRoomService.delete(room.getId(), hostId);
        오래된_삭제로_되돌리기(room);

        purgeRoomService.purgeAll(Instant.now());

        // 회원만 지우고 코드를 남기면 없는 회원을 가리키는 행이 된다.
        assertThat(linkCodeRepository.findByCode(new LinkCodeValue(code.linkCode()))).isEmpty();
    }

    @Test
    void 다른_방에_남아있는_회원은_지우지_않는다() {
        Room purged = createRoomService.create(hostId, "우테코 회식", null, null).room();
        Room surviving = createRoomService.create(hostId, "다음 회식", null, null).room();
        roomMemberRepository.save(RoomMember.join(purged.getId(), guestId, Instant.now()));
        roomMemberRepository.save(RoomMember.join(surviving.getId(), guestId, Instant.now()));
        deleteRoomService.delete(purged.getId(), hostId);
        오래된_삭제로_되돌리기(purged);

        purgeRoomService.purgeAll(Instant.now());

        assertThat(roomRepository.findById(purged.getId())).isEmpty();
        assertThat(memberRepository.findById(guestId)).isPresent();
        assertThat(memberRepository.findById(hostId)).isPresent();
        assertThat(roomRepository.findById(surviving.getId())).isPresent();
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
