package com.sssok.renewal.outgoing.database.member;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import com.sssok.renewal.domain.member.MemberStatus;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import com.sssok.infrastructure.persistence.BaseEntity;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "member")
@Check(name = "ck_member_status", constraints = "status IN ('ANONYMOUS', 'REGISTERED', 'MERGED', 'WITHDRAWN')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "nickname", nullable = false, length = 12)
    private String nickname;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    @ColumnDefault("'ANONYMOUS'")
    private MemberStatus status;

    public MemberJpaEntity(Long id, String nickname, Instant createdAt, MemberStatus status) {
        super(createdAt);
        this.id = id;
        this.nickname = nickname;
        this.status = status;
    }
}
