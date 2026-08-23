package com.sssok.application.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.infrastructure.persistence.auth.LinkCodeJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// Service가 실제 Repository 빈을 통해 연결 코드를 저장/무효화하는지 확인하는 통합 테스트.
// 기본 CRUD만 검증하므로 H2로 빠르게 돈다.
// H2 설정은 application-test.yml(test 프로파일)에 모아뒀다 (docs/backend/TEST_CONVENTION.md 참고).
@SpringBootTest
@ActiveProfiles("test")
class IssueLinkCodeServiceTest {

    @Autowired
    IssueLinkCodeService issueLinkCodeService;

    @Autowired
    LinkCodeJpaRepository linkCodeJpaRepository;

    @Test
    void 코드를_발급하면_6자리_숫자와_만료시각을_받는다() {
        LinkCodeResult result = issueLinkCodeService.issue(10L);

        assertThat(result.linkCode()).matches("\\d{6}");
        assertThat(result.expiresAt()).isNotNull();
    }

    @Test
    void 같은_회원이_다시_발급하면_이전_코드는_무효화된다() {
        issueLinkCodeService.issue(10L);
        issueLinkCodeService.issue(10L);

        assertThat(linkCodeJpaRepository.findAll())
            .filteredOn(entity -> entity.getMemberId().equals(10L))
            .hasSize(1);
    }
}
