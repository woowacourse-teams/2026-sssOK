package com.sssok.renewal.outgoing.database.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import com.sssok.renewal.outgoing.database.member.MemberJpaEntity;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

// 전체 인증 해제 후 아직 DB에 등록되지 않은 구형 토큰도 되살아나지 않게 한다.
@Entity
@Table(name = "legacy_auth_revocation")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LegacyAuthRevocationJpaEntity {
    @Id
    @Column(name = "member_id", nullable = false)
    private Long memberId;

    // 회원별 최대 한 건이며, 회원 물리 삭제 시 함께 정리한다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "member_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_legacy_auth_revocation_member"))
    @OnDelete(action = OnDeleteAction.CASCADE)
    private MemberJpaEntity member;

    public LegacyAuthRevocationJpaEntity(Long memberId) {
        this.memberId = memberId;
    }
}
