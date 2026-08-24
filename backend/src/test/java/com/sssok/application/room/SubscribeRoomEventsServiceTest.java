package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// SSE 구독 전 방 존재/만료 검증이 실제 Repository 빈을 통해 도는지 확인하는 통합 테스트.
// H2 설정은 application-test.yml(test 프로파일)에 모아뒀다 (docs/backend/TEST_CONVENTION.md 참고).
@SpringBootTest
@ActiveProfiles("test")
class SubscribeRoomEventsServiceTest {

    private static final RandomGenerator RANDOM = new SecureRandom();

    @Autowired
    SubscribeRoomEventsService subscribeRoomEventsService;

    @Autowired
    RoomRepository roomRepository;

    @Test
    void 존재하지_않는_방이면_예외() {
        assertThatThrownBy(() -> subscribeRoomEventsService.validate(999_999L))
            .isInstanceOf(RoomNotFoundException.class);
    }

    @Test
    void 만료된_방이면_예외() {
        Room expired = Room.reconstruct(
            null,
            RoomCode.generate(RANDOM),
            new RoomName("만료방"),
            RoomStatus.initial(),
            new RoomExpiration(Instant.now().minus(1, ChronoUnit.HOURS)),
            UploadPolicy.ANYONE,
            1L,
            Instant.now().minus(2, ChronoUnit.DAYS),
            null
        );
        Room saved = roomRepository.save(expired);

        assertThatThrownBy(() -> subscribeRoomEventsService.validate(saved.getId()))
            .isInstanceOf(RoomExpiredException.class);
    }

    @Test
    void 이용_가능한_방이면_예외_없음() {
        Room active = Room.create(RoomCode.generate(RANDOM), new RoomName("활성방"), 1L, Instant.now());
        Room saved = roomRepository.save(active);

        assertThatCode(() -> subscribeRoomEventsService.validate(saved.getId()))
            .doesNotThrowAnyException();
    }
}
