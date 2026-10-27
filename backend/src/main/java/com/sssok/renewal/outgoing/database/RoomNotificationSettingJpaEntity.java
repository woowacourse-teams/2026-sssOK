package com.sssok.renewal.outgoing.database;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.io.Serializable;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 저장은 (room_id, member_id) 기준 upsert 네이티브 쿼리로 한다.
@Entity
@Table(name = "room_notification_setting")
@IdClass(RoomNotificationSettingJpaEntity.Key.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomNotificationSettingJpaEntity {
    @Id
    @Column(name = "room_id")
    private Long roomId;
    @Id
    @Column(name = "member_id")
    private Long memberId;
    @Column(nullable = false)
    private boolean enabled;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public record Key(Long roomId, Long memberId) implements Serializable {
    }
}
