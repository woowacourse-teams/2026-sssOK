package com.sssok.renewal.incoming.auth;

import jakarta.servlet.http.HttpServletRequest;

// 한 요청 안에서 같은 토큰을 두 번 검증하지 않도록 먼저 검증한 쪽이 결과를 요청에 남긴다.
// 토큰 문자열까지 함께 비교하므로 다른 토큰이 들어오면 재사용하지 않고 다시 검증한다.
public record VerifiedAuthentication(String token, Long memberId) {

    private static final String ATTRIBUTE = VerifiedAuthentication.class.getName();

    public static void remember(HttpServletRequest request, String token, Long memberId) {
        request.setAttribute(ATTRIBUTE, new VerifiedAuthentication(token, memberId));
    }

    public static Long find(HttpServletRequest request, String token) {
        return request.getAttribute(ATTRIBUTE) instanceof VerifiedAuthentication verified
            && verified.token().equals(token) ? verified.memberId() : null;
    }
}
