package com.sssok.domain.search;

import java.util.List;

public record ImageAnalysis(String description, String features, String searchText,
                            List<Double> embedding, String embeddingModel,
                            String analysisModel, String promptVersion) {
    public ImageAnalysis {
        requireText(description);
        requireText(features);
        requireText(searchText);
        requireText(embeddingModel);
        requireText(analysisModel);
        requireText(promptVersion);
        embedding = List.copyOf(embedding);
        if (embedding.isEmpty() || embedding.stream().anyMatch(value -> !Double.isFinite(value))
            || embedding.stream().allMatch(value -> value == 0.0)) {
            throw new IllegalArgumentException("유효한 검색 벡터가 필요합니다");
        }
    }

    private static void requireText(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("분석 결과와 모델 정보가 필요합니다");
        }
    }
}
