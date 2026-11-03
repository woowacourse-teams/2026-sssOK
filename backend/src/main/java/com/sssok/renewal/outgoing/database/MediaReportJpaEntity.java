package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportReason;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 저장은 (media_id, member_id) 기준 네이티브 쿼리로 하고, 이 엔티티는 조회에만 쓴다.
@Entity
@Table(name = "media_report")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MediaReportJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "room_id", nullable = false)
    private Long roomId;
    @Column(name = "media_id", nullable = false)
    private Long mediaId;
    @Column(name = "member_id", nullable = false)
    private Long memberId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private MediaReportReason reason;
    @Column(name = "reported_at", nullable = false)
    private Instant reportedAt;

    public MediaReport toDomain() {
        return MediaReport.create(roomId, mediaId, memberId, reason, reportedAt);
    }
}
