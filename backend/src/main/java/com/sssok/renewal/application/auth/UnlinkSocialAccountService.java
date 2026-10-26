package com.sssok.renewal.application.auth;

import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.renewal.outgoing.security.SocialTokenCipher;
import com.sssok.renewal.outgoing.security.SocialTokenCipher.TokenType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UnlinkSocialAccountService {
    private final SocialAccountRepository socialAccountRepository;
    private final SocialTokenCipher socialTokenCipher;
    private final List<SocialAuthClient> socialAuthClients;

    // 외부 API를 DB 트랜잭션 안에서 호출하지 않는다. 탈퇴 커밋 후 한 번 호출한다.
    @Transactional(propagation = Propagation.NEVER)
    public void unlink(SocialIdentity identity) {
        SocialAuthClient socialAuthClient = socialAuthClients.stream()
            .filter(client -> client.provider() == identity.provider())
            .findFirst()
            .orElseThrow(SocialAuthException::unavailable);
        SocialAuthentication authentication = loadAuthentication(identity);
        socialAuthClient.unlink(authentication);
        // 제공자 성공과 DB 커밋은 원자적이지 않다. 로컬 회원·토큰 삭제는 탈퇴 흐름에서 처리한다.
    }

    private SocialAuthentication loadAuthentication(SocialIdentity identity) {
        // 카카오는 어드민 키와 회원번호로 연결을 해제하므로 보관 토큰에 의존하지 않는다.
        if (identity.provider() == SocialProvider.KAKAO) {
            return new SocialAuthentication(identity, null, null);
        }
        EncryptedSocialTokens storedTokens = socialAccountRepository.findTokens(identity)
            .orElseThrow(SocialAuthException::reauthenticationRequired);
        if (storedTokens.accessToken() == null && storedTokens.refreshToken() == null) {
            throw SocialAuthException.reauthenticationRequired();
        }

        String accessToken = decryptToken(identity, TokenType.ACCESS, storedTokens.accessToken());
        String refreshToken = decryptToken(identity, TokenType.REFRESH, storedTokens.refreshToken());
        return new SocialAuthentication(identity, accessToken, refreshToken);
    }

    private String decryptToken(SocialIdentity identity, TokenType tokenType, String encryptedToken) {
        if (encryptedToken == null) {
            return null;
        }
        return socialTokenCipher.decrypt(identity, tokenType, encryptedToken);
    }
}
