package com.sssok.infrastructure.persistence.auth;

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

@Entity
@Table(name = "link_code")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LinkCodeJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "code", nullable = false, unique = true, length = 6)
    private String code;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    public LinkCodeJpaEntity(Long id, Long memberId, String code, Instant expiresAt) {
        this.id = id;
        this.memberId = memberId;
        this.code = code;
        this.expiresAt = expiresAt;
    }
}
