package com.sssok.application.port.out;

import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import java.util.Optional;

// 방 영속화 출력
public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findByCode(RoomCode code);
}
