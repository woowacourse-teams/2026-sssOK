package com.sssok.renewal.application.report;

import com.sssok.application.admin.AdminPermissionChecker;
import com.sssok.application.media.MediaUrlResolver;
import com.sssok.application.media.MediaUrls;
import com.sssok.domain.admin.AdminPermission;
import com.sssok.renewal.application.report.exception.InvalidMediaReportCursorException;
import com.sssok.renewal.application.report.exception.InvalidMediaReportPageSizeException;
import com.sssok.renewal.application.report.exception.MediaReportNotFoundException;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.outgoing.database.MediaReportRepository;
import com.sssok.renewal.outgoing.database.ReportFileRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 사진 URL 은 단건 조회에서만 서명한다.
@Service
@RequiredArgsConstructor
public class MediaReportQueryService {

    static final int DEFAULT_PAGE_SIZE = 20;
    static final int MAX_PAGE_SIZE = 100;

    private final MediaReportRepository mediaReportRepository;
    private final ReportFileRepository reportFileRepository;
    private final MediaUrlResolver mediaUrlResolver;
    private final AdminPermissionChecker permissionChecker;

    @Transactional(readOnly = true)
    public MediaReportPage findLatest(Long adminId, Long cursor, Integer size) {
        permissionChecker.require(adminId, AdminPermission.MEDIA_REPORT_READ);

        int pageSize = pageSizeOf(size);
        List<MediaReport> found = readPage(cursor, pageSize + 1);
        boolean hasNext = found.size() > pageSize;
        return MediaReportPage.of(hasNext ? found.subList(0, pageSize) : found, hasNext);
    }

    @Transactional(readOnly = true)
    public MediaReportDetail findById(Long adminId, Long reportId) {
        permissionChecker.require(adminId, AdminPermission.MEDIA_REPORT_READ);
        MediaReport report = mediaReportRepository.findById(reportId)
            .orElseThrow(MediaReportNotFoundException::new);
        return detailOf(report);
    }

    public MediaReportDetail detailOf(MediaReport report) {
        MediaUrls urls = reportFileRepository.findById(report.getMediaId())
            .filter(file -> file.getRoomId().equals(report.getRoomId()))
            .map(mediaUrlResolver::resolve)
            .orElse(null);
        return new MediaReportDetail(report, urls, mediaReportRepository.countByMediaId(report.getMediaId()));
    }

    private List<MediaReport> readPage(Long cursor, int limit) {
        if (cursor == null) {
            return mediaReportRepository.findLatest(limit);
        }
        MediaReport cursorReport = mediaReportRepository.findById(cursor)
            .orElseThrow(InvalidMediaReportCursorException::new);
        return mediaReportRepository.findLatestBefore(cursorReport.getReportedAt(), cursorReport.getId(), limit);
    }

    private int pageSizeOf(Integer size) {
        if (size == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new InvalidMediaReportPageSizeException();
        }
        return size;
    }
}
