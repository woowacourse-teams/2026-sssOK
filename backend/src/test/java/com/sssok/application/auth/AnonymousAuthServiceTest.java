package com.sssok.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.application.port.out.MemberRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Service가 실제 Repository 빈을 통해 회원을 저장하고 토큰을 발급하는지 확인하는 통합 테스트.
// 기본 CRUD만 검증하므로 PostgreSQL 전용 기능이 필요 없어 H2로 빠르게 돈다.
// H2 설정은 application-test.yml(test 프로파일)에 모아뒀다 (docs/backend/TEST_CONVENTION.md 참고).
@SpringBootTest
@ActiveProfiles("test")
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
