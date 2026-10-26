package com.sssok.renewal.outgoing.database.member;

import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.member.SocialAccount;
import com.sssok.renewal.domain.member.SocialIdentity;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class SocialAccountRepository {
    private final SocialAccountJpaRepository jpaRepository;

    public List<SocialAccount> findByMemberId(Long memberId) {
        return jpaRepository.findByMemberId(memberId).stream().map(SocialAccountJpaEntity::toDomain).toList();
    }

    public void deleteByIdAndMemberId(Long id, Long memberId) {
        if (jpaRepository.deleteByIdAndMemberId(id, memberId) != 1) {
            throw new IllegalStateException("연결 해제할 소셜 계정을 찾을 수 없습니다");
        }
    }

    public SocialAccount save(SocialAccount account) {
        return jpaRepository.save(new SocialAccountJpaEntity(account)).toDomain();
    }

    public Optional<SocialAccount> findByIdentity(SocialIdentity identity) {
        return jpaRepository.findByProviderAndProviderUserId(identity.provider(), identity.providerUserId())
            .map(SocialAccountJpaEntity::toDomain);
    }
    public void updateTokens(SocialIdentity identity, EncryptedSocialTokens tokens) {
        if (jpaRepository.updateTokens(identity.provider().name(), identity.providerUserId(),
            tokens.accessToken(), tokens.refreshToken()) != 1) {
            throw new IllegalStateException("토큰을 저장할 소셜 계정을 찾을 수 없습니다");
        }
    }

    public Optional<EncryptedSocialTokens> findTokens(SocialIdentity identity) {
        return jpaRepository.findTokens(identity.provider(), identity.providerUserId());
    }
}

