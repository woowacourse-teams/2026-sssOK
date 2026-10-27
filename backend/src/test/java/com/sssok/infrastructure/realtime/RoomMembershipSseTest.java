package com.sssok.infrastructure.realtime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class RoomMembershipSseTest {
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final InMemorySseEventPublisher sseEventPublisher = new InMemorySseEventPublisher(
        mock(RoomEventJpaRepository.class), new ObjectMapper(), roomMemberRepository);

    @Test
    void 나간_참여기록의_모든_연결만_닫고_다른회원과_재참여_연결은_유지한다() throws Exception {
        when(roomMemberRepository.findByRoomIdAndMemberId(1L, 2L)).thenReturn(Optional.of(member(10, 2)));
        when(roomMemberRepository.findByRoomIdAndMemberId(1L, 3L)).thenReturn(Optional.of(member(11, 3)));
        var first = sseEventPublisher.subscribe(1L, 2L, null);
        var second = sseEventPublisher.subscribe(1L, 2L, null);
        var other = sseEventPublisher.subscribe(1L, 3L, null);
        when(roomMemberRepository.findByRoomIdAndMemberId(1L, 2L)).thenReturn(Optional.of(member(12, 2)));
        var rejoined = sseEventPublisher.subscribe(1L, 2L, null);
        sseEventPublisher.disconnectMembership(1L, 10L);
        assertThatThrownBy(() -> first.send("event")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> second.send("event")).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> other.send("event")).doesNotThrowAnyException();
        assertThatCode(() -> rejoined.send("event")).doesNotThrowAnyException();
    }

    @Test
    void 참여하지_않으면_재전송_전에_구독을_거부한다() {
        when(roomMemberRepository.findByRoomIdAndMemberId(1L, 2L)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> sseEventPublisher.subscribe(1L, 2L, 1L))
            .isInstanceOf(RoomMembershipRequiredException.class);
    }

    @Test
    void 구독검증과_등록_사이에_나가도_연결이_남지_않는다() throws Exception {
        CountDownLatch checked = new CountDownLatch(1);
        CountDownLatch proceed = new CountDownLatch(1);
        when(roomMemberRepository.findByRoomIdAndMemberId(1L, 2L)).thenAnswer(invocation -> {
            checked.countDown();
            if (!proceed.await(5, TimeUnit.SECONDS)) {
                throw new AssertionError("구독 대기 시간 초과");
            }
            return Optional.of(member(10, 2));
        });
        try (var pool = Executors.newFixedThreadPool(2)) {
            var subscription = pool.submit(() -> sseEventPublisher.subscribe(1L, 2L, null));
            assertThat(checked.await(5, TimeUnit.SECONDS)).isTrue();
            var leave = pool.submit(() -> sseEventPublisher.disconnectMembership(1L, 10L));
            proceed.countDown();
            var emitter = subscription.get(5, TimeUnit.SECONDS);
            leave.get(5, TimeUnit.SECONDS);
            assertThatThrownBy(() -> emitter.send("event")).isInstanceOf(IllegalStateException.class);
        } finally {
            proceed.countDown();
        }
    }
    private RoomMember member(long id, long userId) {
        return RoomMember.reconstruct(id, 1L, userId, Instant.now());
    }
}
