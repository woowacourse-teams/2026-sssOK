package com.sssok.presentation.api.auth;

// 형식 검증(6자리 숫자 여부)은 LinkCodeValue 값 객체가 담당한다.
public record LinkLoginRequest(String linkCode) {
}
