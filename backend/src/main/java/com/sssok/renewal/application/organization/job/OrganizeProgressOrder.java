package com.sssok.renewal.application.organization.job;

import com.sssok.renewal.domain.organization.OrganizeKind;
import java.util.List;

// 분석 서버가 지나가는 단계의 순서(docs/backend/ORGANIZE_PROGRESS_CONTRACT.md). 같은 회차의 지연 이벤트를 거르는 데만 쓴다.
// 목록에 없는 단계는 순서를 알 수 없으므로 거르지 않고 그대로 받는다.
final class OrganizeProgressOrder {
    private static final List<String> PERSON = List.of("waiting", "downloading_photos", "initializing",
        "loading_models", "preparing", "detecting_faces", "grouping_faces", "detecting_bodies",
        "extracting_body_features", "matching_bodies", "exporting");
    private static final List<String> SIMILAR = List.of("waiting", "downloading_photos", "initializing",
        "preparing", "loading_scene_model", "extracting_scene_features", "loading_center_model",
        "extracting_center_features", "matching_similar_photos", "grouping_similar_photos", "exporting");

    private OrganizeProgressOrder() {
    }

    // 이미 지나간 단계로 돌아가거나, 같은 단계에서 처리 수가 줄어든 이벤트는 오래된 것이다.
    static boolean isStale(OrganizeKind kind, String currentStage, Integer currentProcessed, String nextStage,
                           Integer nextProcessed) {
        int current = rank(kind, currentStage);
        int next = rank(kind, nextStage);
        if (current < 0 || next < 0) {
            return false;
        }
        if (next != current) {
            return next < current;
        }
        return currentProcessed != null && nextProcessed != null && nextProcessed < currentProcessed;
    }

    private static int rank(OrganizeKind kind, String stage) {
        if (stage == null) {
            return -1;
        }
        return (kind == OrganizeKind.SIMILAR ? SIMILAR : PERSON).indexOf(stage);
    }
}
