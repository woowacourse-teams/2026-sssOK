package com.sssok.renewal.outgoing.database;

import com.sssok.domain.file.StoredFile;
import com.sssok.infrastructure.persistence.file.StoredFileJpaEntity;
import com.sssok.infrastructure.persistence.file.StoredFileJpaRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.hibernate.Session;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UploadFileRepository {

    // 한 번의 등록(최대 수백 장)에서 파일 UPDATE 를 묶어 보내는 JDBC 배치 크기.
    private static final int REGISTRATION_JDBC_BATCH_SIZE = 32;

    private final StoredFileJpaRepository storedFileJpaRepository;
    private final EntityManager entityManager;

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

    // 등록 트랜잭션에서만 호출한다. 삭제와 같은 ID 순서로 잠금을 잡아 서로 다른 순서로 요청한 등록끼리
    // 교착하지 않게 한다. 세션의 JDBC 배치 크기는 이 트랜잭션에만 적용된다.
    public List<StoredFile> findAllForRegistration(List<Long> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        entityManager.unwrap(Session.class).setJdbcBatchSize(REGISTRATION_JDBC_BATCH_SIZE);
        return entityManager
            .createQuery("select f from StoredFileJpaEntity f where f.id in :ids order by f.id",
                StoredFileJpaEntity.class)
            .setParameter("ids", ids)
            .setLockMode(LockModeType.PESSIMISTIC_WRITE)
            .getResultList().stream()
            .map(StoredFileJpaEntity::toDomain)
            .toList();
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
