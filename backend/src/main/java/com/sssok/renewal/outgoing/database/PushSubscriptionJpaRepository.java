package com.sssok.renewal.outgoing.database;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PushSubscriptionJpaRepository extends JpaRepository<PushSubscriptionJpaEntity, Long> {

    // 같은 브라우저가 다시 구독하거나 다른 계정으로 바꿔 로그인해도 endpoint 하나에 행 하나만 남긴다.
    @Modifying
    @Query(value = """
        INSERT INTO push_subscription (member_id, endpoint, p256dh, auth, created_at)
        VALUES (:memberId, :endpoint, :p256dh, :auth, :now)
        ON CONFLICT (endpoint) DO UPDATE
        SET member_id = EXCLUDED.member_id, p256dh = EXCLUDED.p256dh, auth = EXCLUDED.auth
        """, nativeQuery = true)
    void upsert(@Param("memberId") Long memberId, @Param("endpoint") String endpoint,
                @Param("p256dh") String p256dh, @Param("auth") String auth, @Param("now") Instant now);

    @Modifying
    @Query("delete from PushSubscriptionJpaEntity s where s.memberId = :memberId and s.endpoint = :endpoint")
    void deleteByMemberIdAndEndpoint(@Param("memberId") Long memberId, @Param("endpoint") String endpoint);

    @Modifying
    @Query("delete from PushSubscriptionJpaEntity s where s.endpoint = :endpoint")
    void deleteByEndpoint(@Param("endpoint") String endpoint);

    // 같은 방 참여자 중 행위자를 빼고, 알림을 끄지 않은(설정 행이 없으면 켜짐) 사람의 구독만 고른다.
    @Query(value = """
        SELECT ps.* FROM push_subscription ps
        JOIN room_member rm ON rm.member_id = ps.member_id
        LEFT JOIN room_notification_setting s ON s.room_id = rm.room_id AND s.member_id = rm.member_id
        WHERE rm.room_id = :roomId
          AND rm.member_id <> :actorId
          AND COALESCE(s.enabled, TRUE)
        """, nativeQuery = true)
    List<PushSubscriptionJpaEntity> findRecipients(@Param("roomId") Long roomId, @Param("actorId") Long actorId);

    // 익명 계정을 로그인 계정으로 합칠 때 그 브라우저로 계속 알림이 가도록 구독을 옮긴다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE push_subscription SET member_id = :targetMemberId WHERE member_id = :sourceMemberId
        """, nativeQuery = true)
    int transferMember(@Param("sourceMemberId") Long sourceMemberId, @Param("targetMemberId") Long targetMemberId);
}
