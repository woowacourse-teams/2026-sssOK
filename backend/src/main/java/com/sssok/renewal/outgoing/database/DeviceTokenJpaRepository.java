package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DeviceTokenJpaRepository extends JpaRepository<DeviceTokenJpaEntity, Long> {

    // 같은 앱이 토큰을 다시 등록하거나 다른 계정으로 바꿔 로그인해도 토큰 하나에 행 하나만 남긴다.
    @Modifying
    @Query(value = """
        INSERT INTO device_token (member_id, token, platform, created_at)
        VALUES (:memberId, :token, :platform, :now)
        ON CONFLICT (token) DO UPDATE
        SET member_id = EXCLUDED.member_id, platform = EXCLUDED.platform
        """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("token") String token,
                @Param("platform") String platform, @Param("now") Instant now);

    @Modifying
    @Query("delete from DeviceTokenJpaEntity d where d.memberId = :memberId and d.token = :token")
    void deleteByMemberIdAndToken(@Param("memberId") Long memberId, @Param("token") String token);

    @Modifying
    @Query("delete from DeviceTokenJpaEntity d where d.token = :token")
    void deleteByToken(@Param("token") String token);

    // 받는 사람 조건은 브라우저 구독(PushSubscriptionJpaRepository.findRecipients)과 같다.
    @Query(value = """
        SELECT dt.* FROM device_token dt
        JOIN room_member rm ON rm.member_id = dt.member_id
        LEFT JOIN room_notification_setting s ON s.room_id = rm.room_id AND s.member_id = rm.member_id
        WHERE rm.room_id = :roomId
          AND rm.member_id <> :actorId
          AND COALESCE(s.enabled, TRUE)
        """, nativeQuery = true)
    List<DeviceTokenJpaEntity> findRecipients(@Param("roomId") Long roomId, @Param("actorId") Long actorId);
}
