package com.sssok.renewal.domain.auth;

// 같은 제공자라도 웹 로그인과 앱 SDK 로그인은 서로 다른 제공자 client 설정을 쓴다.
// 로그인 시도, 토큰 교환, 보관 토큰의 연결 해제가 모두 같은 설정을 가리키도록 이 값을 끝까지 전달한다.
public enum OAuthClientType {
    WEB, NATIVE
}
