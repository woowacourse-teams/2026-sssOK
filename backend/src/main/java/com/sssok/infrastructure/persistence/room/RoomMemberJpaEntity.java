package com.sssok.infrastructure.persistence.room;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
    name = "room_member",
    uniqueConstraints = @UniqueConstraint(name = "uk_room_member", columnNames = {"room_id", "member_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoomMemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "joined_at", nullable = false)
    private Instant joinedAt;

    public RoomMemberJpaEntity(Long id, Long roomId, Long memberId, Instant joinedAt) {
        this.id = id;
        this.roomId = roomId;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }
}
