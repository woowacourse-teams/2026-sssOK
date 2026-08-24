package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.application.room.exception.RoomExpiredException;
import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.domain.room.Room;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// SSE 구독 전 방이 존재하고 이용 가능한지 확인한다.
// 멤버십(입장 여부) 검증은 이번 범위에서는 하지 않는다 — #45(입장 API)가 아직 없어
// "이 계정이 이 방에 입장했는지"를 판단할 데이터가 없다. #45 완료 후 이어붙인다.
@Service
@RequiredArgsConstructor
public class SubscribeRoomEventsService {

    private final RoomRepository roomRepository;

    public void validate(Long roomId) {
        Room room = roomRepository.findById(roomId)
            .orElseThrow(() -> new RoomNotFoundException(roomId));
        if (!room.canEnter(Instant.now())) {
            throw new RoomExpiredException();
        }
    }
}
