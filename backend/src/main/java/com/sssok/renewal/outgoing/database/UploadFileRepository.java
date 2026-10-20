package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UploadFileRepository {
    private final StoredFileJpaRepository storedFileJpaRepository;

    public Optional<StoredFile> findById(Long fileId) {
        return storedFileJpaRepository.findById(fileId).map(this::toDomain);
    }

    public Optional<StoredFile> findByIdForUpdate(Long fileId) {
        return storedFileJpaRepository.findWithLockById(fileId).map(this::toDomain);
    }

    public List<StoredFile> findAllByIdIn(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return storedFileJpaRepository.findAllById(fileIds).stream().map(this::toDomain).toList();
    }

    public StoredFile save(StoredFile file) {
        StoredFileJpaEntity storedFileEntity = storedFileJpaRepository.save(toEntity(file));
        return toDomain(storedFileEntity);
    }

    public List<StoredFile> savePendingUploads(List<StoredFile> pendingUploadFiles) {
        List<StoredFileJpaEntity> pendingUploadEntities = pendingUploadFiles.stream()
            .map(this::toEntity).toList();
        return storedFileJpaRepository.saveAll(pendingUploadEntities).stream()
            .map(this::toDomain).toList();
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
            file.getRetryCount(),
            file.getThumbnailKey() == null ? null : file.getThumbnailKey().value(),
            file.getPreviewKey() == null ? null : file.getPreviewKey().value(),
            file.getWidth(),
            file.getHeight(),
            file.getDurationSeconds(),
            file.getTakenAt(),
            file.getLocation() == null ? null : file.getLocation().latitude(),
            file.getLocation() == null ? null : file.getLocation().longitude()
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
            entity.getRetryCount(),
            entity.getThumbnailKey() == null ? null : new StorageKey(entity.getThumbnailKey()),
            entity.getPreviewKey() == null ? null : new StorageKey(entity.getPreviewKey()),
            entity.getWidth(),
            entity.getHeight(),
            entity.getDurationSeconds(),
            entity.getTakenAt(),
            GeoPoint.ofNullable(entity.getLatitude(), entity.getLongitude())
        );
    }
}
