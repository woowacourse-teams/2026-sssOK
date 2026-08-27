package com.sssok.infrastructure.persistence.download;

import com.sssok.infrastructure.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "download_job_media")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class DownloadJobMediaJpaEntity extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "download_job_id", nullable = false)
    private Long downloadJobId;

    @Column(name = "media_id", nullable = false)
    private Long mediaId;

    public DownloadJobMediaJpaEntity(Long id, Long downloadJobId, Long mediaId) {
        this.id = id;
        this.downloadJobId = downloadJobId;
        this.mediaId = mediaId;
    }
}
