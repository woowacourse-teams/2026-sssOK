package com.sssok.domain.feedback;

import java.time.Instant;
import lombok.Getter;

// 사용자가 남긴 의견.
@Getter
public class Feedback {

    private final Long id;
    private final FeedbackContent content;
    private final Long roomId;
    private final String roomName;
    private final Long memberId;
    private final String nickname;
    private final UserAgent userAgent;
    private final FrontendVersion frontendVersion;
    private FeedbackStatus status;
    private final Instant createdAt;

    private Feedback(
        Long id,
        FeedbackContent content,
        Long roomId,
        String roomName,
        Long memberId,
        String nickname,
        UserAgent userAgent,
        FrontendVersion frontendVersion,
        FeedbackStatus status,
        Instant createdAt
    ) {
        this.id = id;
        this.content = content;
        this.roomId = roomId;
        this.roomName = roomName;
        this.memberId = memberId;
        this.nickname = nickname;
        this.userAgent = userAgent;
        this.frontendVersion = frontendVersion;
        this.status = status;
        this.createdAt = createdAt;
    }

    public static Feedback write(
        FeedbackContent content,
        Long roomId,
        String roomName,
        Long memberId,
        String nickname,
        UserAgent userAgent,
        FrontendVersion frontendVersion,
        Instant now
    ) {
        return new Feedback(
            null, content, roomId, roomName, memberId, nickname, userAgent, frontendVersion,
            FeedbackStatus.UNREFLECTED, now);
    }

    public static Feedback reconstruct(
        Long id,
        FeedbackContent content,
        Long roomId,
        String roomName,
        Long memberId,
        String nickname,
        UserAgent userAgent,
        FrontendVersion frontendVersion,
        FeedbackStatus status,
        Instant createdAt
    ) {
        return new Feedback(
            id, content, roomId, roomName, memberId, nickname, userAgent, frontendVersion, status, createdAt);
    }

    public void changeStatus(FeedbackStatus newStatus) {
        this.status = newStatus;
    }
}
