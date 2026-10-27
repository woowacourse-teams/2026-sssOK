package com.sssok.renewal.domain.media.thumbnail.status;

import java.util.List;

// 방 상태와 같은 상태 패턴: 허용된 전이만 각 상태가 구현한다.
public interface ThumbnailJobStatus {
    String name();

    default boolean isRunning() {
        return false;
    }

    default boolean isFailed() {
        return false;
    }

    default ThumbnailJobStatus claim() {
        throw invalidTransition("RUNNING");
    }

    default ThumbnailJobStatus retry() {
        throw invalidTransition("RETRY_WAIT");
    }

    default ThumbnailJobStatus rejected(int remainingAttempts) {
        throw invalidTransition("PENDING/RETRY_WAIT");
    }

    default ThumbnailJobStatus complete(ThumbnailJobStatus terminal) {
        throw invalidTransition(terminal.name());
    }

    private IllegalStateException invalidTransition(String target) {
        return new IllegalStateException("썸네일 작업 상태를 " + name() + "에서 " + target + "로 변경할 수 없습니다");
    }

    static ThumbnailJobStatus initial() {
        return PendingThumbnailJobStatus.INSTANCE;
    }

    static List<ThumbnailJobStatus> all() {
        return List.of(PendingThumbnailJobStatus.INSTANCE, RunningThumbnailJobStatus.INSTANCE,
            RetryWaitThumbnailJobStatus.INSTANCE, SucceededThumbnailJobStatus.INSTANCE,
            FailedThumbnailJobStatus.INSTANCE, CancelledThumbnailJobStatus.INSTANCE);
    }

    static ThumbnailJobStatus from(String name) {
        return switch (name) {
            case "PENDING" -> PendingThumbnailJobStatus.INSTANCE;
            case "RUNNING" -> RunningThumbnailJobStatus.INSTANCE;
            case "RETRY_WAIT" -> RetryWaitThumbnailJobStatus.INSTANCE;
            case "SUCCEEDED" -> SucceededThumbnailJobStatus.INSTANCE;
            case "FAILED" -> FailedThumbnailJobStatus.INSTANCE;
            case "CANCELLED" -> CancelledThumbnailJobStatus.INSTANCE;
            default -> throw new IllegalArgumentException("알 수 없는 썸네일 작업 상태입니다: " + name);
        };
    }
}
