package com.sssok.infrastructure.persistence.medialike;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaLikeJpaRepository extends JpaRepository<MediaLikeJpaEntity, Long> {

    // JPA 를 거치지 않아 @PrePersist 가 돌지 않으므로 감사 컬럼을 직접 넣는다.
    @Modifying
    @Query(value = """
        INSERT INTO media_like (media_id, member_id, created_at, updated_at)
        SELECT sf.id, :memberId, now(), now()
        FROM stored_file sf
        WHERE sf.id = :mediaId
          AND sf.status IN (:statuses)
        ON CONFLICT (media_id, member_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsentAndStatusIn(
        @Param("mediaId") Long mediaId,
        @Param("memberId") Long memberId,
        @Param("statuses") Collection<String> statuses);

    @Modifying
    @Query("delete from MediaLikeJpaEntity l where l.mediaId = :mediaId and l.memberId = :memberId")
    int deleteIfPresent(@Param("mediaId") Long mediaId, @Param("memberId") Long memberId);

    @Modifying
    @Query("delete from MediaLikeJpaEntity l where l.mediaId in :mediaIds")
    int deleteByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    long countByMediaId(Long mediaId);

    @Query("select l.mediaId, count(l) from MediaLikeJpaEntity l where l.mediaId in :mediaIds group by l.mediaId")
    List<Object[]> countGroupByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    @Query("select l.mediaId from MediaLikeJpaEntity l where l.memberId = :memberId and l.mediaId in :mediaIds")
    List<Long> findMediaIdsByMemberIdAndMediaIdIn(
        @Param("memberId") Long memberId,
        @Param("mediaIds") List<Long> mediaIds);
}
