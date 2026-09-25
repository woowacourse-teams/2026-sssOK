package com.sssok.infrastructure.persistence.file;

import com.sssok.application.media.MediaUploaderFilter;
import com.sssok.application.port.out.FileRepository;
import com.sssok.application.port.out.FileRepository.StuckMedia;
import com.sssok.domain.file.FileSize;
import com.sssok.domain.file.GeoPoint;
import com.sssok.domain.file.MediaType;
import com.sssok.domain.file.StorageKey;
import com.sssok.domain.file.StoredFile;
import com.sssok.domain.file.UploadStatus;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
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
    public Optional<StoredFile> findByIdForUpdate(Long id) {
        return jpaRepository.findWithLockById(id).map(this::toDomain);
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
    public List<StoredFile> findAllByRoomIdAndIdIn(Long roomId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository.findAllByRoomIdAndIdIn(roomId, ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<StoredFile> findAllByRoomIdAndIdNotIn(Long roomId, Collection<Long> ids) {
        if (ids.isEmpty()) {
            return findAllByRoomId(roomId);
        }
        return jpaRepository.findAllByRoomIdAndIdNotIn(roomId, ids).stream().map(this::toDomain).toList();
    }

    @Override
    public List<StoredFile> findAllByRoomIdAndStatusInOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses) {
        return jpaRepository
            .findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(roomId, names(statuses)).stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<StoredFile> findPageByRoomIdAndStatusInOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses, Instant lastCreatedAt,
        Long lastMediaId, int limit) {
        List<StoredFileJpaEntity> entities = lastCreatedAt == null
            ? jpaRepository.findAllByRoomIdAndStatusInOrderByCreatedAtDescIdDesc(
                roomId, names(statuses), Limit.of(limit))
            : jpaRepository.findNextPageByRoomIdAndStatusInOrderByNewest(
                roomId, names(statuses), lastCreatedAt, lastMediaId, limit);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRoomIdAndStatusIn(Long roomId, Collection<UploadStatus> statuses) {
        return jpaRepository.countByRoomIdAndStatusIn(roomId, names(statuses));
    }

    @Override
    public List<StoredFile> findPageByRoomIdAndUploaderOrderByNewest(
        Long roomId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader, Instant lastCreatedAt, Long lastMediaId, int limit) {
        List<StoredFileJpaEntity> entities = lastCreatedAt == null
            ? jpaRepository.findFirstPageByRoomIdAndUploaderOrderByNewest(
                roomId, names(statuses), requesterId, uploader.name(), limit)
            : jpaRepository.findNextPageByRoomIdAndUploaderOrderByNewest(
                roomId, names(statuses), requesterId, uploader.name(),
                lastCreatedAt, lastMediaId, limit);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRoomIdAndUploader(
        Long roomId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader) {
        return jpaRepository.countByRoomIdAndUploader(
            roomId, names(statuses), requesterId, uploader.name());
    }

    @Override
    public List<StoredFile> findAllByRoomIdAndIdInAndStatusInOrderByNewest(
        Long roomId, Collection<Long> ids, Collection<UploadStatus> statuses) {
        // IN () 은 유효한 SQL 이 아니라, 빈 목록을 그대로 넘기면 드라이버가 오류를 낸다.
        if (ids.isEmpty()) {
            return List.of();
        }
        return jpaRepository
            .findAllByRoomIdAndIdInAndStatusInOrderByCreatedAtDescIdDesc(roomId, ids, names(statuses))
            .stream()
            .map(this::toDomain)
            .toList();
    }

    @Override
    public List<StoredFile> findPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
        Long roomId, Long folderId, Collection<UploadStatus> statuses,
        Instant lastCreatedAt, Long lastMediaId, int limit) {
        List<StoredFileJpaEntity> entities = lastCreatedAt == null
            ? jpaRepository.findFirstPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
                roomId, folderId, names(statuses), limit)
            : jpaRepository.findNextPageByRoomIdAndFolderIdAndStatusInOrderByNewest(
                roomId, folderId, names(statuses), lastCreatedAt, lastMediaId, limit);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRoomIdAndFolderIdAndStatusIn(
        Long roomId, Long folderId, Collection<UploadStatus> statuses) {
        return jpaRepository.countByRoomIdAndFolderIdAndStatusIn(
            roomId, folderId, names(statuses));
    }

    @Override
    public List<StoredFile> findPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
        Long roomId, Long folderId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader, Instant lastCreatedAt, Long lastMediaId, int limit) {
        List<StoredFileJpaEntity> entities = lastCreatedAt == null
            ? jpaRepository.findFirstPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
                roomId, folderId, names(statuses), requesterId, uploader.name(), limit)
            : jpaRepository.findNextPageByRoomIdAndFolderIdAndUploaderOrderByNewest(
                roomId, folderId, names(statuses), requesterId, uploader.name(),
                lastCreatedAt, lastMediaId, limit);
        return entities.stream().map(this::toDomain).toList();
    }

    @Override
    public long countByRoomIdAndFolderIdAndUploader(
        Long roomId, Long folderId, Collection<UploadStatus> statuses, Long requesterId,
        MediaUploaderFilter uploader) {
        return jpaRepository.countByRoomIdAndFolderIdAndUploader(
            roomId, folderId, names(statuses), requesterId, uploader.name());
    }

    @Override
    public List<StuckMedia> findStuckInProcessing(Instant stuckBefore, int limit) {
        return jpaRepository.findStuckInProcessing(
                UploadStatus.PROCESSING.name(), stuckBefore, Limit.of(limit)).stream()
            .map(row -> new StuckMedia(row.id(), MediaType.valueOf(row.mediaType())))
            .toList();
    }

    @Override
    public void deleteAllByRoomId(Long roomId) {
        jpaRepository.deleteAllByRoomId(roomId);
    }

    @Override
    public void deleteAllByIdIn(List<Long> ids) {
        if (!ids.isEmpty()) {
            jpaRepository.deleteAllByIdInBatch(ids);
        }
    }

    // 상태를 문자열 컬럼으로 저장하고 있어, 조회 조건도 이름으로 맞춰 넘긴다.
    private List<String> names(Collection<UploadStatus> statuses) {
        return statuses.stream().map(UploadStatus::name).toList();
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
