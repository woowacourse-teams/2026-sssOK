package com.sssok.renewal.outgoing.database.member;

import com.sssok.renewal.domain.auth.EncryptedSocialTokens;
import com.sssok.renewal.domain.member.SocialProvider;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SocialAccountJpaRepository extends JpaRepository<SocialAccountJpaEntity, Long> {
    Optional<SocialAccountJpaEntity> findByProviderAndProviderUserId(SocialProvider provider, String providerUserId);

    List<SocialAccountJpaEntity> findByMemberId(Long memberId);

    @Modifying(flushAutomatically = true)
    @Query("delete from SocialAccountJpaEntity a where a.id = :id and a.memberId = :memberId")
    int deleteByIdAndMemberId(@Param("id") Long id, @Param("memberId") Long memberId);
    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE social_account
        SET encrypted_access_token = :accessToken,
            encrypted_refresh_token = COALESCE(:refreshToken, encrypted_refresh_token), updated_at = now()
        WHERE provider = :provider AND provider_user_id = :subject
        """, nativeQuery = true)
    int updateTokens(@Param("provider") String provider, @Param("subject") String subject,
        @Param("accessToken") String accessToken, @Param("refreshToken") String refreshToken);

    @Query("""
        SELECT new com.sssok.renewal.domain.auth.EncryptedSocialTokens(a.encryptedAccessToken, a.encryptedRefreshToken)
        FROM SocialAccountJpaEntity a WHERE a.provider = :provider AND a.providerUserId = :subject
        """)
    Optional<EncryptedSocialTokens> findTokens(@Param("provider") SocialProvider provider, @Param("subject") String subject);
}

