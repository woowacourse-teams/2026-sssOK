package com.sssok.infrastructure.realtime;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomEventJpaRepository extends JpaRepository<RoomEventJpaEntity, Long> {

    List<RoomEventJpaEntity> findByRoomIdAndIdGreaterThanOrderById(Long roomId, Long id);
}
