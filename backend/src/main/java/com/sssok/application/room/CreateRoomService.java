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
    private final RandomGenerator randomGenerator = new SecureRandom();

    public Room create(String name) {
        RoomCode code = RoomCode.generate(randomGenerator);
        // TODO: 실제 방장 식별자는 입장/인증(JWT) 흐름이 붙으면 그걸로 대체한다.
        // 아직 인증 체계가 없어, 방 생성 시점에 임시로 새 식별자를 발급해 방장으로 지정한다.
        Long hostId = randomGenerator.nextLong(1L, Long.MAX_VALUE);
        Room room = Room.create(code, new RoomName(name), hostId, Instant.now());
        return roomRepository.save(room);
    }
}
