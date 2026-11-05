package com.sssok.renewal.application.report;

import com.sssok.application.media.MediaUrls;
import com.sssok.renewal.domain.report.MediaReport;

// 사진이 이미 지워졌으면 mediaUrls 는 null 이다.
public record MediaReportDetail(MediaReport report, MediaUrls mediaUrls, long reportCountOfMedia) {

    public boolean mediaDeleted() {
        return mediaUrls == null;
    }
}
