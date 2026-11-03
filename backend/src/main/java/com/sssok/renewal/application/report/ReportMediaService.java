package com.sssok.renewal.application.report;

import com.sssok.renewal.application.report.exception.MediaAlreadyReportedException;
import com.sssok.renewal.application.report.exception.ReportMediaNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportReason;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import com.sssok.renewal.outgoing.database.ReportFileRepository;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 방 존재·만료·입장 여부는 RoomMembershipInterceptor 가 먼저 걸러준다.
@Service
@RequiredArgsConstructor
public class ReportMediaService {
    private final ReportFileRepository reportFileRepository;
    private final MediaReportRepository mediaReportRepository;

    public MediaReport report(ReportMediaCommand command) {
        MediaReportReason reason = MediaReportReason.from(command.reason());
        reportFileRepository.findById(command.mediaId())
            .filter(file -> file.getRoomId().equals(command.roomId()))
            // RESERVED·FAILED 는 갤러리에 보이지 않아 없는 미디어와 같게 본다.
            .filter(file -> file.getStatus().isVisible())
            .orElseThrow(ReportMediaNotFoundException::new);

        MediaReport report = MediaReport.create(
            command.roomId(), command.mediaId(), command.memberId(), reason, Instant.now());
        if (!mediaReportRepository.saveIfAbsent(report)) {
            throw new MediaAlreadyReportedException();
        }
        return report;
    }
}
