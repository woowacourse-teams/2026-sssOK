package com.sssok.renewal.outgoing.database;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.Check;
import org.hibernate.annotations.ColumnDefault;

// 상태 변경은 JpaRepository 의 조건부 UPDATE 로만 한다. 이 엔티티에는 setter 가 없다.
// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
// PostgreSQL 조건부 인덱스(review/result 만료)는 V50에서 관리한다. JPA @Index로 WHERE를 표현하지 않는다.
// 유니크 제약은 마이그레이션(V50)과 같게 선언한다. H2 테스트는 스키마를 엔티티에서 만들기 때문이다.
@Table(name = "organize_job", indexes = @Index(name = "idx_organize_job_requester_room", columnList = "requester_id, room_id, id DESC"), uniqueConstraints = {
    @UniqueConstraint(name = "uk_organize_job_public_id", columnNames = "public_id"),
    @UniqueConstraint(name = "uk_organize_job_active_room", columnNames = "active_room_id")
})
@Check(name = "ck_organize_job_status", constraints = "status IN ('QUEUED','RUNNING','READY','FAILED','CANCELED','APPLIED','EXPIRED')")
@Check(name = "ck_organize_job_kind", constraints = "kind IN ('PERSON','SIMILAR')")
@Check(name = "ck_organize_job_trigger", constraints = "trigger_type IN ('BUTTON','TEXT')")
@Check(name = "ck_organize_job_counts", constraints = "photo_count > 0 AND excluded_count >= 0 AND job_version > 0 AND retry_count >= 0")
@Check(name = "ck_organize_job_active_room", constraints = "(status IN ('QUEUED','RUNNING','READY') AND active_room_id IS NOT NULL AND active_room_id = room_id) OR (status IN ('FAILED','CANCELED','APPLIED','EXPIRED') AND active_room_id IS NULL)")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeJobJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false)
    private UUID publicId;

    @Column(name = "room_id", nullable = false, updatable = false)
    private Long roomId;

    @Column(name = "requester_id", nullable = false, updatable = false)
    private Long requesterId;

    @Column(name = "kind", nullable = false, length = 16, updatable = false)
    private String kind;

    @Column(name = "trigger_type", nullable = false, length = 16, updatable = false)
    private String triggerType;

    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "request_text", length = 1000, updatable = false)
    private String requestText;

    @Column(name = "server_id", nullable = false, length = 64, updatable = false)
    private String serverId;

    @Column(name = "tool_name", nullable = false, length = 128, updatable = false)
    private String toolName;

    @Column(name = "tool_description", nullable = false, updatable = false, columnDefinition = "TEXT")
    private String toolDescription;

    @Column(name = "photo_count", nullable = false, updatable = false)
    private int photoCount;

    @Column(name = "excluded_count", nullable = false, updatable = false)
    private int excludedCount;

    @Column(name = "active_room_id")
    private Long activeRoomId;

    @Column(name = "failure_code", length = 48)
    private String failureCode;

    @Column(name = "failure_detail", length = 500)
    private String failureDetail;

    @Column(name = "result_version")
    private Integer resultVersion;

    @Column(name = "result", columnDefinition = "TEXT")
    private String result;

    @Column(name = "progress_stage", length = 48)
    private String progressStage;

    @Column(name = "progress_processed")
    private Integer progressProcessed;

    @Column(name = "progress_total")
    private Integer progressTotal;

    @Column(name = "job_version", nullable = false)
    @ColumnDefault("1")
    private int jobVersion;

    @Column(name = "retry_count", nullable = false)
    @ColumnDefault("0")
    private int retryCount;

    @Column(name = "review_expires_at")
    private Instant reviewExpiresAt;

    @Column(name = "result_expires_at")
    private Instant resultExpiresAt;

    @Column(name = "finished_at")
    private Instant finishedAt;

    public OrganizeJobJpaEntity(UUID publicId, Long roomId, Long requesterId, String kind, String triggerType,
                                String status, String requestText, String serverId, String toolName,
                                String toolDescription, int photoCount, int excludedCount, Long activeRoomId, Instant createdAt) {
        super(createdAt);
        this.publicId = publicId;
        this.roomId = roomId;
        this.requesterId = requesterId;
        this.kind = kind;
        this.triggerType = triggerType;
        this.status = status;
        this.requestText = requestText;
        this.serverId = serverId;
        this.toolName = toolName;
        this.toolDescription = toolDescription;
        this.photoCount = photoCount;
        this.excludedCount = excludedCount;
        this.activeRoomId = activeRoomId;
        this.jobVersion = 1;
    }
}
