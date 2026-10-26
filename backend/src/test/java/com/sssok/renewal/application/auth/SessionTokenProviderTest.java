package com.sssok.renewal.application.auth;

import com.sssok.infrastructure.config.JwtProperties;
import com.sssok.renewal.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.renewal.application.auth.exception.UnauthorizedException;
import com.sssok.renewal.domain.member.Member;
import com.sssok.renewal.domain.member.Nickname;
import com.sssok.renewal.outgoing.database.member.MemberRepository;
import com.sssok.renewal.outgoing.security.JwtTokenProvider;
import com.sssok.support.H2IntegrationTest;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;


@H2IntegrationTest
class SessionTokenProviderTest {
    @Autowired SessionTokenProvider sessions;
    @Autowired MemberRepository members;
    @Autowired JwtTokenProvider jwt;
    @Autowired JwtProperties properties;
    @Autowired IssueLinkCodeService linkCodes;
    @Autowired LinkLoginService linkLogin;

    @Test
    void 같은_시각에_로그인해도_서로_다른_세션이며_로그아웃은_현재_세션만_막는다() {
        Member member = member();
        Instant now = Instant.now();
        String first = sessions.issue(member.getId(), now).value();
        String second = sessions.issue(member.getId(), now).value();
        assertThat(first).isNotEqualTo(second);

        sessions.logout(first);

        rejected(first);
        assertThat(sessions.parse(second)).isEqualTo(member.getId());
        assertThatThrownBy(() -> sessions.logout(first)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 등록되지_않은_세션은_서명이_유효해도_사용할_수_없다() {
        rejected(jwt.issue(member().getId(), Instant.now()).value());
    }

    @Test
    void 구형_토큰은_유지하되_로그아웃하면_재사용할_수_없다() {
        Member member = member();
        String old = legacy(member.getId());
        String current = sessions.issue(member.getId(), Instant.now()).value();
        assertThat(sessions.parse(old)).isEqualTo(member.getId());

        sessions.logout(old);

        rejected(old);
        assertThat(sessions.parse(current)).isEqualTo(member.getId());
    }

    @Test
    void 전체_무효화는_미사용_구형_토큰과_연결_코드까지_막고_새_로그인은_허용한다() {
        Member member = member();
        String old = legacy(member.getId());
        String current = sessions.issue(member.getId(), Instant.now()).value();
        String code = linkCodes.issue(member.getId()).linkCode();

        sessions.revokeAll(member.getId());

        rejected(old);
        rejected(current);
        assertThatThrownBy(() -> linkLogin.login(code))
            .isInstanceOf(LinkCodeNotFoundException.class);
        assertThat(sessions.parse(sessions.issue(member.getId(), Instant.now()).value())).isEqualTo(member.getId());
    }

    @Test
    void 통합된_익명_사용자는_구형과_신형_인증_및_새_발급이_차단된다() {
        Member member = member();
        String old = legacy(member.getId());
        String current = sessions.issue(member.getId(), Instant.now()).value();
        member.markMerged();
        members.save(member);

        rejected(old);
        rejected(current);
        assertThatThrownBy(() -> sessions.issue(member.getId(), Instant.now())).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> linkCodes.issue(member.getId())).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 탈퇴한_회원은_남아있는_연결_코드로도_인증할_수_없다() {
        Member member = member();
        member.completeRegistration();
        members.save(member);
        String current = sessions.issue(member.getId(), Instant.now()).value();
        String code = linkCodes.issue(member.getId()).linkCode();
        member.withdraw();
        members.save(member);

        rejected(current);
        assertThatThrownBy(() -> linkLogin.login(code)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void 없는_사용자와_만료_토큰은_거부한다() {
        rejected(legacy(Long.MAX_VALUE));
        String expired = jwt.issue(member().getId(), Instant.now().minus(properties.accessTokenTtl()).minusSeconds(5)).value();
        rejected(expired);
    }

    private Member member() {
        return members.save(Member.register(new Nickname("로지"), Instant.now()));
    }

    private void rejected(String token) {
        assertThatThrownBy(() -> sessions.parse(token)).isInstanceOf(UnauthorizedException.class);
    }

    private String legacy(Long memberId) {
        return Jwts.builder().subject(memberId.toString())
            .issuedAt(new Date()).expiration(Date.from(Instant.now().plusSeconds(3600)))
            .signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(properties.secret())))
            .compact();
    }
}
