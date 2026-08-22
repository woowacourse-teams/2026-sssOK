package com.sssok.presentation.api.auth;

import com.sssok.application.auth.AnonymousAuthService;
import com.sssok.application.auth.AuthResult;
import com.sssok.presentation.api.common.ApiResponse;
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

    @PostMapping("/anonymous")
    public ResponseEntity<ApiResponse<AuthResponse>> createAnonymous(@RequestBody AnonymousAuthRequest request) {
        AuthResult result = anonymousAuthService.authenticate(request.nickname());
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.of(AuthResponse.from(result)));
    }
}
