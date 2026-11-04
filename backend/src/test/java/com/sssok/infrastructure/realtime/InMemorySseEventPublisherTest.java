package com.sssok.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.RoomMember;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.support.H2IntegrationTest;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

// publish()가 실제 Repository 빈을 통해 room_events에 기록하는지 확인하는 통합 테스트.
// 실시간 전달(SseEmitter.send)은 실제 HTTP 연결이 있어야 검증 가능해 API 인수 테스트가 담당한다.
@H2IntegrationTest
class InMemorySseEventPublisherTest {

    @Autowired
    InMemorySseEventPublisher sseEventPublisher;

    @Autowired
    RoomMemberRepository memberships;

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

    @Test
    void 수신자를_지정해_발행하면_그_회원이_기록되고_방_전체_발행은_수신자가_없다() {
        Long roomId = 16384L;

        sseEventPublisher.publish(roomId, "room.updated", Map.of("name", "전체"));
        sseEventPublisher.publish(roomId, "organize.job.updated", Map.of("jobId", "x"), 7L);

        List<RoomEventJpaEntity> events = roomEventJpaRepository.findByRoomIdAndIdGreaterThanOrderById(roomId, 0L);
        assertThat(events).extracting(RoomEventJpaEntity::getRecipientMemberId).containsExactly(null, 7L);
    }

    @Test
    void 재연결_때_놓친_이벤트는_방_전체_이벤트와_내_전용_이벤트만_받는다() {
        Long roomId = 32768L;
        sseEventPublisher.publish(roomId, "room.updated", Map.of("name", "전체"));
        sseEventPublisher.publish(roomId, "organize.job.updated", Map.of("jobId", "mine"), 7L);
        sseEventPublisher.publish(roomId, "organize.job.updated", Map.of("jobId", "others"), 8L);

        List<RoomEventJpaEntity> mine = roomEventJpaRepository.findVisibleAfter(roomId, 0L, 7L);
        List<RoomEventJpaEntity> stranger = roomEventJpaRepository.findVisibleAfter(roomId, 0L, 9L);

        assertThat(mine).extracting(RoomEventJpaEntity::getPayload)
            .anyMatch(payload -> payload.contains("mine")).noneMatch(payload -> payload.contains("others"))
            .anyMatch(payload -> payload.contains("전체"));
        assertThat(stranger).extracting(RoomEventJpaEntity::getEventType).containsExactly("room.updated");
    }

    @Test
    void 놓친_이벤트_조회는_lastEventId_이후만_고른다() {
        Long roomId = 65536L;
        sseEventPublisher.publish(roomId, "organize.job.updated", Map.of("n", 1), 7L);
        sseEventPublisher.publish(roomId, "organize.job.updated", Map.of("n", 2), 7L);
        Long firstId = roomEventJpaRepository.findVisibleAfter(roomId, 0L, 7L).get(0).getId();

        assertThat(roomEventJpaRepository.findVisibleAfter(roomId, firstId, 7L)).hasSize(1);
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
        memberships.save(RoomMember.join(roomId, 1L, Instant.now(), new Nickname("참여자")));
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
                    sseEventPublisher.subscribe(roomId, 1L, 0L);
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
