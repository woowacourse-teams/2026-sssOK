package com.sssok.infrastructure.realtime;

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

// 방 이벤트 기록. 실시간 전달(SseEmitter)과는 별개로, 재연결 시 Last-Event-ID로 놓친 이벤트를
// 따라잡기 위한 로그라 도메인 규칙이 없다 — 그래서 별도 도메인 계층 없이 바로 영속화 계층에 둔다.
@Entity
@Table(name = "room_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomEventJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "event_type", nullable = false, length = 50)
    private String eventType;

    @Column(name = "payload", nullable = false)
    private String payload;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public RoomEventJpaEntity(Long roomId, String eventType, String payload, Instant createdAt) {
        this.roomId = roomId;
        this.eventType = eventType;
        this.payload = payload;
        this.createdAt = createdAt;
    }
}
