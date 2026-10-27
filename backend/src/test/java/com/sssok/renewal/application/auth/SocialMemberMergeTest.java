package com.sssok.renewal.application.auth;

import com.sssok.application.port.out.DownloadJobRepository;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.CreateRoomService;
import com.sssok.application.room.JoinRoomService;
import com.sssok.common.exception.ErrorCode;
import com.sssok.renewal.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@PostgresIntegrationTest
class SocialMemberMergeTest extends PostgresContainerSupport {
    @Autowired SocialMemberLoginService socialMemberLoginService;
    @Autowired MemberRepository memberRepository;
    @Autowired SessionTokenProvider sessionTokenProvider;
    @Autowired CreateRoomService createRoomService;
    @Autowired JoinRoomService joinRoomService;
    @Autowired FileRepository fileRepository;
    @Autowired DownloadJobRepository downloadJobRepository;
    @Autowired RoomRepository roomRepository;
    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired PlatformTransactionManager transactionManager;
    @Autowired IssueLinkCodeService issueLinkCodeService;
    @Autowired LinkLoginService linkLoginService;

    Long sourceId;
    Long targetId;
    Long roomId;
    Long fileId;
    Long jobId;
    SocialIdentity identity;
    String sourceToken;
    String targetToken;
    TransactionTemplate tx;

    @BeforeEach
    void setUp() {
        tx = new TransactionTemplate(transactionManager);
        sourceId = memberRepository.save(Member.register(new Nickname("익명닉네임"), Instant.now())).getId();
        sourceToken = sessionTokenProvider.issue(sourceId, Instant.now()).value();
        identity = new SocialIdentity(SocialProvider.KAKAO, UUID.randomUUID().toString());
        Long originalTarget = memberRepository.save(Member.register(new Nickname("회원닉네임"), Instant.now())).getId();
        AuthResult registered = socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(originalTarget), RefreshTokenDelivery.COOKIE);
        targetId = registered.userId();
        targetToken = registered.accessToken();
        roomId = createRoomService.create(sourceId, "통합할 방", null).room().getId();
        joinRoomService.join(roomId, targetId);
        jdbcTemplate.update("UPDATE room_member SET joined_at = '2020-01-01T00:00:00Z' WHERE room_id = ? AND member_id = ?",
            roomId, sourceId);
        fileId = jdbcTemplate.queryForObject("""
            INSERT INTO stored_file (room_id, uploader_id, original_file_name, media_type, file_size_bytes,
                storage_key, status, created_at, updated_at, reserved_at, retry_count)
            VALUES (?, ?, 'a.jpg', 'JPEG', 1024, ?, 'RESERVED', now(), now(), now(), 0) RETURNING id
            """, Long.class, roomId, sourceId, UUID.randomUUID().toString());
        jdbcTemplate.update("""
            INSERT INTO media_like (media_id, member_id, created_at, updated_at)
            VALUES (?, ?, now(), now()), (?, ?, now(), now())
            """, fileId, sourceId, fileId, targetId);
        jobId = jdbcTemplate.queryForObject("""
            INSERT INTO download_job (room_id, requester_id, status, media_count, total_size_bytes,
                file_name, created_at, updated_at)
            VALUES (?, ?, 'RUNNING', 1, 1024, 'a.zip', now(), now()) RETURNING id
            """, Long.class, roomId, sourceId);
        jdbcTemplate.update("""
            INSERT INTO feedback (content, room_id, room_name, member_id, nickname, created_at, updated_at)
            VALUES ('의견', ?, '작성 당시 방', ?, '작성 당시 닉네임', now(), now())
            """, roomId, sourceId);
    }

    @Test
    void 활동을_이전하고_중복을_합치며_회원_닉네임과_다른_세션을_유지한다() {
        String code = issueLinkCodeService.issue(sourceId).linkCode();
        Long onlySourceRoom = createRoomService.create(sourceId, "익명만 참여", null).room().getId();
        AuthResult result = socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(sourceId), RefreshTokenDelivery.COOKIE);
        assertThat(result.userId()).isEqualTo(targetId);
        assertThat(result.nickname()).isEqualTo("회원닉네임");
        assertThat(sessionTokenProvider.parse(result.accessToken())).isEqualTo(targetId);
        assertThat(sessionTokenProvider.parse(targetToken)).isEqualTo(targetId);
        assertThatThrownBy(() -> sessionTokenProvider.parse(sourceToken)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> linkLoginService.login(code))
            .isInstanceOf(LinkCodeNotFoundException.class);
        assertThat(memberRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(count("room_member", "member_id", sourceId)).isZero();
        assertThat(count("room_member", "room_id", roomId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT joined_at FROM room_member WHERE room_id = ? AND member_id = ?",
            Timestamp.class, roomId, targetId).toInstant()).isEqualTo(Instant.parse("2020-01-01T00:00:00Z"));
        assertThat(roomRepository.findById(onlySourceRoom).orElseThrow().getHostId()).isEqualTo(targetId);
        assertThat(roomRepository.findById(roomId).orElseThrow().getHostId()).isEqualTo(targetId);
        assertThat(fileRepository.findById(fileId).orElseThrow().getUploaderId()).isEqualTo(targetId);
        assertThat(downloadJobRepository.findById(jobId).orElseThrow().getRequesterId()).isEqualTo(targetId);
        assertThat(count("media_like", "member_id", sourceId)).isZero();
        assertThat(count("media_like", "media_id", fileId)).isEqualTo(1);
        assertThat(count("feedback", "member_id", sourceId)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT nickname FROM feedback WHERE room_id = ?", String.class, roomId))
            .isEqualTo("작성 당시 닉네임");
        assertThat(count("feedback", "member_id", targetId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT room_name FROM feedback WHERE room_id = ?", String.class, roomId))
            .isEqualTo("작성 당시 방");
    }

    @Test
    void 통합_트랜잭션이_실패하면_활동과_인증을_모두_복원한다() {
        String code = issueLinkCodeService.issue(sourceId).linkCode();
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> {
            socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(sourceId), RefreshTokenDelivery.COOKIE);
            throw new IllegalStateException("통합 후 실패");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(memberRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ANONYMOUS);
        assertThat(sessionTokenProvider.parse(sourceToken)).isEqualTo(sourceId);
        assertThat(linkLoginService.login(code).userId()).isEqualTo(sourceId);
        assertThat(roomRepository.findById(roomId).orElseThrow().getHostId()).isEqualTo(sourceId);
        assertThat(fileRepository.findById(fileId).orElseThrow().getUploaderId()).isEqualTo(sourceId);
        assertThat(downloadJobRepository.findById(jobId).orElseThrow().getRequesterId()).isEqualTo(sourceId);
        assertThat(count("room_member", "room_id", roomId)).isEqualTo(2);
        assertThat(count("media_like", "media_id", fileId)).isEqualTo(2);
        assertThat(count("feedback", "member_id", sourceId)).isEqualTo(1);
    }

    @Test
    void 같은_익명_회원의_동시_통합은_한번만_성공한다() throws Exception {
        SocialIdentity otherIdentity = new SocialIdentity(SocialProvider.KAKAO, UUID.randomUUID().toString());
        Long otherTarget = socialMemberLoginService.login(new SocialAuthentication(otherIdentity, "access", "refresh"), List.of(), RefreshTokenDelivery.COOKIE).userId();
        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<Long> a = executor.submit(() -> { await(start); return mergeOrConflict(identity); });
            Future<Long> b = executor.submit(() -> { await(start); return mergeOrConflict(otherIdentity); });
            start.countDown();
            List<Long> results = Stream.of(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS))
                .filter(Objects::nonNull).toList();
            assertThat(results).hasSize(1);
            assertThat(results.getFirst()).isIn(targetId, otherTarget);
            assertThat(roomRepository.findById(roomId).orElseThrow().getHostId()).isEqualTo(results.getFirst());
        }
    }

    @Test
    void 여러_익명_계정을_기존_회원으로_통합하고_각_방의_닉네임을_보존한다() {
        Long second = memberRepository.save(Member.register(new Nickname("두번째익명"), Instant.now())).getId();
        String secondToken = sessionTokenProvider.issue(second, Instant.now()).value();
        Long secondRoom = createRoomService.create(second, "다른 방", null, "여행자").room().getId();

        AuthResult result = socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(second, sourceId, second), RefreshTokenDelivery.COOKIE);

        assertThat(result.userId()).isEqualTo(targetId);
        assertThat(memberRepository.findById(second).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(memberRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(roomRepository.findById(secondRoom).orElseThrow().getHostId()).isEqualTo(targetId);
        assertThat(roomNickname(roomId, targetId)).isEqualTo("회원닉네임");
        assertThat(roomNickname(secondRoom, targetId)).isEqualTo("여행자");
        assertThatThrownBy(() -> sessionTokenProvider.parse(secondToken)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> sessionTokenProvider.parse(sourceToken)).isInstanceOf(UnauthorizedException.class);
        assertThat(sessionTokenProvider.parse(targetToken)).isEqualTo(targetId);
    }

    @Test
    void 신규_소셜_회원은_첫_익명_계정을_전환하고_나머지_계정을_통합한다() {
        Long second = memberRepository.save(Member.register(new Nickname("두번째익명"), Instant.now())).getId();
        Long secondRoom = createRoomService.create(second, "다른 방", null, "사진가").room().getId();
        SocialIdentity newIdentity = new SocialIdentity(SocialProvider.KAKAO, UUID.randomUUID().toString());

        AuthResult result = socialMemberLoginService.login(new SocialAuthentication(newIdentity, "access", "refresh"), List.of(second, sourceId), RefreshTokenDelivery.COOKIE);

        assertThat(result.userId()).isEqualTo(sourceId);
        assertThat(result.memberStatus()).isEqualTo(MemberStatus.REGISTERED);
        assertThat(memberRepository.findById(second).orElseThrow().getStatus()).isEqualTo(MemberStatus.MERGED);
        assertThat(roomNickname(secondRoom, sourceId)).isEqualTo("사진가");
        assertThat(sessionTokenProvider.parse(result.accessToken())).isEqualTo(sourceId);
    }

    @Test
    void 통합_대상에_등록_회원이_포함되면_어떤_익명_계정도_변경하지_않는다() {
        assertThatThrownBy(() -> socialMemberLoginService.login(new SocialAuthentication(identity, "access", "refresh"), List.of(sourceId, targetId), RefreshTokenDelivery.COOKIE)).isInstanceOf(SocialAuthException.class);

        assertThat(memberRepository.findById(sourceId).orElseThrow().getStatus()).isEqualTo(MemberStatus.ANONYMOUS);
        assertThat(roomRepository.findById(roomId).orElseThrow().getHostId()).isEqualTo(sourceId);
        assertThat(sessionTokenProvider.parse(sourceToken)).isEqualTo(sourceId);
    }

    private String roomNickname(Long room, Long member) {
        return jdbcTemplate.queryForObject("SELECT nickname FROM room_member WHERE room_id = ? AND member_id = ?",
            String.class, room, member);
    }

    private Long mergeOrConflict(SocialIdentity socialIdentity) {
        try {
            return socialMemberLoginService.login(new SocialAuthentication(socialIdentity, "access", "refresh"), List.of(sourceId), RefreshTokenDelivery.COOKIE).userId();
        } catch (SocialAuthException e) {
            assertThat(e.errorCode()).isEqualTo(ErrorCode.SOCIAL_AUTH_CONFLICT);
            return null;
        }
    }

    private long count(String table, String column, Long value) {
        return jdbcTemplate.queryForObject("SELECT count(*) FROM " + table + " WHERE " + column + " = ?", Long.class, value);
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new IllegalStateException("동시 실행 대기 시간 초과");
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        }
    }
}
