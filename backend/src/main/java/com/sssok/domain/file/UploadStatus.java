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
    // 이 값을 바꾸면 V22__add_media_cursor_pagination_index.sql 의 부분 인덱스 조건도 달라져야 한다.
    // 이미 적용된 V22를 수정하지 말고, 새 Flyway 마이그레이션에서 인덱스를 재생성한다.
    private static final Set<UploadStatus> VISIBLE = Set.of(PROCESSING, READY);

    // 원본 실물이 있어 내려줘도 되는 상태. 썸네일 워커는 원본을 읽기만 하므로 PROCESSING 도 포함한다.
    // 다운로드 단건·다건·zip 이 모두 이 기준 하나를 본다. VISIBLE 과 값은 같지만 뜻이 달라 따로 둔다.
    private static final Set<UploadStatus> DOWNLOADABLE = Set.of(PROCESSING, READY);

    public static Set<UploadStatus> visibleStatuses() {
        return VISIBLE;
    }

    public boolean isVisible() {
        return VISIBLE.contains(this);
    }

    public boolean isDownloadable() {
        return DOWNLOADABLE.contains(this);
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
