package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

@Entity
@Table(name = "organize_execution", indexes = @Index(name = "idx_organize_execution_status", columnList = "status"), uniqueConstraints = {
    @UniqueConstraint(name = "uk_organize_execution_execution_id", columnNames = "execution_id"),
    @UniqueConstraint(name = "uk_organize_execution_job_attempt", columnNames = {"job_id", "attempt"})
})
@Check(name = "ck_organize_execution_status", constraints = "status IN ('DISPATCH_PENDING','DISPATCHING','DISPATCH_UNKNOWN','ACCEPTED','RUNNING','COMPLETED','FAILED','CANCELED')")
@Check(name = "ck_organize_execution_counts", constraints = "attempt > 0 AND dispatch_attempts >= 0")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeExecutionJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "execution_id", nullable = false, updatable = false)
    private UUID executionId;

    @Column(name = "attempt", nullable = false, updatable = false)
    private int attempt;

    @Column(name = "status", nullable = false, length = 24)
    private String status;

    @Column(name = "dispatch_attempts", nullable = false)
    private int dispatchAttempts;

    @Column(name = "next_dispatch_at")
    private Instant nextDispatchAt;

    @Column(name = "dispatch_deadline_at", nullable = false, updatable = false)
    private Instant dispatchDeadlineAt;

    @Column(name = "lease_until")
    private Instant leaseUntil;

    @Column(name = "accepted_at")
    private Instant acceptedAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "last_event_at")
    private Instant lastEventAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    // ID 필드로 저장하며, 이 읽기 전용 매핑은 스키마 FK를 정의한다. 삭제 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_organize_execution_job"))
    private OrganizeJobJpaEntity job;

    public OrganizeExecutionJpaEntity(Long jobId, UUID executionId, int attempt, String status,
                                      Instant nextDispatchAt, Instant dispatchDeadlineAt, Instant createdAt) {
        super(createdAt);
        this.jobId = jobId;
        this.executionId = executionId;
        this.attempt = attempt;
        this.status = status;
        this.nextDispatchAt = nextDispatchAt;
        this.dispatchDeadlineAt = dispatchDeadlineAt;
    }
}
