package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MediaReportJpaRepository extends JpaRepository<MediaReportJpaEntity, Long> {

    // 같은 사람이 같은 사진을 동시에 두 번 신고해도 한 건만 남도록 유니크 제약으로 거른다.
    @Modifying
    @Query(value = """
        INSERT INTO media_report (room_id, media_id, member_id, reason, reported_at)
        VALUES (:roomId, :mediaId, :memberId, :reason, :reportedAt)
        ON CONFLICT (media_id, member_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(@Param("roomId") Long roomId, @Param("mediaId") Long mediaId,
                       @Param("memberId") Long memberId, @Param("reason") String reason,
                       @Param("reportedAt") Instant reportedAt);

    // 익명 계정을 로그인 계정으로 합칠 때 신고자를 옮긴다. 로그인 계정이 이미 신고한 사진은
    // 유니크 제약에 걸리므로 옮기지 않고, 기록을 지우지 않도록 통합된 익명 계정에 그대로 둔다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE media_report r SET member_id = :targetMemberId
        WHERE r.member_id = :sourceMemberId
          AND NOT EXISTS (
              SELECT 1 FROM media_report t
              WHERE t.media_id = r.media_id AND t.member_id = :targetMemberId
          )
        """, nativeQuery = true)
    int transferMember(@Param("sourceMemberId") Long sourceMemberId, @Param("targetMemberId") Long targetMemberId);
}
