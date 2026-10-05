package com.sssok.infrastructure.persistence.medialike;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaLikeJpaRepository extends JpaRepository<MediaLikeJpaEntity, Long> {

    // 같은 회원이 동시에 두 번 눌러도 한 건만 남도록 DB 에 맡긴다. 상태 확인과 삽입을 한 문장에 둬서,
    // 확인한 뒤 FAILED 로 확정된 미디어에 좋아요가 붙지 않게 한다.
    // JPA 를 거치지 않아 BaseEntity 의 @PrePersist 가 돌지 않으므로 감사 컬럼을 직접 넣는다.
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

    // 미디어마다 따로 세면 목록 한 페이지에 쿼리가 N 번 나간다.
    @Query("select l.mediaId, count(l) from MediaLikeJpaEntity l where l.mediaId in :mediaIds group by l.mediaId")
    List<Object[]> countGroupByMediaIdIn(@Param("mediaIds") List<Long> mediaIds);

    @Query("select l.mediaId from MediaLikeJpaEntity l where l.memberId = :memberId and l.mediaId in :mediaIds")
    List<Long> findMediaIdsByMemberIdAndMediaIdIn(
        @Param("memberId") Long memberId,
        @Param("mediaIds") List<Long> mediaIds);
}
