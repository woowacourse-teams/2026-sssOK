package com.sssok.renewal.domain.report;

import com.sssok.renewal.domain.report.exception.InvalidMediaReportReasonException;
import java.util.Arrays;

// 이름이 그대로 API 의 reason 값이 되므로, 이름을 바꾸면 API 계약이 바뀐다.
public enum MediaReportReason {
    INAPPROPRIATE_CONTENT,
    PRIVACY_VIOLATION,
    COPYRIGHT_VIOLATION,
    HARASSMENT;

    public static MediaReportReason from(String value) {
        return Arrays.stream(values())
            .filter(reason -> reason.name().equals(value))
            .findFirst()
            .orElseThrow(() -> new InvalidMediaReportReasonException(
                "reason 은 INAPPROPRIATE_CONTENT, PRIVACY_VIOLATION, COPYRIGHT_VIOLATION, HARASSMENT 중 하나여야 합니다"));
    }
}
