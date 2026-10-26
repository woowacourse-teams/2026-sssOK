package com.sssok.renewal.outgoing.database.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshTokenJpaEntity {
    @Id
    @Column(name = "token_hash", length = 64)
    private String tokenHash;

    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    @Column(name = "used", nullable = false)
    private boolean used;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "replacement_token_hash", length = 64)
    private String replacementTokenHash;

    public RefreshTokenJpaEntity(String tokenHash, String sessionId) {
        this.tokenHash = tokenHash;
        this.sessionId = sessionId;
    }

    public void consume(Instant now, String replacementTokenHash) {
        this.used = true;
        this.usedAt = now;
        this.replacementTokenHash = replacementTokenHash;
    }

    public boolean isWithinReuseGrace(Instant now, Duration gracePeriod) {
        return used && usedAt != null && !now.isBefore(usedAt)
            && now.isBefore(usedAt.plus(gracePeriod));
    }
}
