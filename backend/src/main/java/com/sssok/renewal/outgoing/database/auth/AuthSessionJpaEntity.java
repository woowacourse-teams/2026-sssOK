package com.sssok.renewal.outgoing.database.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sssok.renewal.outgoing.database.member.MemberJpaEntity;
import org.hibernate.annotations.ColumnDefault;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "auth_session", indexes = {
    @Index(name = "idx_auth_session_member", columnList = "member_id"),
    @Index(name = "idx_auth_session_expiry", columnList = "expires_at")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AuthSessionJpaEntity {
    @Id
    @Column(name = "id", nullable = false, length = 64)
    private String id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked", nullable = false)
    @ColumnDefault("false")
    private boolean revoked;

    // 쓰기는 memberId가 담당한다. 회원이 물리 삭제되면 DB가 인증 기록도 삭제한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_auth_session_member"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MemberJpaEntity member;

    public AuthSessionJpaEntity(String id, Long memberId, Instant expiresAt, boolean revoked) {
        this.id = id;
        this.memberId = memberId;
        this.expiresAt = expiresAt;
        this.revoked = revoked;
    }
}
