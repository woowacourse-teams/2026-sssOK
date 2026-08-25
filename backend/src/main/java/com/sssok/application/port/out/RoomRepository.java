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

    // 보존 기간이 지나 영구 삭제할 방. 방장이 지운 방과 그냥 만료된 방을 함께 찾는다.
    List<Room> findAllPurgeTargets(Instant threshold);

    void delete(Room room);
}
