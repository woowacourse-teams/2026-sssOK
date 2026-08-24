package com.sssok.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// publish()가 실제 Repository 빈을 통해 room_events에 기록하는지 확인하는 통합 테스트.
// 실시간 전달(SseEmitter.send)은 실제 HTTP 연결이 있어야 검증 가능해 API 인수 테스트가 담당한다.
@SpringBootTest
@ActiveProfiles("test")
class InMemorySseEventPublisherTest {

    @Autowired
    InMemorySseEventPublisher sseEventPublisher;

    @Autowired
    RoomEventJpaRepository roomEventJpaRepository;

    @Test
    void 발행하면_room_events에_순서대로_기록된다() {
        Long roomId = 1024L;

        sseEventPublisher.publish(roomId, "media.created", Map.of("mediaId", 5012));
        sseEventPublisher.publish(roomId, "media.ready", Map.of("mediaId", 5012, "status", "READY"));

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);

        assertThat(events).hasSize(2);
        assertThat(events.get(0).getEventType()).isEqualTo("media.created");
        assertThat(events.get(0).getPayload()).contains("5012");
        assertThat(events.get(1).getEventType()).isEqualTo("media.ready");
        assertThat(events.get(1).getId()).isGreaterThan(events.get(0).getId());
    }

    @Test
    void lastEventId_이후_기록만_조회된다() {
        Long roomId = 2048L;

        sseEventPublisher.publish(roomId, "room.updated", Map.of("name", "제주 여행"));
        sseEventPublisher.publish(roomId, "room.updated", Map.of("name", "제주 3박 4일"));
        List<RoomEventJpaEntity> all =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);
        Long firstEventId = all.get(0).getId();

        List<RoomEventJpaEntity> afterFirst =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, firstEventId);

        assertThat(afterFirst).hasSize(1);
        assertThat(afterFirst.get(0).getPayload()).contains("3박 4일");
    }
}
