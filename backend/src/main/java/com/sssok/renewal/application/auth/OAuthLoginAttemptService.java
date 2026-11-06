package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.OAuthClientType;
import com.sssok.renewal.domain.auth.OAuthLoginAttempt;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.SocialProvider;
import com.sssok.renewal.outgoing.cache.auth.OAuthLoginAttemptRepository;
import com.sssok.renewal.outgoing.config.AppleOAuthProperties;
import com.sssok.renewal.outgoing.config.KakaoOAuthProperties;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.oauth.AppleAuthClient;
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
import java.util.Optional;
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
    private final Optional<AppleAuthClient> appleClient;
    private final KakaoOAuthProperties kakaoOAuthProperties;
    private final AppleOAuthProperties appleOAuthProperties;

    // 익명 회원 ID는 전달받은 서비스 인증 토큰으로 확인한다. 인증이 없으면 빈 목록을 받는다.
    @Transactional(readOnly = true)
    public OAuthLoginAttemptResult start(SocialProvider provider, List<String> anonymousAccessTokens) {
        return start(provider, anonymousAccessTokens, null);
    }

    // requestOrigin은 카카오 콜백 선택에만 쓰며, 선택한 주소를 로그인 시도에 저장해 토큰 교환까지 같은 값을 쓴다.
    @Transactional(readOnly = true)
    public OAuthLoginAttemptResult start(SocialProvider provider, List<String> anonymousAccessTokens, String requestOrigin) {
        if (anonymousAccessTokens.size() > 100) {
            throw SocialAuthException.invalid();
        }
        requireAvailable(provider);
        String redirectUri = selectRedirectUri(provider, requestOrigin);
        List<Long> anonymousMemberIds = anonymousAccessTokens.stream()
            .map(this::resolveAnonymousMemberId).distinct().sorted().toList();
        String nonce = provider == SocialProvider.APPLE ? generateRandomToken() : null;
        return issueAttempt(provider, OAuthClientType.WEB, redirectUri, nonce, anonymousMemberIds);
    }

    // 앱 SDK 로그인 시도를 시작한다. 웹 주소를 만들지 않고, 서버가 만든 nonce를 앱이 SDK 인증 요청에 그대로 넣게 한다.
    // 익명 활동 통합은 이번 계약에 없으므로 익명 회원은 연결하지 않는다.
    public OAuthLoginAttemptResult startAppleNative() {
        requireNativeAvailable(SocialProvider.APPLE);
        return issueAttempt(SocialProvider.APPLE, OAuthClientType.NATIVE, null, generateRandomToken(), List.of());
    }

    private OAuthLoginAttemptResult issueAttempt(SocialProvider provider, OAuthClientType clientType, String redirectUri,
                                                 String nonce, List<Long> anonymousMemberIds) {
        String state = generateRandomToken();
        String loginVerifier = generateRandomToken();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(LOGIN_ATTEMPT_TTL);

        oauthLoginAttemptRepository.save(new OAuthLoginAttempt(
            hashToken(state), hashToken(loginVerifier), provider, redirectUri, nonce,
            anonymousMemberIds, now, expiresAt, clientType
        ));
        return new OAuthLoginAttemptResult(provider, state, loginVerifier, nonce, redirectUri, expiresAt);
    }

    // 웹 완료 API용이다. 앱 SDK 시도는 거절한다.
    @Transactional(readOnly = true)
    public OAuthLoginAttempt consume(SocialProvider provider, String state, String loginVerifier) {
        return consume(provider, state, loginVerifier, OAuthClientType.WEB);
    }

    // 회원 확인에만 DB 트랜잭션을 사용한다. 캐시 소비는 DB 롤백으로 복원되지 않는다.
    // 제공자 호출은 이 메서드가 반환된 뒤 트랜잭션 밖에서 수행한다.
    @Transactional(readOnly = true)
    public OAuthLoginAttempt consume(SocialProvider provider, String state, String loginVerifier,
                                     OAuthClientType clientType) {
        if (clientType == OAuthClientType.NATIVE) {
            requireNativeAvailable(provider);
        } else {
            requireAvailable(provider);
        }
        String stateHash = hashToken(state);
        String loginVerifierHash = hashToken(loginVerifier);
        OAuthLoginAttempt attempt = oauthLoginAttemptRepository.findByStateHash(stateHash)
            .orElseThrow(SocialAuthException::invalid);

        validateAttempt(attempt, provider, loginVerifierHash, clientType);
        attempt.anonymousMemberIds().forEach(this::requireAnonymousMember);
        if (!oauthLoginAttemptRepository.consume(stateHash, loginVerifierHash, provider, Instant.now())) {
            throw SocialAuthException.invalid();
        }
        return attempt;
    }

    private void validateAttempt(
        OAuthLoginAttempt attempt, SocialProvider provider, String loginVerifierHash, OAuthClientType clientType
    ) {
        // 웹 시도와 앱 SDK 시도는 서로의 완료 API에서 사용할 수 없다.
        if (attempt.clientType() != clientType || attempt.provider() != provider
            || !attempt.loginVerifierHash().equals(loginVerifierHash) || attempt.isExpiredAt(Instant.now())
            || (clientType == OAuthClientType.WEB && !isAllowedRedirectUri(provider, attempt.redirectUri()))) {
            throw SocialAuthException.invalid();
        }
    }

    private Long resolveAnonymousMemberId(String anonymousAccessToken) {
        Long memberId = sessionTokenProvider.parse(anonymousAccessToken);
        requireAnonymousMember(memberId);
        return memberId;
    }

    private void requireAnonymousMember(Long memberId) {
        Member member = memberRepository.findById(memberId).orElseThrow(SocialAuthException::invalid);
        if (member.getStatus() != MemberStatus.ANONYMOUS) {
            throw SocialAuthException.invalid();
        }
    }

    private void requireAvailable(SocialProvider provider) {
        if (provider == null || socialAuthClients.stream().noneMatch(client -> client.provider() == provider)) {
            throw SocialAuthException.unavailable();
        }
    }

    private void requireNativeAvailable(SocialProvider provider) {
        if (provider != SocialProvider.APPLE || !appleClient.filter(AppleAuthClient::nativeConfigured).isPresent()) {
            throw SocialAuthException.unavailable();
        }
    }

    // 카카오는 요청 출처에 등록된 콜백만, 애플은 기존 고정 주소만 사용한다. 출처를 못 찾아도 다른 주소로 대체하지 않는다.
    private String selectRedirectUri(SocialProvider provider, String requestOrigin) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthProperties.callbackFor(requestOrigin).orElseThrow(SocialAuthException::invalid);
            case APPLE -> appleOAuthProperties.redirectUri();
        };
    }

    // 저장된 주소가 지금도 허용 목록에 있어야 한다. 설정에서 빠진 주소로 시작된 시도는 거절한다.
    private boolean isAllowedRedirectUri(SocialProvider provider, String redirectUri) {
        return switch (provider) {
            case KAKAO -> kakaoOAuthProperties.isAllowedCallback(redirectUri);
            case APPLE -> appleOAuthProperties.redirectUri().equals(redirectUri);
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
