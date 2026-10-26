package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.cache.auth.OAuthLoginAttemptRepository;
import com.sssok.renewal.outgoing.config.AppleOAuthProperties;
import com.sssok.renewal.outgoing.config.KakaoOAuthProperties;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.oauth.SocialAuthClient;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class OAuthLoginAttemptService {

    private static final Duration LOGIN_ATTEMPT_TTL = Duration.ofMinutes(5);
    private static final int RANDOM_TOKEN_BYTES = 32;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthLoginAttemptRepository oauthLoginAttemptRepository;
    private final MemberRepository memberRepository;
    private final SessionTokenProvider sessionTokenProvider;
    private final List<SocialAuthClient> socialAuthClients;
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final AppleOAuthProperties appleOAuthProperties;

    // 익명 회원 ID를 요청에서 받지 않고 서비스 인증 토큰에서 확인한다. 인증 없이 시작하려면 null을 전달한다.
    @Transactional(readOnly = true)
    public OAuthLoginAttemptResult start(SocialProvider provider, String anonymousAccessToken) {
        return startMany(provider, anonymousAccessToken == null ? List.of() : List.of(anonymousAccessToken));
    }

    @Transactional(readOnly = true)
    public OAuthLoginAttemptResult startMany(SocialProvider provider, List<String> anonymousAccessTokens) {
        if (anonymousAccessTokens.size() > 100) throw SocialAuthException.invalid();
        String redirectUri = requireEnabledRedirectUri(provider);
        List<Long> anonymousMemberIds = anonymousAccessTokens.stream()
            .map(this::resolveAnonymousMemberId).distinct().sorted().toList();
        String state = generateRandomToken();
        String loginVerifier = generateRandomToken();
        String nonce = provider == SocialProvider.APPLE ? generateRandomToken() : null;
        Instant now = Instant.now();
        Instant expiresAt = now.plus(LOGIN_ATTEMPT_TTL);

        oauthLoginAttemptRepository.save(new OAuthLoginAttempt(
            hashToken(state), hashToken(loginVerifier), provider, redirectUri, nonce,
            anonymousMemberIds, now, expiresAt
        ));
        return new OAuthLoginAttemptResult(provider, state, loginVerifier, nonce, redirectUri, expiresAt);
    }

    // 회원 확인에만 DB 트랜잭션을 사용한다. 캐시 소비는 DB 롤백으로 복원되지 않는다.
    // 제공자 호출은 이 메서드가 반환된 뒤 트랜잭션 밖에서 수행한다.
    @Transactional(readOnly = true)
    public OAuthLoginAttempt consume(SocialProvider provider, String state, String loginVerifier) {
        String redirectUri = requireEnabledRedirectUri(provider);
        String stateHash = hashToken(state);
        String loginVerifierHash = hashToken(loginVerifier);
        OAuthLoginAttempt attempt = oauthLoginAttemptRepository.findByStateHash(stateHash)
            .orElseThrow(SocialAuthException::invalid);

        validateAttempt(attempt, provider, loginVerifierHash, redirectUri);
        attempt.anonymousMemberIds().forEach(this::requireAnonymousMember);
        if (!oauthLoginAttemptRepository.consume(stateHash, loginVerifierHash, provider, Instant.now())) {
            throw SocialAuthException.invalid();
        }
        return attempt;
    }

    private void validateAttempt(
        OAuthLoginAttempt attempt, SocialProvider provider, String loginVerifierHash, String redirectUri
    ) {
        if (attempt.provider() != provider || !attempt.loginVerifierHash().equals(loginVerifierHash)
            || !attempt.redirectUri().equals(redirectUri) || attempt.isExpiredAt(Instant.now())) {
            throw SocialAuthException.invalid();
        }
    }

    private Long resolveAnonymousMemberId(String anonymousAccessToken) {
        if (anonymousAccessToken == null) {
            return null;
        }
        Long memberId = sessionTokenProvider.parse(anonymousAccessToken);
        requireAnonymousMember(memberId);
        return memberId;
    }

    private void requireAnonymousMember(Long memberId) {
        if (memberId == null) {
            return;
        }
        Member member = memberRepository.findById(memberId).orElseThrow(SocialAuthException::invalid);
        if (member.getStatus() != MemberStatus.ANONYMOUS) {
            throw SocialAuthException.invalid();
        }
    }

    private String requireEnabledRedirectUri(SocialProvider provider) {
        if (provider == null || socialAuthClients.stream().noneMatch(client -> client.provider() == provider)) {
            throw SocialAuthException.unavailable();
        }
        return switch (provider) {
            case KAKAO -> kakaoOAuthProperties.redirectUri();
            case APPLE -> appleOAuthProperties.redirectUri();
        };
    }

    private String generateRandomToken() {
        byte[] bytes = new byte[RANDOM_TOKEN_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hashToken(String token) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) {
            throw SocialAuthException.invalid();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }
}
