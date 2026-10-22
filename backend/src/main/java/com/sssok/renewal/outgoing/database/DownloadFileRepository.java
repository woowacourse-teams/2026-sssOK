package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class DownloadFileRepository {
    private final StoredFileJpaRepository storedFileJpaRepository;

    public Optional<StoredFile> findById(Long fileId) {
        return storedFileJpaRepository.findById(fileId).map(StoredFileJpaEntity::toDomain);
    }

    // IN () 은 유효한 SQL 이 아니라 빈 목록이면 조회하지 않는다.
    public List<StoredFile> findAllByRoomIdAndIdIn(Long roomId, Collection<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return storedFileJpaRepository.findAllByRoomIdAndIdIn(roomId, fileIds).stream()
            .map(StoredFileJpaEntity::toDomain).toList();
    }

    public List<StoredFile> findAllByIdIn(List<Long> fileIds) {
        if (fileIds.isEmpty()) {
            return List.of();
        }
        return storedFileJpaRepository.findAllById(fileIds).stream()
            .map(StoredFileJpaEntity::toDomain).toList();
    }
}
