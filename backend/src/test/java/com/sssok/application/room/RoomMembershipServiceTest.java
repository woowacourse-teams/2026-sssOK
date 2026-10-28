package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomHostCannotLeaveException;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.application.room.exception.RoomModifiedException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.ExpiredRoomStatus;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import com.sssok.infrastructure.realtime.InMemorySseEventPublisher;
import com.sssok.infrastructure.realtime.RoomEventJpaEntity;
import com.sssok.infrastructure.realtime.RoomEventJpaRepository;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

// Repository + Service 통합 테스트
// 입장이 PostgreSQL 전용 네이티브 쿼리를 쓰므로 H2가 아닌 실제 PostgreSQL로 돌린다.
@TestPropertySource(properties = {
    "storage.r2.endpoint=https://dummy.r2.cloudflarestorage.com",
    "storage.r2.access-key=dummy-access-key",
    "storage.r2.secret-key=dummy-secret-key",
    "storage.r2.bucket=sssok-test",
    "media.thumbnail.auto-generate=false"
})
@PostgresIntegrationTest
class RoomMembershipServiceTest extends PostgresContainerSupport {

    @Autowired
    RoomMembershipService roomMembershipService;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    GetRoomService getRoomService;

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    RoomRepository roomRepository;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Autowired
    StoredFileJpaRepository storedFileRepository;

    @PersistenceContext
    EntityManager entityManager;

    @Autowired
    InMemorySseEventPublisher sseEventPublisher;

    @Autowired
    PlatformTransactionManager transactionManager;

    private Long hostId;
    private Long guestId;

    @BeforeEach
    void setUp() {
        hostId = createMember("가현");
        guestId = createMember("민수");
    }

    @Test
    void 처음_입장하면_신규_참여로_기록된다() {
        Room room = createRoom();

        JoinRoomResult result = roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(result.newlyJoined()).isTrue();
        assertThat(result.roomId()).isEqualTo(room.getId());
        assertThat(result.userId()).isEqualTo(guestId);
        assertThat(result.joinedAt()).isNotNull();
    }

    @Test
    void 표시_이름은_요청이_아니라_인증된_회원_정보에서_온다() {
        Room room = createRoom();

        JoinRoomResult result = roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(result.displayName()).isEqualTo("민수");
    }

    @Test
    void 응답에_이_방의_방장_ID가_담긴다() {
        Room room = createRoom();

        JoinRoomResult guestResult = roomMembershipService.join(room.getId(), room.getCode(), guestId);
        JoinRoomResult hostResult = roomMembershipService.join(room.getId(), room.getCode(), hostId);

        assertThat(guestResult.hostId()).isEqualTo(hostId);
        assertThat(hostResult.hostId()).isEqualTo(hostId);
    }

    @Test
    void 신규_참여하면_room_member_joined_이벤트가_발행된다() {
        Room room = createRoom();

        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(room.getId(), 0L);
        assertThat(events).hasSize(1);
        assertThat(events.get(0).getEventType()).isEqualTo("room.member.joined");
        assertThat(events.get(0).getPayload()).contains(guestId.toString());
    }

    @Test
    void 재입장하면_이벤트가_다시_발행되지_않는다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(room.getId(), 0L);
        assertThat(events).hasSize(1);
    }

    @Test
    void 이미_참여_중인_사람이_다시_입장하면_신규_참여가_아니다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        JoinRoomResult result = roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(result.newlyJoined()).isFalse();
    }

    @Test
    void 다시_입장해도_처음_참여_시각이_유지된다() {
        Room room = createRoom();
        JoinRoomResult first = roomMembershipService.join(room.getId(), room.getCode(), guestId);

        JoinRoomResult second = roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(second.joinedAt()).isEqualTo(first.joinedAt());
    }

    @Test
    void 다시_입장해도_참여_기록은_하나뿐이다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);
        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isPresent();
    }

    @Test
    void 입장하면_방_조회의_joined가_참이_된다() {
        Room room = createRoom();

        assertThat(getRoomService.getByCode(room.getCode(), guestId).joined()).isFalse();

        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), guestId).joined()).isTrue();
    }

    @Test
    void 다른_사람이_입장해도_내_joined는_그대로다() {
        Room room = createRoom();
        boolean beforeGuestJoins = getRoomService.getByCode(room.getCode(), hostId).joined();

        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), hostId).joined()).isEqualTo(beforeGuestJoins);
    }

    @Test
    void 토큰_없이_조회하면_joined는_거짓이다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), null).joined()).isFalse();
    }

    @Test
    void 방장_표시_이름은_입장과_무관하게_생성_직후부터_채워진다() {
        Room room = createRoom();

        assertThat(getRoomService.getByCode(room.getCode(), null).hostName()).isEqualTo("가현");
    }

    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> roomMembershipService.join(-1L, new RoomCode("ABCD2345"), guestId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 만료된_방이면_예외() {
        Room expired = roomRepository.save(expiredRoom());

        assertThatThrownBy(() -> roomMembershipService.join(expired.getId(), expired.getCode(), guestId))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 삭제된_방이면_예외() {
        Room room = createRoom();
        room.delete(hostId, Instant.now());
        roomRepository.save(room);

        assertThatThrownBy(() -> roomMembershipService.join(room.getId(), room.getCode(), guestId))
            .isInstanceOf(RoomExpiredException.class);
    }

    // 만료 기한이 있던 시절에 만료된 방. 지금은 만료되는 방이 생기지 않아 저장된 상태로 직접 복원한다.

    @Test
    void 내_목록은_역할과_집계를_반환하고_삭제된_방과_다른_방은_제외한다() {
        Room hosted = createRoom();
        Room joined = createRoomService.create(guestId, "참여방", null).room();
        roomMembershipService.join(joined.getId(), joined.getCode(), hostId);
        Room deleted = createRoomService.create(hostId, "삭제방", null).room();
        deleted.delete(hostId, Instant.now());
        roomRepository.save(deleted);
        createRoomService.create(guestId, "다른방", null);
        saveMedia(joined.getId(), guestId, "JPEG", "READY", "old.webp");
        long latest = saveMedia(joined.getId(), guestId, "JPEG", "PROCESSING", null);
        saveMedia(joined.getId(), guestId, "MP4", "READY", "video.webp");
        saveMedia(joined.getId(), guestId, "PNG", "RESERVED", null);
        saveMedia(joined.getId(), guestId, "PNG", "FAILED", null);
        var rooms = roomMembershipService.getMyRooms(hostId);
        assertThat(rooms).extracting(MyRoomSummary::roomId)
            .containsExactly(joined.getId(), hosted.getId());
        assertThat(rooms.getFirst().isHost()).isFalse();
        assertThat(rooms.getFirst().mediaCount()).isEqualTo(3);
        assertThat(rooms.getFirst().memberCount()).isEqualTo(2);
        assertThat(rooms.getFirst().thumbnailUrl()).contains("old.webp");
        assertThat(rooms.getLast().isHost()).isTrue();
        assertThat(rooms.getLast().mediaCount()).isZero();
        assertThat(rooms.getLast().memberCount()).isEqualTo(1);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
            entityManager.createQuery("UPDATE StoredFileJpaEntity f SET f.thumbnailKey = :key WHERE f.id = :id")
                .setParameter("key", "latest.webp").setParameter("id", latest).executeUpdate());
        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().thumbnailUrl()).contains("latest.webp");
        storedFileRepository.deleteById(latest);
        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().thumbnailUrl()).contains("old.webp");
    }

    @Test
    void 썸네일이_준비된_사진이_없으면_null을_반환하고_동영상과_실패한_사진은_선택하지_않는다() {
        Room room = createRoom();
        saveMedia(room.getId(), hostId, "JPEG", "PROCESSING", null);
        saveMedia(room.getId(), hostId, "MP4", "READY", "video.webp");
        saveMedia(room.getId(), hostId, "PNG", "FAILED", "failed.webp");
        saveMedia(room.getId(), hostId, "PNG", "RESERVED", "reserved.webp");

        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().thumbnailUrl()).isNull();
    }

    @Test
    void 나가기와_반복_요청은_미디어를_유지하고_재참여는_새_기록을_만든다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);
        var before = roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId).orElseThrow();
        saveMedia(room.getId(), guestId, "JPEG", "READY", "retained.webp");
        roomMembershipService.leave(room.getId(), guestId);
        roomMembershipService.leave(room.getId(), guestId);
        assertThat(roomMembershipService.getMyRooms(guestId)).isEmpty();
        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().memberCount()).isEqualTo(1);
        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().mediaCount()).isEqualTo(1);
        assertThatThrownBy(() -> sseEventPublisher.subscribe(room.getId(), guestId, null))
            .isInstanceOf(RoomMembershipRequiredException.class);
        assertThat(roomMembershipService.join(room.getId(), room.getCode(), guestId).newlyJoined()).isTrue();
        var after = roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId).orElseThrow();
        assertThat(after.getId()).isNotEqualTo(before.getId());
        assertThat(after.getJoinedAt()).isAfter(before.getJoinedAt());
        assertThat(roomMembershipService.getMyRooms(hostId).getFirst().memberCount()).isEqualTo(2);
    }

    @Test
    void 방장은_나갈_수_없지만_삭제된_방의_참여_기록은_정리할_수_있다() {
        Room room = createRoom();
        assertThatThrownBy(() -> roomMembershipService.leave(room.getId(), hostId))
            .isInstanceOf(RoomHostCannotLeaveException.class);
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), hostId)).isPresent();
        room.delete(hostId, Instant.now());
        roomRepository.save(room);
        roomMembershipService.leave(room.getId(), hostId);
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), hostId)).isEmpty();
        assertThatThrownBy(() -> roomMembershipService.leave(Long.MAX_VALUE, hostId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 같은_참여시각도_ID_역순으로_정렬하고_참여하지_않은_회원은_빈_목록이다() {
        assertThat(roomMembershipService.getMyRooms(guestId)).isEmpty();
        Room first = createRoom();
        Room second = createRoom();
        new TransactionTemplate(transactionManager).executeWithoutResult(tx ->
            entityManager.createQuery("UPDATE RoomMemberJpaEntity m SET m.joinedAt = :time WHERE m.memberId = :memberId")
                .setParameter("time", Instant.parse("2026-01-01T00:00:00Z"))
                .setParameter("memberId", hostId)
                .executeUpdate());
        assertThat(roomMembershipService.getMyRooms(hostId)).extracting(MyRoomSummary::roomId)
            .containsExactly(second.getId(), first.getId());
    }

    @Test
    void 나가기_롤백이면_연결을_유지하고_커밋하면_본인의_연결만_종료한다() throws Exception {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);
        var guest = sseEventPublisher.subscribe(room.getId(), guestId, null);
        var host = sseEventPublisher.subscribe(room.getId(), hostId, null);
        new TransactionTemplate(transactionManager).executeWithoutResult(tx -> {
            roomMembershipService.leave(room.getId(), guestId);
            tx.setRollbackOnly();
        });
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isPresent();
        guest.send("still joined");
        roomMembershipService.leave(room.getId(), guestId);
        assertThatThrownBy(() -> guest.send("left")).isInstanceOf(IllegalStateException.class);
        host.send("still host");
        sseEventPublisher.completeAll(room.getId());
    }

    @Test
    void 입장과_나가기가_겹치면_입장하거나_충돌로_종료한다() throws Exception {
        Room room = createRoom();
        try (var pool = Executors.newFixedThreadPool(2)) {
            for (int i = 0; i < 10; i++) {
                var start = new CountDownLatch(1);
                var join = pool.submit(() -> {
                    start.await();
                    try {
                        return roomMembershipService.join(room.getId(), room.getCode(), guestId);
                    } catch (RoomModifiedException e) {
                        return null;
                    }
                });
                var leave = pool.submit(() -> {
                    start.await();
                    roomMembershipService.leave(room.getId(), guestId);
                    return null;
                });
                start.countDown();
                join.get(10, TimeUnit.SECONDS);
                leave.get(10, TimeUnit.SECONDS);
            }
        }
        assertThat(entityManager.createQuery(
            "SELECT count(m) FROM RoomMemberJpaEntity m WHERE m.roomId = :roomId AND m.memberId = :memberId", Long.class)
            .setParameter("roomId", room.getId()).setParameter("memberId", guestId).getSingleResult())
            .isBetween(0L, 1L);
    }

    @Test
    void 한_회원의_입장_트랜잭션이_끝나기_전에도_다른_회원은_입장할_수_있다() throws Exception {
        Room room = createRoom();
        Long otherId = createMember("다른회원");
        var inserted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var first = pool.submit(() ->
                new TransactionTemplate(transactionManager).execute(tx -> {
                    var result = roomMembershipService.join(room.getId(), room.getCode(), guestId);
                    inserted.countDown();
                    try {
                        if (!release.await(10, TimeUnit.SECONDS)) {
                            throw new AssertionError("트랜잭션 종료 대기 시간 초과");
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException(e);
                    }
                    return result;
                }));
            assertThat(inserted.await(5, TimeUnit.SECONDS)).isTrue();
            // 이 테스트는 입장 SQL의 독립성을 검증한다. 두 연결만 쓰는 테스트 풀에서
            // AFTER_COMMIT 리스너의 새 연결 대기가 섞이지 않도록 두 번째 트랜잭션은 롤백한다.
            var second = pool.submit(() ->
                new TransactionTemplate(transactionManager).execute(tx -> {
                    var result = roomMembershipService.join(room.getId(), room.getCode(), otherId);
                    tx.setRollbackOnly();
                    return result;
                }));
            assertThat(second.get(5, TimeUnit.SECONDS).newlyJoined()).isTrue();
            release.countDown();
            assertThat(first.get(5, TimeUnit.SECONDS).newlyJoined()).isTrue();
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test
    void 같은_회원의_나가기_요청이_겹쳐도_모두_성공한다() throws Exception {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId);
        try (var pool = Executors.newFixedThreadPool(4)) {
            var start = new CountDownLatch(1);
            var requests = new ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) {
                requests.add(pool.submit(() -> {
                    start.await();
                    roomMembershipService.leave(room.getId(), guestId);
                    return null;
                }));
            }
            start.countDown();
            for (var request : requests) {
                request.get(5, TimeUnit.SECONDS);
            }
        }
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isEmpty();
    }

    @Test
    void 같은_회원이_방마다_다른_닉네임을_사용한다() {
        Room first = createRoom();
        Room second = createRoomService.create(hostId, "두번째 방", null, "방장별명").room();

        roomMembershipService.join(first.getId(), first.getCode(), guestId, "여행자");
        roomMembershipService.join(second.getId(), second.getCode(), guestId, "사진가");

        assertThat(getRoomService.getByCode(first.getCode(), guestId).myNickname()).isEqualTo("여행자");
        assertThat(getRoomService.getByCode(second.getCode(), guestId).myNickname()).isEqualTo("사진가");
        assertThat(getRoomService.getByCode(second.getCode(), guestId).hostName()).isEqualTo("방장별명");
    }

    @Test
    void 재입장하면_새로_보낸_닉네임으로_기존_닉네임을_덮어쓰지_않는다() {
        Room room = createRoom();
        roomMembershipService.join(room.getId(), room.getCode(), guestId, "처음별명");

        JoinRoomResult result = roomMembershipService.join(room.getId(), room.getCode(), guestId, "다른별명");

        assertThat(result.displayName()).isEqualTo("처음별명");
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId).orElseThrow().getNickname())
            .isEqualTo("처음별명");
    }

    @Test
    void 생성과_입장은_검증한_닉네임_값을_저장한다() {
        Room room = createRoomService.create(hostId, "닉네임 검증", null, "  방장별명  ").room();

        JoinRoomResult result = roomMembershipService.join(room.getId(), room.getCode(), guestId, "  참여자별명  ");

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), hostId)
            .orElseThrow().getNickname()).isEqualTo("방장별명");
        assertThat(result.displayName()).isEqualTo("참여자별명");
        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)
            .orElseThrow().getNickname()).isEqualTo("참여자별명");
    }

    private Long createMember(String nickname) {
        AuthResult result = anonymousAuthService.authenticate(nickname);
        return result.userId();
    }

    private Room createRoom() {
        return createRoomService.create(hostId, "우테코 회식", null).room();
    }

    private Room expiredRoom() {
        return Room.reconstruct(
            null,
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            ExpiredRoomStatus.INSTANCE,
            UploadPolicy.ANYONE,
            hostId,
            Instant.now().minus(Duration.ofDays(2)),
            null
        );
    }

    private long saveMedia(Long roomId, Long uploaderId, String type, String status, String thumbnail) {
        Instant now = Instant.now();
        var file = new StoredFileJpaEntity(
            null, roomId, uploaderId, "test", type, 100L, UUID.randomUUID().toString(),
            null, status, now, now, 0, thumbnail, null, null, null, null, null, null, null);
        return storedFileRepository.saveAndFlush(file).getId();
    }
}
