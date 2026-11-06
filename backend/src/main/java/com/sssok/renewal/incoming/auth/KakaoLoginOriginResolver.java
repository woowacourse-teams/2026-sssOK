package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.WebOrigin;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Collections;
import java.util.List;
import org.springframework.http.HttpHeaders;

// 카카오 로그인을 시작한 웹의 출처를 Origin·Referer 헤더에서 판별한다. 콜백 선택에만 쓰며 인증을 대신하지 않는다.
final class KakaoLoginOriginResolver {

    private KakaoLoginOriginResolver() {
    }

    // Origin이 있으면 Origin만 기준으로 하고 Referer로 대체하지 않는다. Origin이 없을 때만 Referer의 출처를 쓴다.
    // 두 헤더가 모두 있으면 Referer도 검증하며 출처가 다르면 거절한다. 헤더 중복·형식 오류·누락은 모두 거절한다.
    static String resolve(HttpServletRequest request) {
        List<String> origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        List<String> referers = Collections.list(request.getHeaders(HttpHeaders.REFERER));
        if (origins.size() > 1 || referers.size() > 1) {
            throw SocialAuthException.invalid();
        }
        String refererOrigin = referers.isEmpty() ? null : originOfReferer(referers.get(0));
        if (origins.isEmpty()) {
            if (refererOrigin == null) {
                throw SocialAuthException.invalid();
            }
            return refererOrigin;
        }
        String origin = originOfOrigin(origins.get(0));
        if (refererOrigin != null && !refererOrigin.equals(origin)) {
            throw SocialAuthException.invalid();
        }
        return origin;
    }

    private static String originOfOrigin(String value) {
        try {
            return WebOrigin.parseOrigin(value);
        } catch (IllegalArgumentException e) {
            throw SocialAuthException.invalid();
        }
    }

    private static String originOfReferer(String value) {
        try {
            return WebOrigin.originOfUrl(value);
        } catch (IllegalArgumentException e) {
            throw SocialAuthException.invalid();
        }
    }
}
