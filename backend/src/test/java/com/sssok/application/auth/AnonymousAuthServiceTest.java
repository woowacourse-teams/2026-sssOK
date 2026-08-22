package com.sssok.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

// Service가 실제 Repository 빈을 통해 회원을 저장하고 토큰을 발급하는지 확인하는 통합 테스트.
// 기본 CRUD만 검증하므로 PostgreSQL 전용 기능이 필요 없어 H2로 빠르게 돈다 (Flyway는 끄고
// Hibernate가 엔티티로부터 스키마를 직접 생성한다 — 실제 운영 스키마 검증은 API 인수 테스트가 담당).
@SpringBootTest
@TestPropertySource(properties = {
    "spring.datasource.url=jdbc:h2:mem:auth-service-test;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.driver-class-name=org.h2.Driver",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false"
})
class AnonymousAuthServiceTest {

    @Autowired
    AnonymousAuthService anonymousAuthService;

    @Autowired
    MemberRepository memberRepository;

    @Test
    void 닉네임으로_인증하면_회원이_저장되고_토큰이_발급된다() {
        AuthResult result = anonymousAuthService.authenticate("로지");

        assertThat(result.userId()).isNotNull();
        assertThat(result.nickname()).isEqualTo("로지");
        assertThat(result.accessToken()).isNotBlank();
        assertThat(result.expiresAt()).isNotNull();
        assertThat(memberRepository.findById(result.userId())).isPresent();
    }

    @Test
    void 호출할_때마다_다른_회원이_생성된다() {
        AuthResult first = anonymousAuthService.authenticate("로지");
        AuthResult second = anonymousAuthService.authenticate("로지");

        assertThat(first.userId()).isNotEqualTo(second.userId());
    }
}
