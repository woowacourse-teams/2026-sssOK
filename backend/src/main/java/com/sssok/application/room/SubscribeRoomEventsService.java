package com.sssok.application.room;

import com.sssok.application.port.out.RoomMemberRepository;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomMembershipRequiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// SSE 구독 전 방이 존재하고, 이용 가능하고, 이 회원이 입장한 방인지 확인한다.
@Service
@RequiredArgsConstructor
public class SubscribeRoomEventsService {

    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;

    public void validate(Long roomId, Long memberId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.canEnter(Instant.now())) {
            throw new RoomExpiredException();
        }
        roomMemberRepository.findByRoomIdAndMemberId(roomId, memberId)
            .orElseThrow(RoomMembershipRequiredException::new);
    }
}
