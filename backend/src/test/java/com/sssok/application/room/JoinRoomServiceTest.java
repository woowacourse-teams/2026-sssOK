package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.support.PostgresContainerSupport;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

// Repository + Service 통합 테스트
// 입장이 PostgreSQL 전용 네이티브 쿼리를 쓰므로 H2가 아닌 실제 PostgreSQL로 돌린다.
@SpringBootTest
class JoinRoomServiceTest extends PostgresContainerSupport {

    @Autowired
    JoinRoomService joinRoomService;

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

    private Long hostId;
    private Long guestId;

    @BeforeEach
    void setUp() {
        hostId = 회원_생성("가현");
        guestId = 회원_생성("민수");
    }

    private Long 회원_생성(String nickname) {
        AuthResult result = anonymousAuthService.authenticate(nickname);
        return result.userId();
    }

    private Room createRoom() {
        return createRoomService.create(hostId, "우테코 회식").room();
    }


    @Test
    void 처음_입장하면_신규_참여로_기록된다() {
        Room room = createRoom();

        JoinRoomResult result = joinRoomService.join(room.getId(), guestId);

        assertThat(result.newlyJoined()).isTrue();
        assertThat(result.roomId()).isEqualTo(room.getId());
        assertThat(result.userId()).isEqualTo(guestId);
        assertThat(result.joinedAt()).isNotNull();
    }

    @Test
    void 표시_이름은_요청이_아니라_인증된_회원_정보에서_온다() {
        Room room = createRoom();

        JoinRoomResult result = joinRoomService.join(room.getId(), guestId);

        assertThat(result.displayName()).isEqualTo("민수");
    }

    @Test
    void 응답에_이_방의_방장_ID가_담긴다() {
        Room room = createRoom();

        JoinRoomResult guestResult = joinRoomService.join(room.getId(), guestId);
        JoinRoomResult hostResult = joinRoomService.join(room.getId(), hostId);

        assertThat(guestResult.hostId()).isEqualTo(hostId);
        assertThat(hostResult.hostId()).isEqualTo(hostId);
    }

    @Test
    void 이미_참여_중인_사람이_다시_입장하면_신규_참여가_아니다() {
        Room room = createRoom();
        joinRoomService.join(room.getId(), guestId);

        JoinRoomResult result = joinRoomService.join(room.getId(), guestId);

        assertThat(result.newlyJoined()).isFalse();
    }

    @Test
    void 다시_입장해도_처음_참여_시각이_유지된다() {
        Room room = createRoom();
        JoinRoomResult first = joinRoomService.join(room.getId(), guestId);

        JoinRoomResult second = joinRoomService.join(room.getId(), guestId);

        assertThat(second.joinedAt()).isEqualTo(first.joinedAt());
    }

    @Test
    void 다시_입장해도_참여_기록은_하나뿐이다() {
        Room room = createRoom();
        joinRoomService.join(room.getId(), guestId);
        joinRoomService.join(room.getId(), guestId);

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(room.getId(), guestId)).isPresent();
    }

    @Test
    void 입장하면_방_조회의_joined가_참이_된다() {
        Room room = createRoom();

        assertThat(getRoomService.getByCode(room.getCode(), guestId).joined()).isFalse();

        joinRoomService.join(room.getId(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), guestId).joined()).isTrue();
    }

    @Test
    void 다른_사람이_입장해도_내_joined는_그대로다() {
        Room room = createRoom();

        joinRoomService.join(room.getId(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), hostId).joined()).isFalse();
    }

    @Test
    void 토큰_없이_조회하면_joined는_거짓이다() {
        Room room = createRoom();
        joinRoomService.join(room.getId(), guestId);

        assertThat(getRoomService.getByCode(room.getCode(), null).joined()).isFalse();
    }

    @Test
    void 방장_표시_이름은_입장과_무관하게_생성_직후부터_채워진다() {
        Room room = createRoom();

        assertThat(getRoomService.getByCode(room.getCode(), null).hostName()).isEqualTo("가현");
    }





    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> joinRoomService.join(-1L, guestId))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 만료된_방이면_예외() {
        Room expired = roomRepository.save(expiredRoom());

        assertThatThrownBy(() -> joinRoomService.join(expired.getId(), guestId))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 삭제된_방이면_예외() {
        Room room = createRoom();
        room.delete(hostId, Instant.now());
        roomRepository.save(room);

        assertThatThrownBy(() -> joinRoomService.join(room.getId(), guestId))
            .isInstanceOf(RoomExpiredException.class);
    }

    // CreateRoomService로는 만료된 방을 만들 수 없어 직접 복원한다.
    private Room expiredRoom() {
        Instant past = Instant.now().minus(Duration.ofHours(1));
        return Room.reconstruct(
            null,
            RoomCode.generate(new SecureRandom()),
            new RoomName("지난 회식"),
            RoomStatus.initial(),
            new RoomExpiration(past),
            UploadPolicy.ANYONE,
            hostId,
            past.minus(Duration.ofHours(24)),
            null
        );
    }
}