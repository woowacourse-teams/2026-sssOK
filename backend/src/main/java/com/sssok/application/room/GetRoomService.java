package com.sssok.application.room;

import com.sssok.application.room.exception.RoomNotFoundException;
import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 코드로 조회. 공유 링크·QR 로 들어오는 진입점
@Service
@RequiredArgsConstructor
public class GetRoomService {

    private final RoomRepository roomRepository;
    private final RoomDetailReader roomDetailReader;

    public RoomDetail getByCode(RoomCode code, Long requesterId) {
        Room room = roomRepository.findByCode(code)
            .orElseThrow(() -> new RoomNotFoundException(code));
        return roomDetailReader.read(room, requesterId);
    }
}
