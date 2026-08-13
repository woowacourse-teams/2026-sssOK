package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 코드로 조회
@Service
@RequiredArgsConstructor
public class GetRoomService {

    private final RoomRepository roomRepository;

    public Room getByCode(RoomCode code) {
        return roomRepository.findByCode(code)
            .orElseThrow(() -> new RoomNotFoundException(code));
    }
}
