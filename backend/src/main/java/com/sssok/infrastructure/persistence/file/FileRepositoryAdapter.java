package com.sssok.infrastructure.persistence.file;

import com.sssok.application.port.out.FileRepository;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class FileRepositoryAdapter implements FileRepository {

    private final StoredFileJpaRepository jpaRepository;

    @Override
    public StoredFile save(StoredFile storedFile) {
        return toDomain(jpaRepository.save(toEntity(storedFile)));
    }

    @Override
    public List<StoredFile> saveAll(List<StoredFile> storedFiles) {
        return jpaRepository.saveAll(storedFiles.stream().map(this::toEntity).toList()).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public Optional<StoredFile> findById(Long id) {
        return jpaRepository.findById(id).map(this::toDomain);
    }

    @Override
    public List<StoredFile> findAllByIdIn(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllById(ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<Long> findExistingIds(List<Long> ids) {
        return jpaRepository.findAllById(ids).stream()
            .map(StoredFileJpaEntity::getId)
            .toList();
    }

    @Override
    public List<StoredFile> findAllByRoomId(Long roomId) {
        return jpaRepository.findAllByRoomId(roomId).stream().map(this::toDomain).toList();
    }

    @Override
    public void deleteAllByRoomId(Long roomId) {
        jpaRepository.deleteAllByRoomId(roomId);
    }

    private StoredFileJpaEntity toEntity(StoredFile file) {
        return new StoredFileJpaEntity(
            file.getId(),
            file.getRoomId(),
            file.getUploaderId(),
            file.getOriginalFileName(),
            file.getMediaType().name(),
            file.getFileSize().bytes(),
            file.getStorageKey().value(),
            file.getFolderId(),
            file.getStatus().name(),
            file.getCreatedAt(),
            file.getReservedAt(),
            file.getRetryCount()
        );
    }

    private StoredFile toDomain(StoredFileJpaEntity entity) {
        return StoredFile.reconstruct(
            entity.getId(),
            entity.getRoomId(),
            entity.getUploaderId(),
            entity.getOriginalFileName(),
            MediaType.valueOf(entity.getMediaType()),
            new FileSize(entity.getFileSizeBytes()),
            new StorageKey(entity.getStorageKey()),
            entity.getFolderId(),
            UploadStatus.valueOf(entity.getStatus()),
            entity.getCreatedAt(),
            entity.getReservedAt(),
            entity.getRetryCount()
        );
    }
}
