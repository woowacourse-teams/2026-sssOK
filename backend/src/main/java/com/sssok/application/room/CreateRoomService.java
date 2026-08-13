package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
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
    private final RandomGenerator randomGenerator = new SecureRandom();

    public Room create() {
        RoomCode code = RoomCode.generate(randomGenerator);
        Room room = Room.create(code, Instant.now());
        return roomRepository.save(room);
    }
}
