package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.room.RoomJpaEntity;
import com.sssok.infrastructure.persistence.room.RoomJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportRoomRepository {
    private final RoomJpaRepository roomJpaRepository;

    public Optional<String> findNameById(Long roomId) {
        return roomJpaRepository.findById(roomId).map(RoomJpaEntity::getName);
    }
}
