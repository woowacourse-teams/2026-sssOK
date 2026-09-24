package com.sssok.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sssok.domain.file.DerivativeFormat;
import com.sssok.infrastructure.config.DerivativeImageProperties.Variant;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.context.ConfigurationPropertiesAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

// 이 값들은 측정으로 정한 것이라(docs/backend/IMAGE_DERIVATIVE_FORMAT.md), 설정이 잘못 들어와
// 조용히 다른 값으로 도는 일이 없어야 한다.
class DerivativeImagePropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ConfigurationPropertiesAutoConfiguration.class))
        .withUserConfiguration(TestConfig.class);

    @Test
    void application_yml_의_값을_그대로_바인딩한다() {
        runner.withPropertyValues(
                "media.image.format=webp",
                "media.image.thumbnail.max-width=400",
                "media.image.thumbnail.quality=0.80",
                "media.image.preview.max-width=1600",
                "media.image.preview.quality=0.85")
            .run(context -> {
                DerivativeImageProperties properties =
                    context.getBean(DerivativeImageProperties.class);

                assertThat(properties.format()).isEqualTo(DerivativeFormat.WEBP);
                assertThat(properties.thumbnail().maxWidth()).isEqualTo(400);
                assertThat(properties.thumbnail().quality()).isEqualTo(0.80f);
                assertThat(properties.preview().maxWidth()).isEqualTo(1600);
                assertThat(properties.preview().quality()).isEqualTo(0.85f);
            });
    }

    // 설정을 통째로 빠뜨려도 결정된 값으로 돌아야 한다. 여기서 기본값이 어긋나면 운영과 테스트가
    // 서로 다른 품질로 돌면서도 아무 신호가 없다.
    @Test
    void 설정이_없으면_측정으로_정한_기본값을_쓴다() {
        runner.run(context -> {
            DerivativeImageProperties properties =
                context.getBean(DerivativeImageProperties.class);

            assertThat(properties.format()).isEqualTo(DerivativeFormat.WEBP);
            assertThat(properties.thumbnail()).isEqualTo(new Variant(400, 0.80f));
            assertThat(properties.preview()).isEqualTo(new Variant(1600, 0.85f));
        });
    }

    // 포맷을 되돌릴 자리를 남겨 뒀다 — WebP 네이티브가 어떤 플랫폼에서 안 뜨면 설정만 바꿔 내려앉는다.
    @Test
    void 포맷을_JPEG으로_되돌릴_수_있다() {
        runner.withPropertyValues("media.image.format=jpeg")
            .run(context -> assertThat(
                context.getBean(DerivativeImageProperties.class).format())
                .isEqualTo(DerivativeFormat.JPEG));
    }

    @Test
    void 품질이_범위를_벗어나면_기동에_실패한다() {
        runner.withPropertyValues("media.image.preview.quality=1.5")
            .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void 너비가_0_이하면_기동에_실패한다() {
        runner.withPropertyValues("media.image.thumbnail.max-width=0")
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
    }

    @EnableConfigurationProperties(DerivativeImageProperties.class)
    static class TestConfig {
    }
}
