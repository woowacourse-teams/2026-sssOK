package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
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

    private static final int MAX_CODE_LENGTH = 2048;

    private final OAuthLoginAttemptService oauthLoginAttemptService;
    private final CompleteSocialLoginService completeSocialLoginService;
    private final Optional<KakaoAuthClient> kakaoClient;
    private final Optional<AppleAuthClient> appleClient;

    // 카카오는 시작 단계 없이 SDK access token을 서버가 직접 확인한다. 제공자 HTTP 호출 동안 DB 트랜잭션을 유지하지 않는다.
    public AuthResult loginWithKakao(String kakaoAccessToken, RefreshTokenDelivery delivery) {
        KakaoAuthClient client = kakaoClient.orElseThrow(SocialAuthException::unavailable);
        SocialAuthentication authentication = client.authenticateAccessToken(kakaoAccessToken);
        return completeSocialLoginService.loginVerified(authentication, List.of(), delivery);
    }

    // 애플은 SDK 인증 전에 서버가 로그인 시도(state·loginVerifier·nonce)를 만든다. 웹 로그인 URL은 만들지 않는다.
    public OAuthLoginAttemptResult startApple() {
        return oauthLoginAttemptService.startAppleNative();
    }

    // 시도는 외부 교환 전에 원자적으로 소비한다. 교환이 실패해도 복원하거나 같은 코드를 재시도하지 않으며,
    // 새 시작 단계부터 다시 로그인해야 한다.
    public AuthResult loginWithApple(String code, String state, String loginVerifier, RefreshTokenDelivery delivery) {
        if (code == null || code.isBlank() || code.length() > MAX_CODE_LENGTH) {
            throw SocialAuthException.invalid();
        }
        OAuthLoginAttempt attempt = oauthLoginAttemptService.consume(
            SocialProvider.APPLE, state, loginVerifier, OAuthClientType.NATIVE);
        AppleAuthClient client = appleClient.orElseThrow(SocialAuthException::unavailable);
        SocialAuthentication authentication = client.authenticateNative(code, attempt.nonce());
        return completeSocialLoginService.loginVerified(authentication, attempt.anonymousMemberIds(), delivery);
    }
}
