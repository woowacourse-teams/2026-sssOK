package com.sssok.presentation.api.admin;

import com.sssok.application.admin.AdminAuthResult;
import com.sssok.application.admin.LoginAdminService;
import com.sssok.presentation.api.common.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "관리자 인증", description = "관리자 로그인. 익명 사용자 인증과는 완전히 별개의 경로다.")
@RestController
@RequestMapping("/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final LoginAdminService loginAdminService;

    @Operation(
        summary = "관리자 로그인",
        description = """
            관리자가 아이디·비밀번호로 로그인해 관리자 토큰을 받는다. 익명 사용자 인증\
            (/auth/anonymous)과는 완전히 별개의 경로이고, 발급되는 토큰도 다른 주체를 가리킨다.

            받은 토큰은 이후 관리자 API 요청의 Authorization: Bearer 헤더에 담는다. \
            이 토큰으로 익명 사용자용 API(/rooms/**)를 호출하면 401이 난다 — 토큰에 주체 구분이 \
            들어 있어 회원으로 오인되지 않는다. 반대로 익명 사용자 토큰으로 관리자 API를 \
            호출해도 401이 난다 — 관리자 토큰이 아니라 토큰 단계에서 걸린다. 403은 관리자로 \
            인증은 됐지만 그 작업에 필요한 권한이 없을 때만 난다.

            비밀번호는 단방향 해시로만 저장하며 어떤 응답에도 싣지 않는다.

            로그인에 실패하면 아이디가 틀렸는지 비밀번호가 틀렸는지 구분하지 않고 같은 401을 \
            돌려준다. 구분해 주면 어떤 아이디가 존재하는지 알려주는 꼴이 되기 때문이다. \
            실패가 반복되면 429가 나고 Retry-After 헤더에 다시 시도할 수 있는 시각까지의 초가 담긴다.\
            """
    )
    @PostMapping("/login")
    public ApiResponse<AdminLoginResponse> login(@RequestBody AdminLoginRequest request) {
        String loginId = request == null ? null : request.loginId();
        String password = request == null ? null : request.password();
        AdminAuthResult result = loginAdminService.login(loginId, password);
        return ApiResponse.of(AdminLoginResponse.from(result));
    }
}
