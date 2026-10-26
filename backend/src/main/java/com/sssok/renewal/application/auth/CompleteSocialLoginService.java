package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CompleteSocialLoginService {

    private final OAuthLoginAttemptService oauthLoginAttemptService;
    private final List<SocialAuthClient> socialAuthClients;
    private final SocialMemberLoginService socialMemberLoginService;

    // 제공자 HTTP 호출 동안 DB 트랜잭션이나 회원 잠금을 유지하지 않는다.
    public AuthResult login(SocialProvider provider, String code, String state, String loginVerifier) {
        return login(provider, code, state, loginVerifier, RefreshTokenDelivery.COOKIE);
    }

    public AuthResult login(SocialProvider provider, String code, String state, String loginVerifier,
                            RefreshTokenDelivery delivery) {
        if (code == null || code.isBlank() || code.length() > 2048) {
            throw SocialAuthException.invalid();
        }
        OAuthLoginAttempt attempt = oauthLoginAttemptService.consume(provider, state, loginVerifier);
        SocialAuthClient socialAuthClient = findSocialAuthClient(provider);
        SocialAuthentication authentication = socialAuthClient.authenticate(code, attempt.nonce());
        if (authentication.identity().provider() != provider) {
            throw SocialAuthException.invalid();
        }
        try {
            return socialMemberLoginService.loginMany(authentication, attempt.anonymousMemberIds(), delivery);
        } catch (DataIntegrityViolationException e) {
            // 동일 소셜 계정의 동시 가입은 DB 유일성 제약으로 막는다. 롤백 후 새 시도로 재시작한다.
            throw SocialAuthException.conflict();
        }
    }

    private SocialAuthClient findSocialAuthClient(SocialProvider provider) {
        return socialAuthClients.stream()
            .filter(socialAuthClient -> socialAuthClient.provider() == provider)
            .findFirst()
            .orElseThrow(SocialAuthException::unavailable);
    }
}
