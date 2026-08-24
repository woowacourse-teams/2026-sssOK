package com.sssok.presentation.api.auth;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.application.auth.IssueLinkCodeService;
import com.sssok.application.auth.LinkCodeResult;
import com.sssok.application.auth.LinkLoginService;
import com.sssok.presentation.api.common.ApiResponse;
import com.sssok.presentation.auth.AuthMember;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AnonymousAuthService anonymousAuthService;
    private final IssueLinkCodeService issueLinkCodeService;
    private final LinkLoginService linkLoginService;

    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AuthResponse>> createAnonymous(@RequestBody AnonymousAuthRequest request) {
        AuthResult result = anonymousAuthService.authenticate(request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(AuthResponse.from(result)));
    }

    @PostMapping("/link-code")
    public ResponseEntity<ApiResponse<LinkCodeResponse>> createLinkCode(@AuthMember Long memberId) {
        LinkCodeResult result = issueLinkCodeService.issue(memberId);
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(LinkCodeResponse.from(result)));
    }

    @PostMapping("/link")
    public ApiResponse<AuthResponse> login(@RequestBody LinkLoginRequest request) {
        AuthResult result = linkLoginService.login(request.linkCode());
        return ApiResponse.of(AuthResponse.from(result));
    }
}
