package com.sssok.renewal.domain.member;

import java.time.Instant;
import java.util.Objects;
import lombok.Getter;

// 익명 사용자와 소셜 회원이 공유하는 식별자. 방장·업로더 권한의 기준이 된다.
@Getter
public class Member {

    private final Long id;
    private Nickname displayName;
    private final Instant createdAt;
    private MemberStatus status;

    private Member(Long id, Nickname displayName, Instant createdAt, MemberStatus status) {
        this.id = id;
        this.displayName = displayName;
        this.createdAt = createdAt;
        this.status = Objects.requireNonNull(status);
    }

    public static Member register(Nickname displayName, Instant now) {
        return new Member(null, displayName, now, MemberStatus.ANONYMOUS);
    }

    public static Member reconstruct(Long id, Nickname displayName, Instant createdAt) {
        return reconstruct(id, displayName, createdAt, MemberStatus.ANONYMOUS);
    }

    public static Member reconstruct(Long id, Nickname displayName, Instant createdAt, MemberStatus status) {
        return new Member(id, displayName, createdAt, status);
    }

    public void completeRegistration() {
        requireStatus(MemberStatus.ANONYMOUS);
        status = MemberStatus.REGISTERED;
    }

    // 활동 이전이 성공한 트랜잭션 안에서 호출한다.
    public void markMerged() {
        requireStatus(MemberStatus.ANONYMOUS);
        status = MemberStatus.MERGED;
    }

    public void withdraw() {
        requireStatus(MemberStatus.REGISTERED);
        status = MemberStatus.WITHDRAWN;
        displayName = new Nickname("탈퇴한 사용자");
    }

    public boolean isActive() {
        return status == MemberStatus.ANONYMOUS || status == MemberStatus.REGISTERED;
    }

    private void requireStatus(MemberStatus expected) {
        if (status != expected) {
            throw new IllegalStateException("회원 상태가 일치하지 않습니다: expected=%s, actual=%s"
                .formatted(expected, status));
        }
    }

    public boolean isSame(Long memberId) {
        return id != null && id.equals(memberId);
    }
}
