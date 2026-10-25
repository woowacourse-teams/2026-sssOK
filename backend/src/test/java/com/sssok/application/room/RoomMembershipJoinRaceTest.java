package com.sssok.application.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sssok.application.media.MediaUrlResolver;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.member.Member;
import com.sssok.domain.member.Nickname;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

class RoomMembershipJoinRaceTest {
    private final RoomRepository roomRepository = mock(RoomRepository.class);
    private final RoomMemberRepository roomMemberRepository = mock(RoomMemberRepository.class);
    private final MemberRepository memberRepository = mock(MemberRepository.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final RoomMembershipService roomMembershipService = new RoomMembershipService(
        roomRepository, roomMemberRepository, memberRepository, eventPublisher, mock(MediaUrlResolver.class));

    @BeforeEach
    void setUp() {
        Room room = mock(Room.class);
        when(room.getId()).thenReturn(10L);
        when(room.canEnter()).thenReturn(true);
        when(roomRepository.findById(10L)).thenReturn(Optional.of(room));
        when(memberRepository.findById(2L)).thenReturn(Optional.of(
            Member.reconstruct(2L, new Nickname("참여자"), Instant.now())));
    }

    @Test
    void 삽입에_성공하면_기록을_다시_조회하지_않는다() {
        when(roomMemberRepository.findByRoomIdAndMemberId(10L, 2L)).thenReturn(Optional.empty());
        when(roomMemberRepository.joinIfAbsent(eq(10L), eq(2L), any())).thenReturn(true);
        var result = roomMembershipService.join(10L, 2L);
        assertThat(result.newlyJoined()).isTrue();
        assertThat(result.joinedAt().getNano() % 1000).isZero();
        verify(roomMemberRepository, times(1)).findByRoomIdAndMemberId(10L, 2L);
        verify(eventPublisher).publishEvent(any(RoomMemberJoinedEvent.class));
    }

    @Test
    void 다른_입장이_먼저_성공한_뒤_나가서_기록이_없으면_새로_참여한다() {
        when(roomMemberRepository.findByRoomIdAndMemberId(10L, 2L)).thenReturn(Optional.empty());
        when(roomMemberRepository.joinIfAbsent(eq(10L), eq(2L), any())).thenReturn(false, true);
        assertThat(roomMembershipService.join(10L, 2L).newlyJoined()).isTrue();
        verify(roomMemberRepository, times(2)).findByRoomIdAndMemberId(10L, 2L);
        verify(roomMemberRepository, times(2)).joinIfAbsent(eq(10L), eq(2L), any());
        verify(eventPublisher, times(1)).publishEvent(any(RoomMemberJoinedEvent.class));
    }

    @Test
    void 다른_입장이_먼저_성공했고_기록이_남아_있으면_기존_참여시각을_반환한다() {
        Instant joinedAt = Instant.parse("2026-10-25T00:00:00Z");
        when(roomMemberRepository.findByRoomIdAndMemberId(10L, 2L))
            .thenReturn(Optional.empty())
            .thenReturn(Optional.of(RoomMember.reconstruct(30L, 10L, 2L, joinedAt)));
        when(roomMemberRepository.joinIfAbsent(eq(10L), eq(2L), any())).thenReturn(false);
        var result = roomMembershipService.join(10L, 2L);
        assertThat(result.newlyJoined()).isFalse();
        assertThat(result.joinedAt()).isEqualTo(joinedAt);
        verifyNoInteractions(eventPublisher);
    }
}
