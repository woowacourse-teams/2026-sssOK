package com.sssok.renewal.application.organization.execute;

public record AnalysisPhoto(long mediaId, String source, String extension) {
    public AnalysisPhoto {
        if (mediaId <= 0 || isBlank(source) || isBlank(extension)) {
            throw new IllegalArgumentException("분석 사진의 ID, 주소, 확장자가 필요합니다");
        }
    }

    @Override
    public String toString() {
        return "AnalysisPhoto[mediaId=" + mediaId + ", source=REDACTED, extension=" + extension + "]";
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
