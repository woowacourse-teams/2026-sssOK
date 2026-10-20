package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.media.thumbnail.ThumbnailJob;
import com.sssok.renewal.domain.media.thumbnail.ThumbnailKind;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class ThumbnailJobRepository {
    private final ThumbnailJobJpaRepository jpaRepository;

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
