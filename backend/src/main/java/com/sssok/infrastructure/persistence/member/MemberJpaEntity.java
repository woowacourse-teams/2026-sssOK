package com.sssok.infrastructure.persistence.member;

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
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 12)
    private String nickname;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public MemberJpaEntity(Long id, String nickname, Instant createdAt) {
        this.id = id;
        this.nickname = nickname;
        this.createdAt = createdAt;
    }
}
