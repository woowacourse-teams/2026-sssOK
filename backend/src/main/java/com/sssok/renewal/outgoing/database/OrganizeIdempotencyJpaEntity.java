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
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;

// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
@Table(name = "organize_idempotency", indexes = @Index(name = "idx_organize_idempotency_created", columnList = "created_at"), uniqueConstraints = @UniqueConstraint(name = "uk_organize_idempotency",
    columnNames = {"member_id", "room_id", "operation", "idem_key"}))
@Check(name = "ck_organize_idempotency_operation", constraints = "operation IN ('START','RETRY','APPLY')")
@Check(name = "ck_organize_idempotency_hash", constraints = "length(request_hash) = 64")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeIdempotencyJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "operation", nullable = false, length = 16, updatable = false)
    private String operation;

    @Column(name = "idem_key", nullable = false, length = 64, updatable = false)
    private String idemKey;

    @Column(name = "request_hash", nullable = false, length = 64, updatable = false)
    private String requestHash;

    @Column(name = "job_id", updatable = false)
    private Long jobId;

    @Column(name = "response", columnDefinition = "TEXT", updatable = false)
    private String response;

    // ID 필드로 저장하며, 이 읽기 전용 매핑은 스키마 FK를 정의한다. 삭제 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(name = "job_id", nullable = true, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_organize_idempotency_job"))
    private OrganizeJobJpaEntity job;

    public OrganizeIdempotencyJpaEntity(Long memberId, Long roomId, String operation, String idemKey,
                                        String requestHash, Long jobId, String response, Instant createdAt) {
        super(createdAt);
        this.memberId = memberId;
        this.roomId = roomId;
        this.operation = operation;
        this.idemKey = idemKey;
        this.requestHash = requestHash;
        this.jobId = jobId;
        this.response = response;
    }
}
