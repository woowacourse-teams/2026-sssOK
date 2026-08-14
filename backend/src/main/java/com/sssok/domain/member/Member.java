package com.sssok.domain.member;

import java.time.Instant;
import lombok.Getter;

@Getter
public class Member {

    private final Long id;
    private final Long roomId;
    private final Nickname displayName;
    private final Instant joinedAt;

    private Member(Long id, Long roomId, Nickname displayName, Instant joinedAt) {
        this.id = id;
        this.roomId = roomId;
        this.displayName = displayName;
        this.joinedAt = joinedAt;
    }

    public static Member join(Long roomId, Nickname displayName, Instant now) {
        return new Member(null, roomId, displayName, now);
    }

    public static Member reconstruct(Long id, Long roomId, Nickname displayName, Instant joinedAt) {
        return new Member(id, roomId, displayName, joinedAt);
    }

    public boolean isSame(Long memberId) {
        return id != null && id.equals(memberId);
    }
}
