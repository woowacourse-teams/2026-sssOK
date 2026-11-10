package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ThumbnailJobRepository {
    private final ThumbnailJobJpaRepository jpaRepository;
    private final EntityManager entityManager;

    public Optional<ThumbnailJob> findById(Long mediaId) {
        return jpaRepository.findById(mediaId).map(ThumbnailJobJpaEntity::toDomain);
    }

    public Optional<ThumbnailJob> lock(Long mediaId) {
        return jpaRepository.lock(mediaId).map(ThumbnailJobJpaEntity::toDomain);
    }

    // JPA 쿼리가 원래 사용하던 FOR UPDATE SKIP LOCKED를 그대로 적용한다.
    public Optional<ThumbnailJob> claimable(ThumbnailKind kind, Instant now) {
        return jpaRepository.claimable(kind.name(), now).map(ThumbnailJobJpaEntity::toDomain);
    }

    public void save(ThumbnailJob job) {
        jpaRepository.save(new ThumbnailJobJpaEntity(job));
    }

    // 호출자가 파일 잠금을 잡은 상태다. 실패하지 않은 기존 작업의 재시도 이력은 유지한다.
    // 작업 조회는 한 번의 쿼리로 하고, 새 작업은 persist 해 flush 때 배치로 저장한다.
    public void enqueueAll(List<ThumbnailJob> jobs) {
        if (jobs.isEmpty()) {
            return;
        }
        Map<Long, ThumbnailJobJpaEntity> existing = jpaRepository
            .findAllById(jobs.stream().map(ThumbnailJob::getMediaId).toList()).stream()
            .collect(Collectors.toMap(ThumbnailJobJpaEntity::getMediaId, Function.identity()));
        for (ThumbnailJob job : jobs) {
            ThumbnailJobJpaEntity prior = existing.get(job.getMediaId());
            if (prior == null) {
                ThumbnailJobJpaEntity created = new ThumbnailJobJpaEntity(job);
                entityManager.persist(created);
                existing.put(job.getMediaId(), created);
            } else if (prior.toDomain().getStatus().isFailed()) {
                prior.apply(job);
            }
        }
    }

    public List<Long> expired(Instant now, int limit) {
        return jpaRepository.expired(now, limit);
    }

    public List<Long> missing(int limit) {
        return jpaRepository.missing(limit);
    }

    public List<Object[]> counts() {
        return jpaRepository.counts();
    }

    public Instant oldestWaiting() {
        return jpaRepository.oldestWaiting();
    }
}
