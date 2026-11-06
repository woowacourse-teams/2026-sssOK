package com.sssok.renewal.application.report;

import com.sssok.renewal.domain.report.MediaReport;

// 구현체는 전송 실패를 예외로 올리지 않는다. 알림이 실패해도 신고는 남아야 한다.
public interface MediaReportNotifier {

    void notifyReported(MediaReport report);
}
