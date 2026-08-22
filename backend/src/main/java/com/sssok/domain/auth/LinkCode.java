package com.sssok.domain.auth;

import java.time.Duration;
import java.time.Instant;
import lombok.Getter;

// 다른 기기로 이어받기 위한 1회용 코드. memberId(원래 기기의 계정)를 새 기기가 이어받을 수 있게 한다.
@Getter
public class LinkCode {

    private static final Duration TTL = Duration.ofMinutes(5);

    private final Long id;
    private final Long memberId;
    private final LinkCodeValue code;
    private final Instant expiresAt;

    private LinkCode(Long id, Long memberId, LinkCodeValue code, Instant expiresAt) {
        this.id = id;
        this.memberId = memberId;
        this.code = code;
        this.expiresAt = expiresAt;
    }

    public static LinkCode issue(Long memberId, LinkCodeValue code, Instant now) {
        return new LinkCode(null, memberId, code, now.plus(TTL));
    }

    public static LinkCode reconstruct(Long id, Long memberId, LinkCodeValue code, Instant expiresAt) {
        return new LinkCode(id, memberId, code, expiresAt);
    }

    public boolean isExpired(Instant now) {
        return now.isAfter(expiresAt);
    }
}
