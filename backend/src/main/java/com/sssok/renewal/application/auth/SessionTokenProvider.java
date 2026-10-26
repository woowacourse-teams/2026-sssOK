package com.sssok.renewal.application.auth;

import com.sssok.infrastructure.config.JwtProperties;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.outgoing.database.auth.AuthSessionJpaEntity;
import com.sssok.renewal.outgoing.database.auth.AuthSessionJpaRepository;
import com.sssok.renewal.outgoing.database.auth.LegacyAuthRevocationJpaEntity;
import com.sssok.renewal.outgoing.database.auth.LegacyAuthRevocationRepository;
import com.sssok.renewal.outgoing.database.auth.LinkCodeRepository;
import com.sssok.renewal.outgoing.database.auth.RefreshTokenJpaEntity;
import com.sssok.renewal.outgoing.database.auth.RefreshTokenJpaRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.security.JwtTokenProvider.VerifiedToken;
import com.sssok.renewal.outgoing.security.JwtTokenProvider;
import com.sssok.renewal.outgoing.security.TokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionTokenProvider implements TokenProvider {
    private static final Duration REFRESH_TTL = Duration.ofDays(30);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final JwtProperties jwtProperties;
    private final RefreshTokenJpaRepository refreshTokenJpaRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthSessionJpaRepository authSessionJpaRepository;
    private final LegacyAuthRevocationRepository legacyAuthRevocationRepository;
    private final MemberRepository memberRepository;
    private final LinkCodeRepository linkCodeRepository;

    @Override
    @Transactional
    public IssuedToken issue(Long memberId, Instant now) {
        return issue(memberId, now, RefreshTokenDelivery.COOKIE);
    }

    @Override
    @Transactional
    public IssuedToken issue(Long memberId, Instant now, RefreshTokenDelivery delivery) {
        lockMember(memberId);
        requireActive(memberId);
        AuthSessionJpaEntity session = authSessionJpaRepository.save(new AuthSessionJpaEntity(
            UUID.randomUUID().toString(), memberId, now.plus(REFRESH_TTL), delivery));
        return issueTokens(session, now);
    }

    // 재사용 감지 시 세션 폐기를 커밋한 뒤 401을 반환한다.
    @Transactional(noRollbackFor = UnauthorizedException.class)
    public AuthResult refresh(String rawToken, RefreshTokenDelivery delivery) {
        if (rawToken == null || !rawToken.matches("[A-Za-z0-9_-]{43}")) {
            throw unauthorized();
        }
        String hash = hash(rawToken);
        // 잠그기 전에는 소유자만 조회한다. 소비 여부는 회원 잠금을 획득한 뒤 새로 읽는다.
        String sessionId = refreshTokenJpaRepository.findSessionId(hash).orElseThrow(this::unauthorized);
        Long memberId = authSessionJpaRepository.findMemberId(sessionId).orElseThrow(this::unauthorized);
        lockMember(memberId);
        AuthSessionJpaEntity session = authSessionJpaRepository.findById(sessionId).orElseThrow(this::unauthorized);
        RefreshTokenJpaEntity refreshToken = refreshTokenJpaRepository.findById(hash).orElseThrow(this::unauthorized);
        Instant now = Instant.now();
        requireActive(memberId);
        if (session.isRevoked() || !session.getExpiresAt().isAfter(now)
            || session.getRefreshTokenDelivery() != delivery) {
            throw unauthorized();
        }
        if (refreshToken.isUsed()) {
            session.revoke();
            throw unauthorized();
        }
        refreshToken.consume();
        Member member = memberRepository.findById(memberId).orElseThrow(this::unauthorized);
        return AuthResult.from(member, issueTokens(session, now));
    }

    private IssuedToken issueTokens(AuthSessionJpaEntity session, Instant now) {
        Instant expiresAt = now.plus(jwtProperties.accessTokenTtl());
        if (expiresAt.isAfter(session.getExpiresAt())) {
            expiresAt = session.getExpiresAt();
        }
        IssuedToken access = jwtTokenProvider.issue(session.getMemberId(), now, session.getId(), expiresAt);
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        refreshTokenJpaRepository.save(new RefreshTokenJpaEntity(hash(refresh), session.getId()));
        return new IssuedToken(access.value(), access.expiresAt(), refresh, session.getExpiresAt());
    }

    @Override
    @Transactional(readOnly = true)
    public Long parse(String token) {
        VerifiedToken verified = jwtTokenProvider.verify(token);
        requireActive(verified.memberId());
        requireValidSession(verified, token);
        return verified.memberId();
    }

    @Transactional
    public void logout(String token) {
        VerifiedToken verified = jwtTokenProvider.verify(token);
        lockMember(verified.memberId());
        requireActive(verified.memberId());
        requireValidSession(verified, token);
        if (verified.sessionId() == null) {
            authSessionJpaRepository.save(new AuthSessionJpaEntity(sessionKey(verified, token),
                verified.memberId(), verified.expiresAt(), true));
        } else {
            authSessionJpaRepository.findById(verified.sessionId()).orElseThrow(this::unauthorized).revoke();
        }
    }

    // 통합·탈퇴 서비스의 트랜잭션 안에서도 호출할 수 있다. 새 로그인 세션 발급은 허용한다.
    @Transactional
    public void revokeAll(Long memberId) {
        lockMember(memberId);
        authSessionJpaRepository.revokeAll(memberId);
        legacyAuthRevocationRepository.save(new LegacyAuthRevocationJpaEntity(memberId));
        linkCodeRepository.deleteAllByMemberId(memberId);
    }

    private void requireValidSession(VerifiedToken token, String raw) {
        Optional<AuthSessionJpaEntity> session = authSessionJpaRepository.findById(sessionKey(token, raw));
        if (token.sessionId() == null) {
            if (legacyAuthRevocationRepository.existsById(token.memberId()) || session.isPresent()) {
                throw unauthorized();
            }
            return;
        }
        AuthSessionJpaEntity stored = session.orElseThrow(this::unauthorized);
        if (stored.isRevoked() || !stored.getMemberId().equals(token.memberId())
            || !stored.getExpiresAt().isAfter(Instant.now())) {
            throw unauthorized();
        }
    }

    private void lockMember(Long memberId) {
        if (!memberRepository.lockById(memberId)) {
            throw unauthorized();
        }
    }

    private void requireActive(Long memberId) {
        if (!memberRepository.findById(memberId).orElseThrow(this::unauthorized).isActive()) {
            throw unauthorized();
        }
    }

    private String sessionKey(VerifiedToken token, String raw) {
        if (token.sessionId() != null) {
            return token.sessionId();
        }
        return hash(raw);
    }

    private String hash(String raw) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(raw.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다", e);
        }
    }

    private UnauthorizedException unauthorized() {
        return new UnauthorizedException("다시 접속해주세요");
    }
}
