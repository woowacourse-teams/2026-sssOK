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
import org.hibernate.annotations.Check;

// 회원·방·사진 ID는 삭제 후에도 남기는 이력 식별자다. 외부 테이블 FK와 삭제 cascade를 두지 않는다.
@Entity
@Table(name = "organize_job_photo", uniqueConstraints =
    @UniqueConstraint(name = "uk_organize_job_photo", columnNames = {"job_id", "media_id"}))
@Check(name = "ck_organize_job_photo_source", constraints = "source IN ('PREVIEW','ORIGINAL')")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OrganizeJobPhotoJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_id", nullable = false, updatable = false)
    private Long jobId;

    @Column(name = "media_id", nullable = false, updatable = false)
    private Long mediaId;

    @Column(name = "extension", nullable = false, length = 8, updatable = false)
    private String extension;

    @Column(name = "source", nullable = false, length = 16, updatable = false)
    private String source;

    // ID 필드로 저장하며, 이 읽기 전용 매핑은 스키마 FK를 정의한다. 삭제 cascade는 사용하지 않는다.
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_id", nullable = false, insertable = false, updatable = false,
        foreignKey = @ForeignKey(name = "fk_organize_job_photo_job"))
    private OrganizeJobJpaEntity job;

    public OrganizeJobPhotoJpaEntity(Long jobId, Long mediaId, String extension, String source, Instant createdAt) {
        super(createdAt);
        this.jobId = jobId;
        this.mediaId = mediaId;
        this.extension = extension;
        this.source = source;
    }
}
