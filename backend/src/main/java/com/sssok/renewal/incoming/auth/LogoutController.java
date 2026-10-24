package com.sssok.renewal.incoming.auth;

import com.sssok.renewal.application.auth.SessionTokenProvider;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class LogoutController {
    private final SessionTokenProvider sessions;

    @Operation(summary = "현재 세션 로그아웃", description = "Authorization 헤더의 서비스 토큰을 무효화한다. "
        + "다른 로그인 세션은 유지된다. 성공 시 204, 없거나 만료·무효화된 인증이면 401을 반환한다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
        sessions.logout(header.substring("Bearer ".length()));
        return ResponseEntity.noContent().build();
    }
}
