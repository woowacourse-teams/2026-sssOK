package com.sssok.presentation.api.auth;

import io.swagger.v3.oas.annotations.media.Schema;

// 형식 검증(6자리 숫자 여부)은 LinkCodeValue 값 객체가 담당한다.
public record LinkLoginRequest(
    @Schema(description = "다른 기기에서 발급받은 6자리 연결 코드", example = "483920") String linkCode
) {
}
