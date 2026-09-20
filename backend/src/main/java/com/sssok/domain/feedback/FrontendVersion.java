package com.sssok.domain.feedback;

// 의견을 남길 때 프론트가 실어 보내는 버전 태그(예: fe-v0.1.0).
//
// "앱 버전"이 아니라 "프론트 버전"으로 이름을 못박는다. 나중에 백엔드 버전이나 둘을 묶은
// 릴리즈 버전을 함께 기록하게 되면, 이 값이 무엇을 가리키는지 애매해지기 때문이다.
public record FrontendVersion(String value) {

    public static final int MAX_LENGTH = 32;

    public static FrontendVersion from(String rawHeader) {
        if (rawHeader == null || rawHeader.isBlank()) {
            return new FrontendVersion(null);
        }
        String stripped = rawHeader.strip();
        if (stripped.length() > MAX_LENGTH) {
            return new FrontendVersion(stripped.substring(0, MAX_LENGTH));
        }
        return new FrontendVersion(stripped);
    }

    public boolean isUnknown() {
        return value == null;
    }
}
