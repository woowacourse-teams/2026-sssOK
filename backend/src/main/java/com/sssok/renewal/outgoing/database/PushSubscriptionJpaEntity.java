package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.notification.PushSubscription;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 저장은 endpoint 기준 upsert 네이티브 쿼리로 하고, 이 엔티티는 조회에만 쓴다.
@Entity
@Table(name = "push_subscription")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushSubscriptionJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(nullable = false, length = 1024)
    private String endpoint;
    @Column(nullable = false, length = 128)
    private String p256dh;
    @Column(nullable = false, length = 64)
    private String auth;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public PushSubscription toDomain() {
        return PushSubscription.reconstruct(memberId, endpoint, p256dh, auth);
    }
}
