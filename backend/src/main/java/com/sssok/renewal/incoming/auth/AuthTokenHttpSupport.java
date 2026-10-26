package com.sssok.renewal.incoming.auth;

import com.sssok.infrastructure.config.CorsProperties;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import com.sssok.common.exception.ErrorCode;

@Component
@RequiredArgsConstructor
public class AuthTokenHttpSupport {
    public static final String COOKIE_NAME = "sssok_refresh";
    private final CorsProperties corsProperties;

    @Value("${auth.cookie.secure:true}")
    private boolean secure;

    @Value("${auth.cookie.same-site:Lax}")
    private String sameSite;

    // 임의 사이트의 form 요청은 이 헤더를 보낼 수 없다. 교차 출처 JS 요청은 CORS 검증도 거친다.
    public void requireCookieRequest(HttpServletRequest request) {
        String origin = request.getHeader(HttpHeaders.ORIGIN);
        if (!"1".equals(request.getHeader("X-CSRF-Protection"))
            || (origin != null && !corsProperties.allowedOrigins().contains(origin))) {
            throw new AuthTokenRequestException(ErrorCode.INVALID_COOKIE_AUTH_REQUEST);
        }
    }

    public void validateDelivery(RefreshTokenDelivery delivery, HttpServletRequest request) {

    }

    public ResponseEntity<ApiResponse<AuthResponse>> respond(AuthResult result, RefreshTokenDelivery delivery,
                                                            HttpStatus status) {
        ResponseEntity.BodyBuilder response = ResponseEntity.status(status).cacheControl(CacheControl.noStore());
        if (delivery == RefreshTokenDelivery.COOKIE && result.refreshToken() != null) {
            response.header(HttpHeaders.SET_COOKIE, cookie(result.refreshToken(),
                Duration.between(Instant.now(), result.refreshExpiresAt())).toString());
        }
        return response.body(ApiResponse.of(AuthResponse.from(result, delivery)));
    }

    public String clearCookie() {
        return cookie("", Duration.ZERO).toString();
    }

    private ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value).httpOnly(true).secure(secure)
            .sameSite(sameSite).path("/api/v1/auth").maxAge(maxAge).build();
    }
}
