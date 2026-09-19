package com.sssok.domain.feedback;

// 의견을 남길 때 프론트가 실어 보내는 버전 태그(예: fe-v0.1.0).
//
// 같은 증상이 특정 버전에만 몰려 있으면 그 배포에서 생긴 문제라고 좁힐 수 있다.
// UserAgent 가 "어떤 기기에서"를 알려준다면 이 값은 "어느 버전에서"를 알려준다.
//
// UserAgent 와 달리 브라우저가 자동으로 붙여 주지 않아 프론트가 직접 보내야 한다.
// 그래서 빠질 여지가 더 크다 — 구버전 클라이언트, 배포 과도기, 봇 요청 모두 없이 들어온다.
// 없음을 정상으로 받아들이고 null 로 저장한다. 부가 정보 때문에 의견을 잃으면 손해가 크다.
//
// 형식은 검증하지 않고 길이만 제한한다. 클라이언트가 보내는 값이라 어차피 신뢰할 수 없고,
// 서버가 패턴을 강제하면 버전 체계가 바뀔 때마다 서버를 고쳐야 한다.
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
