package com.sssok.presentation.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

// 형식 검증(길이 등)은 Nickname 값 객체가 담당한다.
public record AnonymousAuthRequest(
    @Schema(description = "화면에 표시할 닉네임", example = "민수") String nickname
) {
}
