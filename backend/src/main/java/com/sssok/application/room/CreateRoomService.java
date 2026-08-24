package com.sssok.application.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.EntryPassword;
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

    public RoomDetail create(Long hostId, String name, String rawEntryPassword) {
        RoomCode code = RoomCode.generate(randomGenerator);
        EntryPassword entryPassword = toEntryPassword(rawEntryPassword, code);
        Room room = Room.create(code, new RoomName(name), hostId, entryPassword, Instant.now());
        return roomDetailReader.read(roomRepository.save(room), hostId);
    }

    // 항목을 아예 안 보냈을 때만 암호 없는 방이다.
    // 공백만 보낸 경우는 잠갔다고 착각하지 않도록 EntryPassword 가 걸러낸다.
    private EntryPassword toEntryPassword(String rawEntryPassword, RoomCode code) {
        if (rawEntryPassword == null) {
            return null;
        }
        return EntryPassword.of(rawEntryPassword, code);
    }
}
