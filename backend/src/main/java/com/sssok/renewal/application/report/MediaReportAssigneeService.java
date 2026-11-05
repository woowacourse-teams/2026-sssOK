package com.sssok.renewal.application.report;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.domain.feedback.FeedbackAssignee;
import com.sssok.renewal.application.report.exception.MediaReportNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MediaReportAssigneeService {

    private final MediaReportRepository mediaReportRepository;
    private final MediaReportQueryService mediaReportQueryService;
    private final AdminPermissionChecker permissionChecker;

    @Transactional
    public MediaReportDetail changeAssignee(Long adminId, Long reportId, String assignee) {
        permissionChecker.require(adminId, AdminPermission.MEDIA_REPORT_MANAGE);
        FeedbackAssignee newAssignee = assignee == null ? null : FeedbackAssignee.from(assignee);

        MediaReport report = mediaReportRepository.findById(reportId)
            .orElseThrow(MediaReportNotFoundException::new);
        report.changeAssignee(newAssignee);
        mediaReportRepository.updateAssignee(report);
        return mediaReportQueryService.detailOf(report);
    }
}
