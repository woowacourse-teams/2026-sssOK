package com.sssok.presentation.api.auth;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.auth.IssueLinkCodeService;
import com.sssok.application.auth.LinkCodeResult;
import com.sssok.application.auth.LinkLoginService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "인증", description = "닉네임만으로 시작하는 익명 인증, 그리고 다른 기기에서 같은 계정을 이어 쓰기 위한 연결 코드 발급·로그인")
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AnonymousAuthService anonymousAuthService;
    private final IssueLinkCodeService issueLinkCodeService;
    private final LinkLoginService linkLoginService;

    @Operation(
        summary = "닉네임으로 익명 인증",
        description = "닉네임만 입력하면 별도 회원가입 절차 없이 즉시 계정을 만들고 accessToken을 발급한다. "
            + "사용법: 서비스 최초 진입 시 이 API를 호출해서 받은 accessToken을, 이후 모든 요청의 "
            + "Authorization: Bearer {accessToken} 헤더에 실어 보내면 된다."
    )
    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AuthResponse>> createAnonymous(@RequestBody AnonymousAuthRequest request) {
        AuthResult result = anonymousAuthService.authenticate(request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(AuthResponse.from(result)));
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
    public ApiResponse<AuthResponse> login(@RequestBody LinkLoginRequest request) {
        AuthResult result = linkLoginService.login(request.linkCode());
        return ApiResponse.of(AuthResponse.from(result));
    }
}
