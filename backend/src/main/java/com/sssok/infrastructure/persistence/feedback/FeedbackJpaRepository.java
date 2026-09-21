package com.sssok.infrastructure.persistence.feedback;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface FeedbackJpaRepository extends JpaRepository<FeedbackJpaEntity, Long> {

    Optional<FeedbackJpaEntity> findFirstByMemberIdAndCreatedAtAfterOrderByCreatedAtDesc(
        Long memberId, Instant since);

    // 방·회원을 join 하지 않는다 — purge 된 방의 의견도 그대로 나와야 한다.
    List<FeedbackJpaEntity> findAllByOrderByCreatedAtDescIdDesc(Limit limit);

    // 기준점을 (작성 시각, ID) 쌍으로 비교한다.
    @Query("""
        SELECT f FROM FeedbackJpaEntity f
        WHERE f.createdAt < :createdAt
           OR (f.createdAt = :createdAt AND f.id < :id)
        ORDER BY f.createdAt DESC, f.id DESC
        """)
    List<FeedbackJpaEntity> findAllBefore(
        @Param("createdAt") Instant createdAt, @Param("id") Long id, Limit limit);
}
