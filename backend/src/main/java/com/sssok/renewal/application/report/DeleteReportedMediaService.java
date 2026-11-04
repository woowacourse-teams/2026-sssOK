package com.sssok.renewal.application.report;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.application.media.MediaDeleter;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.renewal.application.report.exception.MediaReportNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import com.sssok.renewal.outgoing.database.ReportFileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사용자 삭제와 같은 MediaDeleter 로 지운다. 사진이 이미 없어도 실패로 보지 않고 같은 사진의 신고를
// 처리완료로 맞춘다. 같은 사진을 동시에 지워도 삭제가 한 번만 일어나도록 행을 잠그고 읽는다.
@Service
@RequiredArgsConstructor
public class DeleteReportedMediaService {

    private final MediaReportRepository mediaReportRepository;
    private final ReportFileRepository reportFileRepository;
    private final MediaDeleter mediaDeleter;
    private final MediaReportQueryService mediaReportQueryService;
    private final AdminPermissionChecker permissionChecker;

    @Transactional
    public MediaReportDetail deleteMedia(Long adminId, Long reportId) {
        permissionChecker.require(adminId, AdminPermission.MEDIA_REPORT_MANAGE);
        MediaReport report = mediaReportRepository.findById(reportId)
            .orElseThrow(MediaReportNotFoundException::new);

        reportFileRepository.findByIdForUpdate(report.getMediaId())
            .filter(file -> file.getRoomId().equals(report.getRoomId()))
            .ifPresent(file -> mediaDeleter.delete(report.getRoomId(), List.of(file)));
        mediaReportRepository.reflectAllByMediaId(report.getMediaId());

        MediaReport reflected = mediaReportRepository.findById(reportId)
            .orElseThrow(MediaReportNotFoundException::new);
        return mediaReportQueryService.detailOf(reflected);
    }
}
