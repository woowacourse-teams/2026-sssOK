package com.sssok.application.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.application.auth.exception.LinkCodeExpiredException;
import com.sssok.application.auth.exception.LinkCodeNotFoundException;
import com.sssok.application.port.out.LinkCodeRepository;
import com.sssok.domain.auth.LinkCode;
import com.sssok.domain.auth.LinkCodeValue;
import com.sssok.domain.auth.exception.InvalidLinkCodeException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// LinkLoginService가 실제 Repository 빈을 통해 코드를 조회·소비하고 토큰을 발급하는지 확인하는 통합 테스트.
// 기본 CRUD만 검증하므로 H2로 빠르게 돈다 (docs/backend/TEST_CONVENTION.md 참고).
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:link-login-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class LinkLoginServiceTest {

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    IssueLinkCodeService issueLinkCodeService;

    @Autowired
    LinkLoginService linkLoginService;

    @Autowired
    LinkCodeRepository linkCodeRepository;

    @Test
    void 유효한_코드로_로그인하면_같은_회원의_새_토큰을_받는다() {
        AuthResult registered = anonymousAuthService.authenticate("로지");
        LinkCodeResult issued = issueLinkCodeService.issue(registered.userId());

        AuthResult loggedIn = linkLoginService.login(issued.linkCode());

        assertThat(loggedIn.userId()).isEqualTo(registered.userId());
        assertThat(loggedIn.nickname()).isEqualTo("로지");
        assertThat(loggedIn.accessToken()).isNotBlank();
    }

    @Test
    void 로그인에_성공하면_코드는_즉시_폐기된다() {
        AuthResult registered = anonymousAuthService.authenticate("로지");
        LinkCodeResult issued = issueLinkCodeService.issue(registered.userId());

        linkLoginService.login(issued.linkCode());

        assertThatThrownBy(() -> linkLoginService.login(issued.linkCode()))
            .isInstanceOf(LinkCodeNotFoundException.class);
    }

    @Test
    void 존재하지_않는_코드면_예외() {
        assertThatThrownBy(() -> linkLoginService.login("999999"))
            .isInstanceOf(LinkCodeNotFoundException.class);
    }

    @Test
    void 형식이_잘못된_코드면_예외() {
        assertThatThrownBy(() -> linkLoginService.login("abc"))
            .isInstanceOf(InvalidLinkCodeException.class);
    }

    @Test
    void 만료된_코드면_예외() {
        AuthResult registered = anonymousAuthService.authenticate("로지");
        LinkCodeValue code = LinkCodeValue.generate(RandomGenerator.of("Random"));
        LinkCode expired = LinkCode.issue(registered.userId(), code, Instant.now().minus(10, ChronoUnit.MINUTES));
        linkCodeRepository.save(expired);

        assertThatThrownBy(() -> linkLoginService.login(code.value()))
            .isInstanceOf(LinkCodeExpiredException.class);
    }
}
