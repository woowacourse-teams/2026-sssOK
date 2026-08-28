package com.sssok.domain.file;

import java.util.Set;

// RESERVED 는 서명 URL 만 나가고 스토리지에는 아직 아무것도 없는 상태다.
public enum UploadStatus {

    RESERVED,
    PROCESSING,
    READY,
    FAILED;

    private static final Set<UploadStatus> FROM_RESERVED = Set.of(PROCESSING, FAILED);
    private static final Set<UploadStatus> FROM_PROCESSING = Set.of(READY, FAILED);
    private static final Set<UploadStatus> FROM_FAILED = Set.of(PROCESSING);

    // 조회에 노출되는 상태. RESERVED·FAILED 는 스토리지에 실물이 없어, 내려보내면 클라이언트가
    // 열 수 없는 빈 항목을 그리게 된다. 다운로드 API 도 이 둘을 없는 것과 동일하게 취급한다.
    private static final Set<UploadStatus> VISIBLE = Set.of(PROCESSING, READY);

    public static Set<UploadStatus> visibleStatuses() {
        return VISIBLE;
    }

    public boolean isVisible() {
        return VISIBLE.contains(this);
    }

    // 서명 URL 을 다시 발급해도 되는 상태. 이미 올라간 파일을 덮어쓰지 못하게 막는 기준이다.
    public boolean canReissueUploadUrl() {
        return this == RESERVED || this == FAILED;
    }

    public boolean canTransitionTo(UploadStatus next) {
        return switch (this) {
            case RESERVED -> FROM_RESERVED.contains(next);
            case PROCESSING -> FROM_PROCESSING.contains(next);
            case FAILED -> FROM_FAILED.contains(next);
            case READY -> false;
        };
    }
}
