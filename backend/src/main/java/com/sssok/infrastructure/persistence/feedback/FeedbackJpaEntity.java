package com.sssok.infrastructure.persistence.feedback;

import com.sssok.infrastructure.persistence.BaseEntity;
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

// room_id·member_id 를 연관관계가 아닌 값으로 둔다 — 방이 purge 돼도 의견은 남아야 한다.
@Entity
@Table(name = "feedback")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FeedbackJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 1000)
    private String content;

    @Column(name = "room_id", nullable = false)
    private Long roomId;

    @Column(name = "room_name", nullable = false)
    private String roomName;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column
    private String nickname;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "app_version", length = 32)
    private String appVersion;

    public FeedbackJpaEntity(
        Long id,
        String content,
        Long roomId,
        String roomName,
        Long memberId,
        String nickname,
        String userAgent,
        String appVersion,
        Instant createdAt
    ) {
        super(createdAt);
        this.id = id;
        this.content = content;
        this.roomId = roomId;
        this.roomName = roomName;
        this.memberId = memberId;
        this.nickname = nickname;
        this.userAgent = userAgent;
        this.appVersion = appVersion;
    }
}
