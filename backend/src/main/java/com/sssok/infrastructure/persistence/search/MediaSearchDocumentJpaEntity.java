package com.sssok.infrastructure.persistence.search;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "media_search_document")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaSearchDocumentJpaEntity {
    // 벡터 컬럼은 전용 네이티브 쿼리로만 접근한다. 엔티티 조회 시 벡터 전체를 가져오지 않는다.
    @Id
    @Column(name = "media_id")
    private Long mediaId;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "description", columnDefinition = "text")
    private String description;

    @Column(name = "features", columnDefinition = "text")
    private String features;

    @Column(name = "search_text", columnDefinition = "text")
    private String searchText;

    @Column(name = "embedding_model", length = 100)
    private String embeddingModel;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "analysis_model", length = 100)
    private String analysisModel;

    @Column(name = "prompt_version", length = 100)
    private String promptVersion;

    @Column(name = "attempts")
    private int attempts;

    @Column(name = "next_attempt_at")
    private Instant nextAttemptAt;

    @Column(name = "started_at")
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

}
