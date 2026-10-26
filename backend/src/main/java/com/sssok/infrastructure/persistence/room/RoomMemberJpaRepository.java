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

    List<RoomMemberJpaEntity> findByRoomIdInAndMemberIdIn(
        Collection<Long> roomIds, Collection<Long> memberIds);

    // 동시 입장에서도 하나만 통과하도록 DB에 맡긴다.
    @Modifying
    @Query(value = """
        INSERT INTO room_member (room_id, member_id, joined_at, created_at, updated_at, nickname)
        VALUES (:roomId, :memberId, :joinedAt, :joinedAt, :joinedAt,
            COALESCE(:nickname, (SELECT nickname FROM member WHERE id = :memberId)))
        ON CONFLICT (room_id, member_id) DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
        @Param("roomId") Long roomId,
        @Param("memberId") Long memberId,
        @Param("joinedAt") Instant joinedAt,
        @Param("nickname") String nickname
    );

}
