package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.Room;
import com.sssok.domain.room.RoomCode;
import com.sssok.domain.room.RoomExpiration;
import com.sssok.domain.room.RoomName;
import com.sssok.domain.room.roomstatus.RoomStatus;
import com.sssok.domain.room.UploadPolicy;
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

    @Override
    public Optional<Room> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    // 읽어온 version 을 그대로 실어 보내야 "내가 읽은 뒤 바뀌었는지"를 DB가 판정할 수 있다.
    private RoomJpaEntity toEntity(Room room) {
        return new RoomJpaEntity(
            room.getId(),
            room.getVersion(),
            room.getCode().value(),
            room.getName().value(),
            room.getStatus().name(),
            room.getExpiration().expiresAt(),
            room.getUploadPolicy().name(),
            room.getHostId(),
            room.getCreatedAt(),
            room.getDeletedAt()
        );
    }

    private Room toDomain(RoomJpaEntity entity) {
        return Room.reconstruct(
            entity.getId(),
            entity.getVersion(),
            new RoomCode(entity.getCode()),
            new RoomName(entity.getName()),
            RoomStatus.from(entity.getStatus()),
            new RoomExpiration(entity.getExpiresAt()),
            UploadPolicy.valueOf(entity.getUploadPolicy()),
            entity.getHostId(),
            entity.getCreatedAt(),
            entity.getDeletedAt()
        );
    }
}