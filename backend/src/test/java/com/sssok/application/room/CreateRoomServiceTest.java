package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.InvalidRoomExpirationException;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Repository + Service 통합 테스트 (H2)
// 자동 입장은 방금 만든 roomId에 대한 단순 save()라 동시성 경쟁이 없어 PostgreSQL 전용 쿼리가 필요 없다.
@SpringBootTest
@ActiveProfiles("test")
class CreateRoomServiceTest {

    private static final Long HOST = 1L;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Test
    void uploadPolicy와_expiryHours를_생략하면_기본값이_적용된다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", null, null);

        assertThat(detail.room().getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
        assertThat(detail.room().getExpiration().expiresAt())
            .isCloseTo(Instant.now().plus(Duration.ofHours(24)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void uploadPolicy와_expiryHours를_보내면_그대로_반영된다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", "host", 72);

        assertThat(detail.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
        assertThat(detail.room().getExpiration().expiresAt())
            .isCloseTo(Instant.now().plus(Duration.ofHours(72)), within(1, ChronoUnit.MINUTES));
    }

    @Test
    void 알_수_없는_업로드_권한이면_예외() {
        assertThatThrownBy(() -> createRoomService.create(HOST, "우테코 회식", "nobody", null))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }

    @Test
    void 허용되지_않은_만료_시간이면_예외() {
        assertThatThrownBy(() -> createRoomService.create(HOST, "우테코 회식", null, 48))
            .isInstanceOf(InvalidRoomExpirationException.class);
    }

    @Test
    void 방을_생성하면_방장이_자동으로_참여자로_등록된다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", null, null);

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(detail.room().getId(), HOST)).isPresent();
        assertThat(detail.joined()).isTrue();
    }
}
