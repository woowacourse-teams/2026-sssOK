package com.sssok.application.room;

import com.sssok.application.auth.exception.UnauthorizedException;
import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.member.Member;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomMember;
import java.time.Instant;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 방 입장
// 같은 사람이 다시 호출해도 참여 기록이 늘어나지 않는다
@Service
@RequiredArgsConstructor
public class JoinRoomService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final MemberRepository memberRepository;

    @Transactional
    public JoinRoomResult join(Long roomId, Long memberId, String passcode) {
        Instant now = Instant.now();
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.canEnter(now)) {
            throw new RoomExpiredException();
        }
        room.verifyEntry(passcode);

        Member member = memberRepository.findById(memberId)
            .orElseThrow(() -> new UnauthorizedException("다시 접속해주세요"));

        Optional<RoomMember> joined = roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId);
        if (joined.isPresent()) {
            return JoinRoomResult.rejoined(room, joined.get(), member);
        }

        // 실제로 행을 넣은 쪽만 신규 참여다.
        if (roomMemberRepository.joinIfAbsent(roomId, memberId, now)) {
            return JoinRoomResult.newlyJoined(room, requireJoined(roomId, memberId), member);
        }
        return JoinRoomResult.rejoined(room, requireJoined(roomId, memberId), member);
    }

    private RoomMember requireJoined(Long roomId, Long memberId) {
        return roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(() -> new IllegalStateException(
                "방금 저장한 참여 기록을 찾을 수 없습니다: roomId=%d, memberId=%d".formatted(roomId, memberId)));
    }
}
