package com.sssok.renewal.domain.report;

import com.sssok.renewal.domain.report.exception.InvalidMediaReportStatusException;
import java.util.Arrays;

// 이름이 그대로 API 의 status 값이 되므로, 이름을 바꾸면 API 계약이 바뀐다.
public enum MediaReportStatus {
    UNREFLECTED,
    REFLECTED;

    public static MediaReportStatus from(String value) {
        return Arrays.stream(values())
            .filter(status -> status.name().equals(value))
            .findFirst()
            .orElseThrow(() -> new InvalidMediaReportStatusException(value));
    }
}
