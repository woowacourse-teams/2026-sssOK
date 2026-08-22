package com.sssok.presentation.api.auth;

// 형식 검증(길이 등)은 Nickname 값 객체가 담당한다.
public record AnonymousAuthRequest(String nickname) {
}
