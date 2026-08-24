package com.sssok.domain.member;

import java.time.Instant;
import lombok.Getter;

// 전역 회원(계정) 애그리거트. 로그인이 없는 서비스라 닉네임만으로 생성되며,
// 특정 방에 속하지 않는다 — 이 id(userId)가 방장·업로더 판정의 유일한 기준이 된다.
@Getter
public class Member {

    private final Long id;
    private final Nickname displayName;
    private final Instant createdAt;

    private Member(Long id, Nickname displayName, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.createdAt = createdAt;
    }

    public static Member register(Nickname displayName, Instant now) {
        return new Member(null, displayName, now);
    }

    public static Member reconstruct(Long id, Nickname displayName, Instant createdAt) {
        return new Member(id, displayName, createdAt);
    }

    public boolean isSame(Long memberId) {
        return id != null && id.equals(memberId);
    }
}
