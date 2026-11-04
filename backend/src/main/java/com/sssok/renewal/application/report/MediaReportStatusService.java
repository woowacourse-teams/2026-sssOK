package com.sssok.renewal.application.report;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.renewal.application.report.exception.MediaReportNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportStatus;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaReportStatusService {

    private final MediaReportRepository mediaReportRepository;
    private final MediaReportQueryService mediaReportQueryService;
    private final AdminPermissionChecker permissionChecker;

    @Transactional
    public MediaReportDetail changeStatus(Long adminId, Long reportId, String status) {
        permissionChecker.require(adminId, AdminPermission.MEDIA_REPORT_MANAGE);
        MediaReportStatus newStatus = MediaReportStatus.from(status);

        MediaReport report = mediaReportRepository.findById(reportId)
            .orElseThrow(MediaReportNotFoundException::new);
        report.changeStatus(newStatus);
        mediaReportRepository.updateStatus(report);
        return mediaReportQueryService.detailOf(report);
    }
}
