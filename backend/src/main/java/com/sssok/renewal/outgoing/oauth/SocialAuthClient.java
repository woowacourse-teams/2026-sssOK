package com.sssok.renewal.outgoing.oauth;

import com.sssok.renewal.domain.member.SocialProvider;

public interface SocialAuthClient {
    SocialProvider provider();

    // 서버에서 state를 소비하고 검증한 뒤 호출한다. expectedNonce는 요청 본문을 신뢰하지 않고
    // 해당 로그인 시도에 서버가 저장했던 값을 사용한다. 카카오 사용자 정보 조회 방식은 nonce를 사용하지 않는다.
    // redirectUri도 시작 시 서버가 저장한 값이다. 인가 요청과 같은 값을 토큰 교환에 써야 하는 카카오가 사용하고,
    // 애플은 기존처럼 설정된 고정 주소를 쓰므로 이 값을 사용하지 않는다.
    SocialAuthentication authenticate(String authorizationCode, String expectedNonce, String redirectUri);
    // 내부 탈퇴 흐름에서만 호출한다. 서비스 회원·세션·방 데이터는 변경하지 않는다.
    void unlink(SocialAuthentication authentication);
}

