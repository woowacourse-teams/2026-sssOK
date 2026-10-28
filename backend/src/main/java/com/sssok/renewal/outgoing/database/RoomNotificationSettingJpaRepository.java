package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RoomNotificationSettingJpaRepository
    extends JpaRepository<RoomNotificationSettingJpaEntity, RoomNotificationSettingJpaEntity.Key> {

    @Query("select s.enabled from RoomNotificationSettingJpaEntity s "
        + "where s.roomId = :roomId and s.memberId = :memberId")
    Optional<Boolean> findEnabled(@Param("roomId") Long roomId, @Param("memberId") Long memberId);

    @Modifying
    @Query(value = """
        INSERT INTO room_notification_setting (room_id, member_id, enabled, updated_at)
        VALUES (:roomId, :memberId, :enabled, :now)
        ON CONFLICT (room_id, member_id) DO UPDATE
        SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
        """, nativeQuery = true)
    void upsert(@Param("roomId") Long roomId, @Param("memberId") Long memberId,
                @Param("enabled") boolean enabled, @Param("now") Instant now);

    // 익명 계정의 방별 설정을 로그인 계정으로 옮긴다. 같은 방에 둘 다 설정이 있으면 나중에 바꾼 쪽을 남긴다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
        INSERT INTO room_notification_setting (room_id, member_id, enabled, updated_at)
        SELECT room_id, :targetMemberId, enabled, updated_at
        FROM room_notification_setting
        WHERE member_id = :sourceMemberId
        ON CONFLICT (room_id, member_id) DO UPDATE
        SET enabled = EXCLUDED.enabled, updated_at = EXCLUDED.updated_at
        WHERE room_notification_setting.updated_at < EXCLUDED.updated_at
        """, nativeQuery = true)
    int mergeSettings(@Param("sourceMemberId") Long sourceMemberId, @Param("targetMemberId") Long targetMemberId);

    @Modifying(flushAutomatically = true)
    @Query(value = """
        DELETE FROM room_notification_setting WHERE member_id = :sourceMemberId
        """, nativeQuery = true)
    int deleteByMemberId(@Param("sourceMemberId") Long sourceMemberId);
}
