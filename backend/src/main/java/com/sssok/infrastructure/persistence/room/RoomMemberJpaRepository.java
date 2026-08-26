package com.sssok.infrastructure.persistence.room;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomMemberJpaRepository extends JpaRepository<RoomMemberJpaEntity, Long> {

    Optional<RoomMemberJpaEntity> findByRoomIdAndMemberId(Long roomId, Long memberId);

    void deleteAllByRoomId(Long roomId);

    @Query("SELECT rm.memberId FROM RoomMemberJpaEntity rm WHERE rm.roomId = :roomId")
    List<Long> findMemberIdsByRoomId(@Param("roomId") Long roomId);

    @Query("SELECT DISTINCT rm.memberId FROM RoomMemberJpaEntity rm WHERE rm.memberId IN :memberIds")
    List<Long> findMemberIdsStillInAnyRoom(@Param("memberIds") Collection<Long> memberIds);

    // 동시 입장에서도 하나만 통과하도록 DB에 맡긴다.
    @Modifying
    @Query(value = """
        INSERT INTO room_member (room_id, member_id, joined_at)
        VALUES (:roomId, :memberId, :joinedAt)
        ON CONFLICT (room_id, member_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("roomId") Long roomId,
        @Param("memberId") Long memberId,
        @Param("joinedAt") Instant joinedAt
    );
}
