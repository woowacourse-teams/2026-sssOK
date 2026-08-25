package com.sssok.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    @Test
    void 직렬화할_수_없는_payload는_room_events에_남기지_않고_예외를_던진다() {
        Long roomId = 4096L;

        assertThatThrownBy(() -> sseEventPublisher.publish(roomId, "broken", new EmptyPayload()))
            .isInstanceOf(IllegalStateException.class);

        assertThat(roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L)).isEmpty();
    }

    // Jackson 기본 설정(FAIL_ON_EMPTY_BEANS)에서, 필드도 getter도 없는 빈은 직렬화할 수 없어 예외가 난다.
    private static final class EmptyPayload {
    }

    @Test
    void 같은_방에_동시에_발행해도_room_events가_유실이나_중복_없이_순서대로_쌓인다() throws InterruptedException {
        Long roomId = 8192L;
        int threadCount = 20;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    sseEventPublisher.publish(roomId, "concurrent.event", Map.of("index", index));
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        List<RoomEventJpaEntity> events =
            roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);
        List<Long> ids = events.stream().map(RoomEventJpaEntity::getId).toList();

        assertThat(events).hasSize(threadCount);
        assertThat(ids).isSorted();
        assertThat(ids).doesNotHaveDuplicates();
    }

    @Test
    void 같은_방에_구독과_발행이_동시에_일어나도_예외_없이_처리된다() throws InterruptedException {
        Long roomId = 16384L;
        int subscriberCount = 10;
        int publisherCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(subscriberCount + publisherCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(subscriberCount + publisherCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < subscriberCount; i++) {
            executor.submit(() -> {
                try {
                    start.await();
                    sseEventPublisher.subscribe(roomId, 0L);
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        for (int i = 0; i < publisherCount; i++) {
            int index = i;
            executor.submit(() -> {
                try {
                    start.await();
                    sseEventPublisher.publish(roomId, "concurrent.event", Map.of("index", index));
                } catch (Throwable t) {
                    errors.add(t);
                } finally {
                    done.countDown();
                }
            });
        }
        start.countDown();
        done.await();
        executor.shutdown();

        assertThat(errors).isEmpty();
    }
}
