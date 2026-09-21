package com.sssok.presentation.api.admin;

import io.swagger.v3.oas.annotations.media.Schema;

public record CreateAdminRequest(
    @Schema(description = "로그인 아이디. 4~20자 영소문자·숫자·밑줄", example = "newadmin") String loginId,
    @Schema(description = "초기 비밀번호. 8~72바이트") String password,
    @Schema(description = "표시 이름", example = "새관리자") String name,
    @Schema(description = "SUPER_ADMIN 또는 ADMIN. 생략하면 ADMIN", example = "ADMIN") String role
) {
}
