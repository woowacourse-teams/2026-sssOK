package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.random.RandomGenerator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 생성
@Service
@RequiredArgsConstructor
public class CreateRoomService {

    private final RoomRepository roomRepository;
    private final RoomDetailReader roomDetailReader;

    private final RandomGenerator randomGenerator = new SecureRandom();

    public RoomDetail create(Long hostId, String name) {
        Room room = Room.create(RoomCode.generate(randomGenerator), new RoomName(name), hostId, Instant.now());
        return roomDetailReader.read(roomRepository.save(room), hostId);
    }
}
