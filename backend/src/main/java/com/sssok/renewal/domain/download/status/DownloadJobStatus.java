package com.sssok.renewal.domain.download.status;

import com.sssok.renewal.domain.download.exception.IllegalDownloadJobStatusException;
import java.util.List;

// 썸네일 작업과 같은 상태 패턴: 허용된 전이만 각 상태가 구현한다.
public interface DownloadJobStatus {
    String name();

    default boolean isReady() {
        return false;
    }

    default DownloadJobStatus run() {
        throw invalidTransition(RunningDownloadJobStatus.INSTANCE);
    }

    default DownloadJobStatus ready() {
        throw invalidTransition(ReadyDownloadJobStatus.INSTANCE);
    }

    default DownloadJobStatus fail() {
        throw invalidTransition(FailedDownloadJobStatus.INSTANCE);
    }

    default DownloadJobStatus expire() {
        throw invalidTransition(ExpiredDownloadJobStatus.INSTANCE);
    }

    private IllegalDownloadJobStatusException invalidTransition(DownloadJobStatus next) {
        return new IllegalDownloadJobStatusException(this, next);
    }

    static DownloadJobStatus initial() {
        return QueuedDownloadJobStatus.INSTANCE;
    }

    static List<DownloadJobStatus> active() {
        return List.of(QueuedDownloadJobStatus.INSTANCE, RunningDownloadJobStatus.INSTANCE);
    }

    static DownloadJobStatus from(String name) {
        return switch (name) {
            case "QUEUED" -> QueuedDownloadJobStatus.INSTANCE;
            case "RUNNING" -> RunningDownloadJobStatus.INSTANCE;
            case "READY" -> ReadyDownloadJobStatus.INSTANCE;
            case "FAILED" -> FailedDownloadJobStatus.INSTANCE;
            case "EXPIRED" -> ExpiredDownloadJobStatus.INSTANCE;
            default -> throw new IllegalArgumentException("알 수 없는 다운로드 잡 상태입니다: " + name);
        };
    }
}
