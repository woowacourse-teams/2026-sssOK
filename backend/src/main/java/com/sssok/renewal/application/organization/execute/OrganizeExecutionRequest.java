package com.sssok.renewal.application.organization.execute;

import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrganizeExecutionRequest(OrganizeTool tool, long roomId, UUID jobId, UUID executionId, int attempt,
                                       List<AnalysisPhoto> photos) {
    // 사용자에게 적용하는 한도(gallery.organization.job.max-photos)는 시작 정책이 따로 검사한다.
    // 여기는 설정 실수가 그대로 호출되지 않게 하는 안전 상한이다.
    public static final int ABSOLUTE_MAX_PHOTOS = 1000;

    public OrganizeExecutionRequest {
        Objects.requireNonNull(tool);
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(executionId);
        photos = List.copyOf(photos);
        if (roomId <= 0 || attempt < 1) {
            throw new IllegalArgumentException("방 ID와 실행 회차는 양수여야 합니다");
        }
        if (!hasValidPhotoCount(photos) || !hasDistinctMediaIds(photos)) {
            throw new IllegalArgumentException("분석 사진은 서로 다른 1~" + ABSOLUTE_MAX_PHOTOS + "장이어야 합니다");
        }
    }

    private static boolean hasValidPhotoCount(List<AnalysisPhoto> photos) {
        return !photos.isEmpty() && photos.size() <= ABSOLUTE_MAX_PHOTOS;
    }

    private static boolean hasDistinctMediaIds(List<AnalysisPhoto> photos) {
        return photos.stream().map(AnalysisPhoto::mediaId).distinct().count() == photos.size();
    }
}
