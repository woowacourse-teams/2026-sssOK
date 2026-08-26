package com.sssok.infrastructure.persistence.folder;

import com.sssok.application.port.out.FolderRepository;
import com.sssok.domain.folder.Folder;
import com.sssok.domain.folder.FolderName;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FolderRepositoryAdapter implements FolderRepository {

    private final FolderJpaRepository jpaRepository;

    @Override
    public Folder save(Folder folder) {
        FolderJpaEntity saved = jpaRepository.save(toEntity(folder));
        return toDomain(saved);
    }

    @Override
    public Optional<Folder> findByRoomIdAndName(Long roomId, String name) {
        return jpaRepository.findByRoomIdAndName(roomId, name).map(this::toDomain);
    }

    private FolderJpaEntity toEntity(Folder folder) {
        return new FolderJpaEntity(
            folder.getId(),
            folder.getRoomId(),
            folder.getName().value(),
            folder.getCreatedAt()
        );
    }

    private Folder toDomain(FolderJpaEntity entity) {
        return Folder.reconstruct(
            entity.getId(),
            entity.getRoomId(),
            new FolderName(entity.getName()),
            entity.getCreatedAt()
        );
    }
}
