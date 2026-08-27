package com.sssok.domain.file;

import java.util.Set;

// RESERVED 는 서명 URL 만 나가고 스토리지에는 아직 아무것도 없는 상태라, 목록에 보이면 안 된다.
public enum UploadStatus {

    RESERVED,
    PROCESSING,
    READY,
    FAILED;

    private static final Set<UploadStatus> FROM_RESERVED = Set.of(PROCESSING, FAILED);
    private static final Set<UploadStatus> FROM_PROCESSING = Set.of(READY, FAILED);
    private static final Set<UploadStatus> FROM_FAILED = Set.of(PROCESSING);

    public boolean canTransitionTo(UploadStatus next) {
        return switch (this) {
            case RESERVED -> FROM_RESERVED.contains(next);
            case PROCESSING -> FROM_PROCESSING.contains(next);
            case FAILED -> FROM_FAILED.contains(next);
            case READY -> false;
        };
    }
}
