package com.sssok.renewal.outgoing.database;

import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.UploadPolicy;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.infrastructure.persistence.room.RoomJpaEntity;
import com.sssok.infrastructure.persistence.room.RoomJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class NotificationRoomRepository {
    private final RoomJpaRepository roomJpaRepository;

    public Optional<Room> findById(Long roomId) {
        return roomJpaRepository.findById(roomId).map(this::toDomain);
    }

    private Room toDomain(RoomJpaEntity entity) {
        return Room.reconstruct(
            entity.getId(), entity.getVersion(),
            new RoomCode(entity.getCode()), new RoomName(entity.getName()),
            RoomStatus.from(entity.getStatus()), UploadPolicy.valueOf(entity.getUploadPolicy()),
            entity.getHostId(), entity.getCreatedAt(), entity.getDeletedAt());
    }
}
