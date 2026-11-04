package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// OAuth 회원이 마지막으로 새 작업을 접수한 시각. 회원당 한 행이다.
// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
@Table(name = "organize_start_gate")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeStartGateJpaEntity extends BaseEntity {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "last_started_at", nullable = false)
    private Instant lastStartedAt;

    public OrganizeStartGateJpaEntity(Long memberId, Instant lastStartedAt, Instant createdAt) {
        super(createdAt);
        this.memberId = memberId;
        this.lastStartedAt = lastStartedAt;
    }
}
