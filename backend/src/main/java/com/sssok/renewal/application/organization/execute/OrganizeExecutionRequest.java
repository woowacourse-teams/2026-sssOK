package com.sssok.renewal.application.organization.execute;

import com.sssok.renewal.application.organization.interpret.OrganizeTool;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record OrganizeExecutionRequest(OrganizeTool tool, long roomId, UUID jobId, UUID executionId, int attempt,
                                       List<AnalysisPhoto> photos) {
    public static final int MAX_PHOTOS = 100;

    public OrganizeExecutionRequest {
        Objects.requireNonNull(tool);
        Objects.requireNonNull(jobId);
        Objects.requireNonNull(executionId);
        photos = List.copyOf(photos);
        if (roomId <= 0 || attempt < 1) {
            throw new IllegalArgumentException("방 ID와 실행 회차는 양수여야 합니다");
        }
        if (!hasValidPhotoCount(photos) || !hasDistinctMediaIds(photos)) {
            throw new IllegalArgumentException("분석 사진은 서로 다른 1~" + MAX_PHOTOS + "장이어야 합니다");
        }
    }

    private static boolean hasValidPhotoCount(List<AnalysisPhoto> photos) {
        return !photos.isEmpty() && photos.size() <= MAX_PHOTOS;
    }

    private static boolean hasDistinctMediaIds(List<AnalysisPhoto> photos) {
        return photos.stream().map(AnalysisPhoto::mediaId).distinct().count() == photos.size();
    }
}
