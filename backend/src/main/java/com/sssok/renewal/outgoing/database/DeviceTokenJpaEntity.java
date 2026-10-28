package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.notification.DevicePlatform;
import com.sssok.renewal.domain.notification.DeviceToken;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 저장은 token 기준 upsert 네이티브 쿼리로 하고, 이 엔티티는 조회에만 쓴다.
@Entity
@Table(name = "device_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DeviceTokenJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Column(nullable = false, length = 512)
    private String token;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private DevicePlatform platform;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public DeviceToken toDomain() {
        return DeviceToken.reconstruct(memberId, token, platform);
    }
}
