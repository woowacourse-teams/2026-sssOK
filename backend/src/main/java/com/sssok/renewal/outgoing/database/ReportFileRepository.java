package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ReportFileRepository {
    private final StoredFileJpaRepository storedFileJpaRepository;

    public Optional<StoredFile> findById(Long fileId) {
        return storedFileJpaRepository.findById(fileId).map(StoredFileJpaEntity::toDomain);
    }

    public Optional<StoredFile> findByIdForUpdate(Long fileId) {
        return storedFileJpaRepository.findWithLockById(fileId).map(StoredFileJpaEntity::toDomain);
    }
}
