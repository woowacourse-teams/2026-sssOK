package com.sssok.application.room;

import com.sssok.application.media.MediaUrlResolver;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomHostCannotLeaveException;
import com.sssok.application.room.exception.RoomModifiedException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomMember;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
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
    public JoinRoomResult join(Long roomId, RoomCode code, Long memberId) {
        return join(roomId, code, memberId, null);
    }

    @Transactional
    public JoinRoomResult join(Long roomId, RoomCode code, Long memberId, String nickname) {
        Nickname requestedNickname = nickname == null ? null : new Nickname(nickname);
        Room room = getRoom(roomId);
        // roomId 는 순차 증가라 추측할 수 있어, 공유 링크로 받은 코드가 맞을 때만 입장시킨다.
        // 틀리면 방이 없을 때와 같은 응답을 줘서 방의 존재·만료 여부를 드러내지 않는다.
        if (!room.getCode().matches(code)) {
            throw new RoomNotFoundException(roomId);
        }
        if (!room.canEnter()) {
            throw new RoomExpiredException();
        }
        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new UnauthorizedException("다시 접속해주세요"));

        JoinRoomResult result = joinOrGetMembership(room, member, requestedNickname);
        if (result.newlyJoined()) {
            eventPublisher.publishEvent(RoomMemberJoinedEvent.from(result));
        }
        return result;
    }

    @Transactional
    public void leave(Long roomId, Long memberId) {
        Room room = getRoom(roomId);
        if (!room.isDeleted() && room.isHost(memberId)) {
            throw new RoomHostCannotLeaveException();
        }
        roomMemberRepository.deleteMembership(roomId, memberId)
            .ifPresent(membershipId -> eventPublisher.publishEvent(new RoomMemberLeftEvent(roomId, membershipId)));
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

    private JoinRoomResult joinOrGetMembership(Room room, Member member, Nickname requestedNickname) {
        Nickname roomNickname = requestedNickname == null ? member.getDisplayName() : requestedNickname;
        Long roomId = room.getId();
        Long memberId = member.getId();
        Optional<RoomMember> existingMembership = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId);
        if (existingMembership.isPresent()) {
            return JoinRoomResult.rejoined(room, existingMembership.get(), member);
        }

        // DB 정밀도에 맞춰 최초 입장과 재입장의 응답 시각을 동일하게 한다.
        Instant joinedAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
        if (roomMemberRepository.joinIfAbsent(roomId, memberId, joinedAt, roomNickname)) {
            return JoinRoomResult.newlyJoined(room, RoomMember.join(roomId, memberId, joinedAt, roomNickname), member);
        }

        // 중복 입장이면 기존 기록을 한 번만 확인한다. 그 사이 나갔다면 재요청을 안내한다.
        RoomMember membership = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(RoomModifiedException::new);
        return JoinRoomResult.rejoined(room, membership, member);
    }
}
