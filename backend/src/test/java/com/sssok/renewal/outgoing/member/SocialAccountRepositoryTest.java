package com.sssok.renewal.outgoing.member;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.renewal.domain.member.*;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.database.member.SocialAccountRepository;
import com.sssok.support.PostgresContainerSupport;
import com.sssok.support.PostgresIntegrationTest;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@PostgresIntegrationTest
@Transactional
class SocialAccountRepositoryTest extends PostgresContainerSupport {
    @Autowired MemberRepository memberRepository;
    @Autowired SocialAccountRepository socialAccountRepository;
    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 같은_문자열_ID라도_제공자가_다르면_별도_계정이다() {
        Long first = member();
        Long second = member();
        SocialIdentity kakao = new SocialIdentity(SocialProvider.KAKAO, "123");
        SocialIdentity apple = new SocialIdentity(SocialProvider.APPLE, "123");
        socialAccountRepository.save(SocialAccount.link(first, kakao, Instant.now()));
        socialAccountRepository.save(SocialAccount.link(second, apple, Instant.now()));
        assertThat(socialAccountRepository.findByIdentity(kakao).orElseThrow().memberId()).isEqualTo(first);
        assertThat(socialAccountRepository.findByIdentity(apple).orElseThrow().memberId()).isEqualTo(second);
    }

    @Test
    void 같은_소셜_계정을_여러_회원에게_연결할_수_없다() {
        SocialIdentity identity = new SocialIdentity(SocialProvider.KAKAO, "unique");
        Long first = member();
        Long second = member();
        socialAccountRepository.save(SocialAccount.link(first, identity, Instant.now()));
        assertThatThrownBy(() -> socialAccountRepository.save(SocialAccount.link(second, identity, Instant.now())))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 없는_회원에게_계정을_연결할_수_없다() {
        assertThatThrownBy(() -> socialAccountRepository.save(SocialAccount.link(Long.MAX_VALUE,
            new SocialIdentity(SocialProvider.APPLE, "missing"), Instant.now())))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @ParameterizedTest
    @ValueSource(strings = {"UNKNOWN", ""})
    void DB도_정의되지_않은_제공자를_거부한다(String provider) {
        Long memberId = member();
        assertThatThrownBy(() -> jdbcTemplate.update("INSERT INTO social_account(member_id, provider, provider_user_id, created_at, updated_at) VALUES (?, ?, 'id', now(), now())",
            memberId, provider)).isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void 명시한_유일성_외래키_인덱스가_실제_DB에_존재한다() {
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_constraint WHERE conname IN "
            + "('uk_social_account_identity', 'fk_social_account_member', 'ck_social_account_provider', 'ck_social_account_subject') AND convalidated", Integer.class))
            .isEqualTo(4);
        assertThat(jdbcTemplate.queryForObject("SELECT count(*) FROM pg_indexes WHERE indexname = 'idx_social_account_member'", Integer.class)).isEqualTo(1);
    }

    private Long member() {
        return memberRepository.save(Member.register(new Nickname("소셜"), Instant.now())).getId();
    }
}
