package com.sssok.domain.feedback;

// 의견을 남길 때 프론트가 실어 보내는 버전 태그
public record AppVersion(String value) {

    public static final int MAX_LENGTH = 32;

    public static AppVersion from(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return new AppVersion(null);
        }
        String stripped = rawHeader.strip();
        if (stripped.length() > MAX_LENGTH) {
            return new AppVersion(stripped.substring(0, MAX_LENGTH));
        }
        return new AppVersion(stripped);
    }

    public boolean isUnknown() {
        return value == null;
    }
}
