package com.sssok.infrastructure.persistence.room;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberJpaRepository extends JpaRepository<RoomMemberJpaEntity, Long> {

    Optional<RoomMemberJpaEntity> findByRoomIdAndMemberId(Long roomId, Long memberId);

    // 기존 미디어 조회와 동일하게 JPA 네이티브 쿼리와 projection으로 필요한 값만 읽는다.
    // 집계를 분리해 미디어 × 참여자의 조인 곱으로 개수가 부풀지 않게 한다.
    @Query(value = """
        SELECT r.id AS "roomId", r.code AS "code", r.name AS "name",
               r.host_id = :memberId AS "isHost",
               (SELECT count(*) FROM stored_file f WHERE f.room_id = r.id
                   AND f.status IN ('PROCESSING', 'READY')) AS "mediaCount",
               (SELECT count(*) FROM room_member m WHERE m.room_id = r.id) AS "memberCount",
               (SELECT f.thumbnail_key FROM stored_file f WHERE f.room_id = r.id
                   AND f.status IN ('PROCESSING', 'READY')
                   AND f.media_type IN ('JPEG', 'PNG', 'GIF')
                   ORDER BY f.created_at DESC, f.id DESC LIMIT 1) AS "thumbnailKey"
        FROM room_member mine JOIN room r ON r.id = mine.room_id
        WHERE mine.member_id = :memberId AND r.status <> 'DELETED'
        ORDER BY mine.joined_at DESC, mine.id DESC
        """, nativeQuery = true)
    List<RoomMembershipSummaryProjection> findRoomsByMemberId(@Param("memberId") Long memberId);

    // 동시 입장에서도 하나만 통과하도록 DB에 맡긴다.
    @Modifying
    @Query(value = """
        INSERT INTO room_member (room_id, member_id, joined_at, created_at, updated_at)
        VALUES (:roomId, :memberId, :joinedAt, :joinedAt, :joinedAt)
        ON CONFLICT (room_id, member_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("roomId") Long roomId,
        @Param("memberId") Long memberId,
        @Param("joinedAt") Instant joinedAt
    );

    interface RoomMembershipSummaryProjection {
        Long getRoomId();

        String getCode();

        String getName();

        boolean getIsHost();

        long getMediaCount();

        long getMemberCount();

        String getThumbnailKey();
    }

}
