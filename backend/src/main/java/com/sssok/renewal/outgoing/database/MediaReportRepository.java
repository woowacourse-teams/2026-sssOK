package com.sssok.renewal.outgoing.database;

import com.sssok.renewal.domain.report.MediaReport;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
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
}
