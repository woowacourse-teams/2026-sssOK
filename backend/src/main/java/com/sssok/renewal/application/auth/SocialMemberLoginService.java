package com.sssok.renewal.application.auth;

import com.sssok.renewal.application.auth.exception.SocialAuthException;
import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.auth.RefreshTokenDelivery;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.MemberStatus;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.domain.member.SocialAccount;
import com.sssok.renewal.domain.member.SocialIdentity;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.renewal.outgoing.oauth.SocialAuthentication;
import com.sssok.renewal.outgoing.security.SocialTokenCipher;
import com.sssok.renewal.outgoing.security.SocialTokenCipher.TokenType;
import com.sssok.renewal.outgoing.security.TokenProvider.IssuedToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SocialMemberLoginService {

    private final MemberRepository memberRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final SessionTokenProvider sessionTokenProvider;
    private final MergeAnonymousMemberService mergeAnonymousMemberService;
    private final SocialTokenCipher socialTokenCipher;

    // 제공자가 검증한 식별자와 토큰만 받는다. 회원 변경·토큰 보관·인증 교체는 함께 커밋한다.
    @Transactional
    public AuthResult login(SocialAuthentication authentication, Long anonymousMemberId) {
        return login(authentication, anonymousMemberId, RefreshTokenDelivery.COOKIE);
    }

    @Transactional
    public AuthResult login(SocialAuthentication authentication, Long anonymousMemberId, RefreshTokenDelivery delivery) {
        return loginMany(authentication, anonymousMemberId == null ? List.of() : List.of(anonymousMemberId), delivery);
    }

    @Transactional
    public AuthResult loginMany(SocialAuthentication authentication, List<Long> anonymousMemberIds,
                                RefreshTokenDelivery delivery) {
        List<Long> sourceIds = anonymousMemberIds.stream().distinct().sorted().toList();
        Optional<SocialAccount> account = socialAccountRepository.findByIdentity(authentication.identity());
        Set<Long> lockIds = new TreeSet<>(sourceIds);
        account.ifPresent(value -> lockIds.add(value.memberId()));
        for (Long id : lockIds) {
            if (!memberRepository.lockById(id)) throw SocialAuthException.invalid();
        }
        sourceIds.forEach(id -> requireMemberStatus(id, MemberStatus.ANONYMOUS));
        EncryptedSocialTokens encryptedTokens = encryptTokens(authentication);
        Long firstSource = sourceIds.isEmpty() ? null : sourceIds.getFirst();
        Member member = resolveLoginMember(authentication.identity(), firstSource);
        for (Long sourceId : sourceIds) {
            if (!sourceId.equals(firstSource)) mergeAnonymousMemberService.merge(sourceId, member.getId());
        }
        socialAccountRepository.updateTokens(authentication.identity(), encryptedTokens);

        IssuedToken token = sessionTokenProvider.issue(member.getId(), Instant.now(), delivery);
        return AuthResult.from(member, token);
    }

    private Member resolveLoginMember(SocialIdentity identity, Long anonymousMemberId) {
        Optional<SocialAccount> socialAccount = socialAccountRepository.findByIdentity(identity);
        if (socialAccount.isEmpty()) {
            return registerMember(identity, anonymousMemberId);
        }

        Long registeredMemberId = socialAccount.get().memberId();
        if (anonymousMemberId == null) {
            return lockMember(registeredMemberId, MemberStatus.REGISTERED);
        }
        return mergeAnonymousMemberService.merge(anonymousMemberId, registeredMemberId);
    }

    private Member registerMember(SocialIdentity identity, Long anonymousMemberId) {
        Instant now = Instant.now();
        Member member = anonymousMemberId == null
            ? Member.register(new Nickname("사용자"), now)
            : lockMember(anonymousMemberId, MemberStatus.ANONYMOUS);
        member.completeRegistration();
        member = memberRepository.save(member);
        socialAccountRepository.save(SocialAccount.link(member.getId(), identity, now));
        // 익명 시절 발급한 토큰·연결 코드로 가입된 계정에 다시 접근하지 못하게 한다.
        sessionTokenProvider.revokeAll(member.getId());
        return member;
    }

    private Member lockMember(Long memberId, MemberStatus expectedStatus) {
        if (!memberRepository.lockById(memberId)) {
            throw SocialAuthException.invalid();
        }
        return requireMemberStatus(memberId, expectedStatus);
    }

    private Member requireMemberStatus(Long memberId, MemberStatus expectedStatus) {
        Member member = memberRepository.findById(memberId).orElseThrow(SocialAuthException::invalid);
        if (member.getStatus() != expectedStatus) {
            throw SocialAuthException.conflict();
        }
        return member;
    }

    private EncryptedSocialTokens encryptTokens(SocialAuthentication authentication) {
        SocialIdentity identity = authentication.identity();
        String encryptedAccessToken = socialTokenCipher.encrypt(identity, TokenType.ACCESS, authentication.accessToken());
        String encryptedRefreshToken = authentication.refreshToken() == null ? null
            : socialTokenCipher.encrypt(identity, TokenType.REFRESH, authentication.refreshToken());
        return new EncryptedSocialTokens(encryptedAccessToken, encryptedRefreshToken);
    }
}
