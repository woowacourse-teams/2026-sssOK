package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomStatus;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RoomRepositoryAdapter implements RoomRepository {

    private final RoomJpaRepository jpaRepository;

    @Override
    public Room save(Room room) {
        RoomJpaEntity saved = jpaRepository.save(toEntity(room));
        return toDomain(saved);
    }

    @Override
    public Optional<Room> findByCode(RoomCode code) {
        return jpaRepository.findByCode(code.value()).map(this::toDomain);
    }

    private RoomJpaEntity toEntity(Room room) {
        return new RoomJpaEntity(
            room.getId(),
            room.getCode().value(),
            room.getStatus().name(),
            room.getCreatedAt()
        );
    }

    private Room toDomain(RoomJpaEntity entity) {
        return Room.reconstruct(
            entity.getId(),
            new RoomCode(entity.getCode()),
            RoomStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt()
        );
    }
}
