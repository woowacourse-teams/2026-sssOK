package com.sssok.application.room;

import com.sssok.application.port.out.MemberRepository;
import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.domain.room.Room;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// 방 응답에 붙는 조회성 값을 한곳에서 채운다.
@Component
@RequiredArgsConstructor
public class RoomDetailReader {

    private final MemberRepository memberRepository;
    private final RoomMemberRepository roomMemberRepository;

    public RoomDetail read(Room room, Long requesterId) {
        return new RoomDetail(room, hostNameOf(room), isJoined(room, requesterId));
    }

    private String hostNameOf(Room room) {
        return memberRepository.findById(room.getHostId())
            .map(member -> member.getDisplayName().value())
            .orElse(null);
    }

    private boolean isJoined(Room room, Long requesterId) {
        return requesterId != null
            && roomMemberRepository.findByRoomIdAndMemberId(room.getId(), requesterId).isPresent();
    }
}
