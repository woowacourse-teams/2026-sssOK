package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.report.MediaReport;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@RequiredArgsConstructor
public class MediaReportRepository {
    private final MediaReportJpaRepository jpaRepository;

    // 이미 같은 사람이 같은 사진을 신고했다면 저장하지 않고 빈 값을 돌려준다.
    @Transactional
    public Optional<MediaReport> saveIfAbsent(MediaReport report) {
        return jpaRepository.insertIfAbsent(report.getRoomId(), report.getRoomName(), report.getMediaId(),
                report.getMemberId(), report.getNickname(), report.getReason().name(), report.getReportedAt())
            .map(report::withId);
    }

    public Optional<MediaReport> findById(Long id) {
        return jpaRepository.findById(id).map(MediaReportJpaEntity::toDomain);
    }

    public List<MediaReport> findLatest(int limit) {
        return toDomains(jpaRepository.findAllByOrderByReportedAtDescIdDesc(Limit.of(limit)));
    }

    public List<MediaReport> findLatestBefore(Instant reportedAt, Long id, int limit) {
        return toDomains(jpaRepository.findAllBefore(reportedAt, id, Limit.of(limit)));
    }

    public long countByMediaId(Long mediaId) {
        return jpaRepository.countByMediaId(mediaId);
    }

    private List<MediaReport> toDomains(List<MediaReportJpaEntity> entities) {
        return entities.stream().map(MediaReportJpaEntity::toDomain).toList();
    }
}
