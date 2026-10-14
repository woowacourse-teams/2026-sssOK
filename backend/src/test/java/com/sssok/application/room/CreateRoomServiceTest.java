package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import com.sssok.domain.room.roomstatus.ActiveRoomStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.sssok.support.H2IntegrationTest;

// Repository + Service 통합 테스트 (H2)
// 자동 입장은 방금 만든 roomId에 대한 단순 save()라 동시성 경쟁이 없어 PostgreSQL 전용 쿼리가 필요 없다.
@H2IntegrationTest
class CreateRoomServiceTest {

    private static final Long HOST = 1L;

    @Autowired
    CreateRoomService createRoomService;

    @Autowired
    RoomMemberRepository roomMemberRepository;

    @Test
    void uploadPolicy를_생략하면_ANYONE이_적용되고_ACTIVE_상태로_만들어진다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", null);

        assertThat(detail.room().getUploadPolicy()).isEqualTo(UploadPolicy.ANYONE);
        assertThat(detail.room().getStatus()).isSameAs(ActiveRoomStatus.INSTANCE);
    }

    @Test
    void uploadPolicy를_보내면_그대로_반영된다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", "host");

        assertThat(detail.room().getUploadPolicy()).isEqualTo(UploadPolicy.HOST_ONLY);
    }

    @Test
    void 알_수_없는_업로드_권한이면_예외() {
        assertThatThrownBy(() -> createRoomService.create(HOST, "우테코 회식", "nobody"))
            .isInstanceOf(InvalidUploadPolicyException.class);
    }

    @Test
    void 방을_생성하면_방장이_자동으로_참여자로_등록된다() {
        RoomDetail detail = createRoomService.create(HOST, "우테코 회식", null);

        assertThat(roomMemberRepository.findByRoomIdAndMemberId(detail.room().getId(), HOST)).isPresent();
        assertThat(detail.joined()).isTrue();
    }
}
