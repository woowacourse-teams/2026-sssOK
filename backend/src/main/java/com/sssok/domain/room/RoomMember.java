package com.sssok.domain.room;

import com.sssok.renewal.domain.member.Nickname;
import java.time.Instant;
import lombok.Getter;

// 어떤 회원이 어떤 방에 참여했다는 사실만 남기는 기록.
@Getter
public class RoomMember {

    private final Long id;
    private final Long roomId;
    private final Long memberId;
    private final Instant joinedAt;
    private final String nickname;

    private RoomMember(Long id, Long roomId, Long memberId, Instant joinedAt) {
        this(id, roomId, memberId, joinedAt, null);
    }

    private RoomMember(Long id, Long roomId, Long memberId, Instant joinedAt, String nickname) {
        this.nickname = nickname;
        this.id = id;
        this.roomId = roomId;
        this.memberId = memberId;
        this.joinedAt = joinedAt;
    }

    public static RoomMember reconstruct(Long id, Long roomId, Long memberId, Instant joinedAt) {
        return new RoomMember(id, roomId, memberId, joinedAt);
    }

    public static RoomMember join(Long roomId, Long memberId, Instant now, Nickname nickname) {
        return new RoomMember(null, roomId, memberId, now,
            nickname.value());
    }

    public static RoomMember reconstruct(Long id, Long roomId, Long memberId, Instant joinedAt, String nickname) {
        return new RoomMember(id, roomId, memberId, joinedAt, nickname);
    }

    public boolean belongsTo(Long roomId) {
        return this.roomId.equals(roomId);
    }
}
