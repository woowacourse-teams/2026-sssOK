package com.sssok.domain.feedback;

// 의견을 남긴 기기·브라우저를 알려주는 User-Agent 헤더 값.
//
// 파싱하지 않고 원본 문자열을 그대로 보관한다.
public record UserAgent(String value) {

    public static final int MAX_LENGTH = 512;

    public static UserAgent from(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return new UserAgent(null);
        }
        String stripped = rawHeader.strip();
        if (stripped.length() > MAX_LENGTH) {
            return new UserAgent(stripped.substring(0, MAX_LENGTH));
        }
        return new UserAgent(stripped);
    }

    public boolean isUnknown() {
        return value == null;
    }
}
