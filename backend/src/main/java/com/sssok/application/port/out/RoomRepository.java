package com.sssok.application.port.out;

import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

// 방 영속화 출력
public interface RoomRepository {

    Room save(Room room);

    Optional<Room> findByCode(RoomCode code);

    Optional<Room> findById(Long id);

    // 저장된 상태는 ACTIVE 인데 만료 시각이 now 이전(같은 시각 포함)인 방
    List<Room> findAllExpiredActive(Instant now);
}
