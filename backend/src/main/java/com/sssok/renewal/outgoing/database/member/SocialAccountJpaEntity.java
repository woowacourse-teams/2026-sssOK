package com.sssok.renewal.outgoing.database.member;

import com.sssok.infrastructure.persistence.BaseEntity;
import com.sssok.renewal.domain.member.SocialAccount;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "social_account",
    uniqueConstraints = @UniqueConstraint(name = "uk_social_account_identity", columnNames = {"provider", "provider_user_id"}),
    indexes = @Index(name = "idx_social_account_member", columnList = "member_id"))
@Check(name = "ck_social_account_provider", constraints = "provider IN ('KAKAO', 'APPLE')")
@Check(name = "ck_social_account_subject", constraints = "length(trim(provider_user_id)) > 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SocialAccountJpaEntity extends BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Enumerated(EnumType.STRING)
    @Column(name = "provider", nullable = false, length = 10)
    private SocialProvider provider;

    @Column(name = "provider_user_id", nullable = false, length = 255)
    private String providerUserId;

    // 탈퇴 회원도 연결 해제 확인 전까지 계정과 암호문을 유지한다. 회원은 물리 삭제하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_social_account_member"))
    private MemberJpaEntity member;

    public SocialAccountJpaEntity(SocialAccount account) {
        super(account.createdAt());
        id = account.id();
        memberId = account.memberId();
        provider = account.identity().provider();
        providerUserId = account.identity().providerUserId();
    }

    public SocialAccount toDomain() {
        return new SocialAccount(id, memberId, new SocialIdentity(provider, providerUserId), getCreatedAt());
    }
}
