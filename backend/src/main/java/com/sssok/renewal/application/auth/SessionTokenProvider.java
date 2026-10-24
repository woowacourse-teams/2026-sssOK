package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.outgoing.database.auth.AuthSessionJpaEntity;
import com.sssok.renewal.outgoing.database.auth.AuthSessionJpaRepository;
import com.sssok.renewal.outgoing.database.auth.LegacyAuthRevocationJpaEntity;
import com.sssok.renewal.outgoing.database.auth.LegacyAuthRevocationRepository;
import com.sssok.renewal.outgoing.database.auth.LinkCodeRepository;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.security.JwtTokenProvider;
import com.sssok.renewal.outgoing.security.JwtTokenProvider.VerifiedToken;
import com.sssok.renewal.outgoing.security.TokenProvider;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionTokenProvider implements TokenProvider {
    private final JwtTokenProvider jwt;
    private final AuthSessionJpaRepository sessions;
    private final LegacyAuthRevocationRepository legacyRevocations;
    private final MemberRepository members;
    private final LinkCodeRepository linkCodes;

    @Override
    @Transactional
    public IssuedToken issue(Long memberId, Instant now) {
        lockMember(memberId);
        requireActive(memberId);
        IssuedToken token = jwt.issue(memberId, now);
        VerifiedToken verified = jwt.verify(token.value());
        sessions.save(new AuthSessionJpaEntity(verified.sessionId(), memberId, verified.expiresAt(), false));
        return token;
    }

    @Override
    @Transactional(readOnly = true)
    public Long parse(String token) {
        VerifiedToken verified = jwt.verify(token);
        requireActive(verified.memberId());
        requireValidSession(verified, token);
        return verified.memberId();
    }

    @Transactional
    public void logout(String token) {
        VerifiedToken verified = jwt.verify(token);
        lockMember(verified.memberId());
        requireActive(verified.memberId());
        requireValidSession(verified, token);
        sessions.save(new AuthSessionJpaEntity(sessionKey(verified, token),
            verified.memberId(), verified.expiresAt(), true));
    }

    // 통합·탈퇴 서비스의 트랜잭션 안에서도 호출할 수 있다. 새 로그인 세션 발급은 허용한다.
    @Transactional
    public void revokeAll(Long memberId) {
        lockMember(memberId);
        sessions.revokeAll(memberId);
        legacyRevocations.save(new LegacyAuthRevocationJpaEntity(memberId));
        linkCodes.deleteAllByMemberId(memberId);
    }

    private void requireValidSession(VerifiedToken token, String raw) {
        var session = sessions.findById(sessionKey(token, raw));
        if (token.sessionId() == null) {
            if (legacyRevocations.existsById(token.memberId()) || session.isPresent()) {
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
        if (!members.lockById(memberId)) {
            throw unauthorized();
        }
    }

    private void requireActive(Long memberId) {
        if (!members.findById(memberId).orElseThrow(this::unauthorized).isActive()) {
            throw unauthorized();
        }
    }

    private String sessionKey(VerifiedToken token, String raw) {
        if (token.sessionId() != null) {
            return token.sessionId();
        }
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
