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
    // refresh 토큰이 없을 때 기존 refresh는 같은 발급 client일 때만 유지한다(NULL은 웹).
    // 다른 client의 refresh가 새 client 메타데이터에 붙지 않게 access·refresh·client 종류를 한 번에 바꾼다.
    @Modifying(flushAutomatically = true)
    @Query(value = """
        UPDATE social_account
        SET encrypted_access_token = :accessToken,
            encrypted_refresh_token = CASE
                WHEN CAST(:refreshToken AS text) IS NOT NULL THEN CAST(:refreshToken AS text)
                WHEN COALESCE(token_client_type, 'WEB') = :clientType THEN encrypted_refresh_token
                ELSE NULL END,
            token_client_type = :clientType, updated_at = now()
        WHERE provider = :provider AND provider_user_id = :subject
        """, nativeQuery = true)
    int updateTokens(@Param("provider") String provider, @Param("subject") String subject,
        @Param("accessToken") String accessToken, @Param("refreshToken") String refreshToken,
        @Param("clientType") String clientType);

    @Query("""
        SELECT new com.sssok.renewal.domain.auth.EncryptedSocialTokens(a.encryptedAccessToken, a.encryptedRefreshToken, a.tokenClientType)
        FROM SocialAccountJpaEntity a WHERE a.provider = :provider AND a.providerUserId = :subject
        """)
    Optional<EncryptedSocialTokens> findTokens(@Param("provider") SocialProvider provider, @Param("subject") String subject);
}

