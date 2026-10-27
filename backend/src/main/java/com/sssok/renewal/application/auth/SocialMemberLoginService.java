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
import com.sssok.renewal.outgoing.security.SocialTokenCipher.TokenType;
import com.sssok.renewal.outgoing.security.SocialTokenCipher;
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

    // 회원 변경·제공자 토큰 보관·서비스 인증 교체는 함께 커밋한다.
    @Transactional
    public AuthResult login(SocialAuthentication authentication, List<Long> anonymousMemberIds,
                            RefreshTokenDelivery delivery) {
        List<Long> sourceIds = anonymousMemberIds.stream().distinct().sorted().toList();
        lockLoginMembers(authentication.identity(), sourceIds);
        sourceIds.forEach(id -> requireMemberStatus(id, MemberStatus.ANONYMOUS));

        Member member = resolveLoginMember(authentication.identity(), sourceIds);
        mergeAnonymousMembers(sourceIds, member);
        socialAccountRepository.updateTokens(authentication.identity(), encryptTokens(authentication));

        IssuedToken token = sessionTokenProvider.issue(member.getId(), Instant.now(), delivery);
        return AuthResult.from(member, token);
    }

    private void lockLoginMembers(SocialIdentity identity, List<Long> sourceIds) {
        Set<Long> memberIds = new TreeSet<>(sourceIds);
        socialAccountRepository.findByIdentity(identity).ifPresent(account -> memberIds.add(account.memberId()));
        for (Long memberId : memberIds) {
            if (!memberRepository.lockById(memberId)) {
                throw SocialAuthException.invalid();
            }
        }
    }

    private Member resolveLoginMember(SocialIdentity identity, List<Long> sourceIds) {
        // 잠금을 기다리는 동안 소셜 계정이 연결될 수 있으므로 다시 확인한다.
        Optional<SocialAccount> account = socialAccountRepository.findByIdentity(identity);
        if (account.isPresent()) {
            return lockMember(account.get().memberId(), MemberStatus.REGISTERED);
        }
        return registerMember(identity, sourceIds);
    }

    private Member registerMember(SocialIdentity identity, List<Long> sourceIds) {
        Instant now = Instant.now();
        Member member = sourceIds.isEmpty()
            ? Member.register(new Nickname("사용자"), now)
            : lockMember(sourceIds.getFirst(), MemberStatus.ANONYMOUS);
        member.completeRegistration();
        member = memberRepository.save(member);
        socialAccountRepository.save(SocialAccount.link(member.getId(), identity, now));
        sessionTokenProvider.revokeAll(member.getId());
        return member;
    }

    private void mergeAnonymousMembers(List<Long> sourceIds, Member member) {
        sourceIds.stream()
            .filter(sourceId -> !member.isSame(sourceId))
            .forEach(sourceId -> mergeAnonymousMemberService.merge(sourceId, member.getId()));
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
