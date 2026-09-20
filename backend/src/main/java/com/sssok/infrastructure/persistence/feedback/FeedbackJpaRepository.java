package com.sssok.infrastructure.persistence.feedback;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FeedbackJpaRepository extends JpaRepository<FeedbackJpaEntity, Long> {

    Optional<FeedbackJpaEntity> findFirstByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(
        Long memberId, Instant since);
}
