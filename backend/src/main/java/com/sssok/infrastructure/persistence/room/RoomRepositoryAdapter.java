package com.sssok.infrastructure.persistence.room;

import com.sssok.application.port.out.RoomRepository;
import com.sssok.domain.room.EntryPassword;
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

    private RoomJpaEntity toEntity(Room room) {
        return new RoomJpaEntity(
            room.getId(),
            room.getCode().value(),
            room.getName().value(),
            room.getStatus().name(),
            room.getExpiration().expiresAt(),
            room.getUploadPolicy().name(),
            toHash(room.getEntryPassword()),
            room.getHostId(),
            room.getCreatedAt(),
            room.getDeletedAt()
        );
    }

    private Room toDomain(RoomJpaEntity entity) {
        return Room.reconstruct(
            entity.getId(),
            new RoomCode(entity.getCode()),
            new RoomName(entity.getName()),
            RoomStatus.from(entity.getStatus()),
            new RoomExpiration(entity.getExpiresAt()),
            UploadPolicy.valueOf(entity.getUploadPolicy()),
            toEntryPassword(entity.getEntryPassword()),
            entity.getHostId(),
            entity.getCreatedAt(),
            entity.getDeletedAt()
        );
    }

    private String toHash(EntryPassword entryPassword) {
        if (entryPassword == null) {
            return null;
        }
        return entryPassword.hash();
    }

    private EntryPassword toEntryPassword(String hash) {
        if (hash == null) {
            return null;
        }
        return new EntryPassword(hash);
    }
}