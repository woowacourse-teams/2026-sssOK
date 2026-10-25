package com.sssok.application.room;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.media.MediaUrlResolver;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.member.Member;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomMembershipService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MemberRepository memberRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final MediaUrlResolver mediaUrlResolver;

    @Transactional(readOnly = true)
    public List<MyRoomSummary> getMyRooms(Long memberId) {
        return roomMemberRepository.findRoomsByMemberId(memberId).stream()
            .map(this::toMyRoomSummary)
            .toList();
    }

    @Transactional
    public JoinRoomResult join(Long roomId, Long memberId) {
        Room room = getRoom(roomId);
        if (!room.canEnter()) {
            throw new RoomExpiredException();
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new UnauthorizedException("다시 접속해주세요"));

        JoinRoomResult result = joinOrGetMembership(room, member);
        if (result.newlyJoined()) {
            eventPublisher.publishEvent(RoomMemberJoinedEvent.from(result));
        }
        return result;
    }



    private Room getRoom(Long roomId) {
        return roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
    }

    private MyRoomSummary toMyRoomSummary(RoomMembershipSummary summary) {
        String thumbnailUrl = mediaUrlResolver.resolveThumbnailUrl(summary.thumbnailKey());
        return new MyRoomSummary(
            summary.roomId(),
            summary.code(),
            summary.name(),
            summary.isHost(),
            summary.mediaCount(),
            summary.memberCount(),
            thumbnailUrl
        );
    }

    private JoinRoomResult joinOrGetMembership(Room room, Member member) {
        Long roomId = room.getId();
        Long memberId = member.getId();
        while (true) {
            Optional<RoomMember> existingMembership = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId);
            if (existingMembership.isPresent()) {
                return JoinRoomResult.rejoined(room, existingMembership.get(), member);
            }

            // DB 정밀도에 맞춰 최초 입장과 재입장의 응답 시각을 동일하게 한다.
            Instant joinedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
            if (roomMemberRepository.joinIfAbsent(roomId, memberId, joinedAt)) {
                return JoinRoomResult.newlyJoined(room, RoomMember.join(roomId, memberId, joinedAt), member);
            }
            // 다른 요청이 먼저 입장한 뒤 나갔을 수 있다. 기록이 반드시 있다고 가정하지 않고 다시 확인한다.
        }
    }
}
