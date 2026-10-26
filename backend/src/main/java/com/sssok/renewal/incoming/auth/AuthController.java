package com.sssok.renewal.incoming.auth;

import com.sssok.common.exception.ErrorCode;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.renewal.application.auth.AnonymousAuthService;
import com.sssok.renewal.application.auth.AuthResult;
import com.sssok.renewal.application.auth.IssueLinkCodeService;
import com.sssok.renewal.application.auth.LinkCodeResult;
import com.sssok.renewal.application.auth.LinkLoginService;
import com.sssok.renewal.application.auth.SessionTokenProvider;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "익명 인증, 연결 코드 로그인, 인증 갱신 및 로그아웃")
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthTokenHttpSupport tokenHttpSupport;
    private final AnonymousAuthService anonymousAuthService;
    private final IssueLinkCodeService issueLinkCodeService;
    private final LinkLoginService linkLoginService;
    private final SessionTokenProvider sessionTokenProvider;

    @Operation(summary = "현재 세션 로그아웃", description = "Authorization 헤더의 서비스 토큰을 무효화한다. "
        + "다른 로그인 세션은 유지된다. 성공 시 204, 없거나 만료·무효화된 인증이면 401을 반환한다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestHeader(value = "Authorization", required = false) String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new UnauthorizedException("다시 접속해주세요");
        }
        sessionTokenProvider.logout(header.substring("Bearer ".length()));
        return ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, tokenHttpSupport.clearCookie()).build();
    }

    @Operation(
        summary = "닉네임으로 익명 인증",
        description = "닉네임만 입력하면 별도 회원가입 절차 없이 즉시 계정을 만들고 accessToken을 발급한다. "
            + "사용법: 서비스 최초 진입 시 이 API를 호출해서 받은 accessToken을, 이후 모든 요청의 "
            + "Authorization: Bearer {accessToken} 헤더에 실어 보내면 된다."
    )
    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AuthResponse>> createAnonymous(@RequestBody AnonymousAuthRequest request, HttpServletRequest httpRequest) {
        tokenHttpSupport.validateDelivery(request.refreshTokenDelivery(), httpRequest);
        AuthResult result = anonymousAuthService.authenticate(request.nickname(), request.refreshTokenDelivery());
        return tokenHttpSupport.respond(result, request.refreshTokenDelivery(), HttpStatus.CREATED);
    }

    @Operation(
        summary = "다른 기기에서 로그인할 연결 코드 발급",
        description = "로그인된 상태에서, 다른 기기(예: PC ↔ 모바일)로 같은 계정을 이어가고 싶을 때 쓰는 "
            + "일회성 숫자 코드를 발급한다. 코드는 일정 시간 뒤 만료되고, 로그인에 한 번 성공하면 즉시 폐기된다. "
            + "사용법: 이 API로 받은 linkCode를 다른 기기 화면에 보여주고, 그 기기에서 "
            + "POST /auth/link 로 같은 코드를 보내면 로그인된다."
    )
    @PostMapping("/link-code")
    public ResponseEntity<ApiResponse<LinkCodeResponse>> createLinkCode(
        @Parameter(hidden = true) @AuthMember Long memberId
    ) {
        LinkCodeResult result = issueLinkCodeService.issue(memberId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(LinkCodeResponse.from(result)));
    }

    @Operation(
        summary = "연결 코드로 로그인",
        description = "다른 기기에서 발급받은 연결 코드로 그 계정에 로그인한다. 코드는 한 번만 쓸 수 있고, "
            + "이미 사용됐거나 만료된 코드로 시도하면 각각 404 / 410으로 응답한다. "
            + "사용법: 사용자가 다른 기기 화면에서 본 6자리 코드를 그대로 linkCode에 담아 보내면 된다."
    )
    @PostMapping("/link")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@RequestBody LinkLoginRequest request,
        HttpServletRequest httpRequest) {
        tokenHttpSupport.validateDelivery(request.refreshTokenDelivery(), httpRequest);
        AuthResult result = linkLoginService.login(request.linkCode(), request.refreshTokenDelivery());
        return tokenHttpSupport.respond(result, request.refreshTokenDelivery(), HttpStatus.OK);
    }

    @Operation(summary = "인증 토큰 갱신", description = "쿠키 또는 본문의 refresh token을 한 번 사용해 두 토큰을 교체한다.")
    @PostMapping("/refresh")
    public ResponseEntity<ApiResponse<AuthResponse>> refresh(
        @CookieValue(name = AuthTokenHttpSupport.COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) RefreshRequest request, HttpServletRequest httpRequest
    ) {
        String bodyToken = request == null ? null : request.refreshToken();
        if (cookieToken != null && bodyToken != null) {
            throw new AuthTokenRequestException(ErrorCode.INVALID_REQUEST_BODY);
        }
        RefreshTokenDelivery delivery = cookieToken == null ? RefreshTokenDelivery.BODY : RefreshTokenDelivery.COOKIE;
        tokenHttpSupport.validateDelivery(delivery, httpRequest);
        AuthResult result = sessionTokenProvider.refresh(cookieToken == null ? bodyToken : cookieToken, delivery);
        return tokenHttpSupport.respond(result, delivery, HttpStatus.OK);
    }

}
