package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.infrastructure.config.DerivativeImageProperties.Variant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 이 값들은 측정으로 정한 것이라(docs/backend/IMAGE_DERIVATIVE_FORMAT.md), 설정이 잘못 들어와
// 조용히 다른 값으로 도는 일이 없어야 한다.
class DerivativeImagePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(TestConfig.class);

    // 값을 손으로 넣어 두고 재면 yml 이 바뀌어도 테스트는 그대로 통과한다. 그래서 실제
    // application.yml 을 읽어 확인한다 — 여기가 측정으로 정한 값이 실제로 실린 곳인지 보는 자리다.
    private final ApplicationContextRunner realConfigRunner = runner
        .withInitializer(new ConfigDataApplicationContextInitializer())
        .withPropertyValues("spring.profiles.active=test");

    @Test
    void application_yml_의_값을_그대로_바인딩한다() {
        realConfigRunner.run(context -> {
            DerivativeImageProperties properties =
                context.getBean(DerivativeImageProperties.class);

            assertThat(properties.format()).isEqualTo(DerivativeFormat.WEBP);
            assertThat(properties.thumbnail()).isEqualTo(new Variant(400, 0.80f));
            assertThat(properties.preview()).isEqualTo(new Variant(1600, 0.85f));
        });
    }

    // 코드에 기본값을 두지 않았으므로, 설정이 빠지면 조용히 다른 값으로 도는 대신 못 뜬다.
    @Test
    void 설정이_통째로_없으면_기동에_실패한다() {
        runner.run(context -> assertThat(context).hasFailed());
    }

    // preview 의 한쪽만 빠졌을 때 thumbnail 기준 기본값이 새어 들어오면 안 된다.
    @Test
    void 파생본_설정이_일부만_있으면_기동에_실패한다() {
        runner.withPropertyValues(
                "media.image.format=webp",
                "media.image.thumbnail.max-width=400",
                "media.image.thumbnail.quality=0.80",
                "media.image.preview.max-width=1600")
            .run(context -> assertThat(context).hasFailed());
    }

    // 포맷을 되돌릴 자리를 남겨 뒀다 — WebP 네이티브가 어떤 플랫폼에서 안 뜨면 설정만 바꿔 내려앉는다.
    @Test
    void 포맷을_JPEG으로_되돌릴_수_있다() {
        realConfigRunner.withPropertyValues("media.image.format=jpeg")
            .run(context -> assertThat(
                context.getBean(DerivativeImageProperties.class).format())
                .isEqualTo(DerivativeFormat.JPEG));
    }

    @Test
    void 품질이_범위를_벗어나면_기동에_실패한다() {
        realConfigRunner.withPropertyValues("media.image.preview.quality=1.5")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 너비가_0_이하면_기동에_실패한다() {
        realConfigRunner.withPropertyValues("media.image.thumbnail.max-width=0")
            .run(context -> assertThat(context).hasFailed());
    }

    // 생성자가 직접 거절하는지도 본다. 컨텍스트 없이 쓰는 테스트에서 잘못된 값이 통과하면
    // 그 테스트만 다른 설정으로 도는 셈이 된다.
    @Test
    void 잘못된_값은_생성자에서_거절한다() {
        assertThatThrownBy(() -> new Variant(400, 0f))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Variant(-1, 0.8f))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Variant(400, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @EnableConfigurationProperties(DerivativeImageProperties.class)
    static class TestConfig {
    }
}
