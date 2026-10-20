package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StoredFile;
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
        return storedFileJpaRepository.findById(fileId).map(StoredFileJpaEntity::toDomain);
    }

    public Optional<StoredFile> findByIdForUpdate(Long fileId) {
        return storedFileJpaRepository.findWithLockById(fileId).map(StoredFileJpaEntity::toDomain);
    }

    public List<StoredFile> findAllByIdIn(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return storedFileJpaRepository.findAllById(fileIds).stream().map(StoredFileJpaEntity::toDomain).toList();
    }

    public StoredFile save(StoredFile file) {
        StoredFileJpaEntity storedFileEntity = storedFileJpaRepository.save(StoredFileJpaEntity.from(file));
        return storedFileEntity.toDomain();
    }

    public List<StoredFile> savePendingUploads(List<StoredFile> pendingUploadFiles) {
        List<StoredFileJpaEntity> pendingUploadEntities = pendingUploadFiles.stream()
            .map(StoredFileJpaEntity::from).toList();
        return storedFileJpaRepository.saveAll(pendingUploadEntities).stream()
            .map(StoredFileJpaEntity::toDomain).toList();
    }

}
