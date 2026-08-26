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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.random.RandomGenerator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

// LinkLoginService가 실제 Repository 빈을 통해 코드를 조회·소비하고 토큰을 발급하는지 확인하는 통합 테스트.
// 기본 CRUD만 검증하므로 H2로 빠르게 돈다.
// H2 설정은 application-test.yml(test 프로파일)에 모아뒀다 (docs/backend/TEST_CONVENTION.md 참고).
@SpringBootTest
@ActiveProfiles("test")
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
    void 회원이_사라진_코드면_예외() {
        // 방이 정리되면서 회원이 지워지면 코드만 남는다. 이때 500 이 아니라 코드 오류로 나가야 한다.
        LinkCodeValue code = LinkCodeValue.generate(RandomGenerator.of("Random"));
        linkCodeRepository.save(LinkCode.issue(-1L, code, Instant.now()));

        assertThatThrownBy(() -> linkLoginService.login(code.value()))
            .isInstanceOf(LinkCodeNotFoundException.class);
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

    @Test
    void 만료된_코드는_로그인_시도_시점에_청소된다() {
        AuthResult registered = anonymousAuthService.authenticate("로지");
        LinkCodeValue code = LinkCodeValue.generate(RandomGenerator.of("Random"));
        LinkCode expired = LinkCode.issue(registered.userId(), code, Instant.now().minus(10, ChronoUnit.MINUTES));
        linkCodeRepository.save(expired);

        assertThatThrownBy(() -> linkLoginService.login(code.value()))
            .isInstanceOf(LinkCodeExpiredException.class);

        // 방치돼있던 행이 지워졌는지 확인 — 같은 코드로 다시 시도하면 이제는 404(찾을 수 없음)여야 한다.
        assertThatThrownBy(() -> linkLoginService.login(code.value()))
            .isInstanceOf(LinkCodeNotFoundException.class);
    }

    @Test
    void 동시에_같은_코드로_로그인하면_하나만_성공한다() throws InterruptedException {
        AuthResult registered = anonymousAuthService.authenticate("로지");
        LinkCodeResult issued = issueLinkCodeService.issue(registered.userId());

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);

        List<Future<AuthResult>> futures = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                readyLatch.countDown();
                startLatch.await();
                return linkLoginService.login(issued.linkCode());
            }));
        }
        readyLatch.await();
        startLatch.countDown();

        int successCount = 0;
        int notFoundCount = 0;
        for (Future<AuthResult> future : futures) {
            try {
                future.get();
                successCount++;
            } catch (ExecutionException e) {
                assertThat(e.getCause()).isInstanceOf(LinkCodeNotFoundException.class);
                notFoundCount++;
            }
        }
        executor.shutdown();

        assertThat(successCount).isEqualTo(1);
        assertThat(notFoundCount).isEqualTo(1);
    }
}
