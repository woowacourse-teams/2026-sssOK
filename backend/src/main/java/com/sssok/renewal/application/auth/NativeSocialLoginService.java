package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.outgoing.oauth.KakaoAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

// 앱 SDK 로그인. 앱이 네이티브 SDK로 인증을 끝내고 웹뷰가 그 결과를 넘긴다. 제공자별 검증 방식은 그대로 두고,
// 검증 뒤의 회원 처리와 서비스 토큰 발급은 웹 로그인과 같은 CompleteSocialLoginService.loginVerified를 쓴다.
// 요청에 익명 인증 정보가 없으므로 익명 활동은 통합하지 않는다. 클라이언트 빈은 제공자 설정이 꺼져 있으면 없다.
@Service
@RequiredArgsConstructor
public class NativeSocialLoginService {

    private final CompleteSocialLoginService completeSocialLoginService;
    private final Optional<KakaoAuthClient> kakaoClient;

    // 카카오는 시작 단계 없이 SDK access token을 서버가 직접 확인한다. 제공자 HTTP 호출 동안 DB 트랜잭션을 유지하지 않는다.
    public AuthResult loginWithKakao(String kakaoAccessToken, RefreshTokenDelivery delivery) {
        KakaoAuthClient client = kakaoClient.orElseThrow(SocialAuthException::unavailable);
        SocialAuthentication authentication = client.authenticateAccessToken(kakaoAccessToken);
        return completeSocialLoginService.loginVerified(authentication, List.of(), delivery);
    }
}
