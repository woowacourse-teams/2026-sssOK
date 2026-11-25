package com.sssok.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.RoomMember;
import com.sssok.renewal.outgoing.monitoring.SsePublishMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

// 발행 구간 지표(#833)가 발행·heartbeat·실패한 send 마다 남는지 본다.
// 연결 전 SseEmitter 는 send 를 버퍼에 쌓아 성공하고, complete() 한 SseEmitter 는 send 가 실패한다.
class InMemorySseEventPublisherMetricsTest {
    private static final long ROOM_ID = 1L;

    private final RoomEventJpaRepository roomEvents = mock(RoomEventJpaRepository.class);
    private final RoomMemberRepository roomMembers = mock(RoomMemberRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final InMemorySseEventPublisher publisher = new InMemorySseEventPublisher(
        roomEvents, new ObjectMapper(), roomMembers, new SsePublishMetrics(registry));

    @BeforeEach
    void setUp() {
        when(roomEvents.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(roomMembers.findByRoomIdAndMemberId(ROOM_ID, 2L))
            .thenReturn(Optional.of(RoomMember.reconstruct(10L, ROOM_ID, 2L, Instant.now())));
        when(roomMembers.findByRoomIdAndMemberId(ROOM_ID, 3L))
            .thenReturn(Optional.of(RoomMember.reconstruct(11L, ROOM_ID, 3L, Instant.now())));
    }

    @Test
    void 발행하면_락_대기와_보유_저장_send_전달_지표가_남는다() {
        publisher.subscribe(ROOM_ID, 2L, null);
        publisher.subscribe(ROOM_ID, 3L, null);

        publisher.publish(ROOM_ID, "media.created", Map.of("mediaId", 5));

        assertThat(timerCount("sssok.sse.publish.lock.wait", "event_type", "media.created")).isEqualTo(1);
        assertThat(timerCount("sssok.sse.publish.lock.hold", "event_type", "media.created")).isEqualTo(1);
        assertThat(registry.get("sssok.sse.publish.save").timer().count()).isEqualTo(1);
        assertThat(sendCount("event", "success")).isEqualTo(2);
        assertThat(timerCount("sssok.sse.delivery", "event_type", "media.created")).isEqualTo(2);
        assertThat(registry.get("sssok.sse.connections").gauge().value()).isEqualTo(2);
    }

    @Test
    void 닫힌_연결에_send_가_실패하면_실패로_세고_전달_지연에는_넣지_않는다() {
        publisher.subscribe(ROOM_ID, 2L, null).complete();
        publisher.subscribe(ROOM_ID, 3L, null);

        publisher.publish(ROOM_ID, "media.created", Map.of("mediaId", 5));

        assertThat(sendCount("event", "failure")).isEqualTo(1);
        assertThat(sendCount("event", "success")).isEqualTo(1);
        assertThat(timerCount("sssok.sse.delivery", "event_type", "media.created")).isEqualTo(1);
        assertThat(registry.get("sssok.sse.connections").gauge().value()).isEqualTo(1);
    }

    @Test
    void heartbeat_는_연결마다_send_와_한_바퀴_시간을_남긴다() {
        publisher.subscribe(ROOM_ID, 2L, null);
        publisher.subscribe(ROOM_ID, 3L, null);

        publisher.sendHeartbeat();

        assertThat(sendCount("heartbeat", "success")).isEqualTo(2);
        assertThat(registry.get("sssok.sse.heartbeat.round").timer().count()).isEqualTo(1);
    }

    private long timerCount(String name, String tagKey, String tagValue) {
        return registry.get(name).tag(tagKey, tagValue).timer().count();
    }

    private double sendCount(String kind, String outcome) {
        return registry.get("sssok.sse.send").tags("kind", kind, "outcome", outcome).counter().count();
    }
}
