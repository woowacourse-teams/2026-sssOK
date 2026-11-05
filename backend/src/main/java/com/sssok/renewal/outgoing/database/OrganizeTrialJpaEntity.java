package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.ForeignKey;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 익명 회원이 (회원, 방) 체험 1회를 쓴 기록. 행이 있으면 사용한 것이고 지우면 돌려준 것이다.
// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
@Table(name = "organize_trial", uniqueConstraints = {
    @UniqueConstraint(name = "uk_organize_trial_member_room", columnNames = {"member_id", "room_id"}),
    @UniqueConstraint(name = "uk_organize_trial_job", columnNames = "job_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeTrialJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    // ID 필드로 저장하며, 이 읽기 전용 매핑은 스키마 FK를 정의한다. 삭제 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_organize_trial_job"))
    private OrganizeJobJpaEntity job;

    public OrganizeTrialJpaEntity(Long memberId, Long roomId, Long jobId, Instant createdAt) {
        super(createdAt);
        this.memberId = memberId;
        this.roomId = roomId;
        this.jobId = jobId;
    }
}
