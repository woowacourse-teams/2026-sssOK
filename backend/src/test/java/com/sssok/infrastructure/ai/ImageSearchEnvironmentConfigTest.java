package com.sssok.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.sssok.infrastructure.config.ImageSearchProperties;
import com.sssok.infrastructure.config.ImageSearchQueryProperties;
import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.core.io.ClassPathResource;

class ImageSearchEnvironmentConfigTest {
    @Test
    void 안내한_환경변수로_분석_시작_시각과_검색_임계값을_설정한다() {
        new ApplicationContextRunner().withUserConfiguration(TestConfig.class)
            .withInitializer(context -> {
                context.getEnvironment().getPropertySources().addFirst(
                    new SystemEnvironmentPropertySource("imageSearchTestEnvironment", Map.of(
                        "MEDIA_SEARCH_CREATED_SINCE", "2026-10-06T05:58:28Z",
                        "MEDIA_SEARCH_QUERY_MIN_SIMILARITY", "0")));
                try {
                    new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"))
                        .forEach(source -> context.getEnvironment().getPropertySources().addLast(source));
                } catch (IOException exception) {
                    throw new IllegalStateException(exception);
                }
            }).run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(context.getBean(ImageSearchProperties.class).createdSince())
                    .isEqualTo(Instant.parse("2026-10-06T05:58:28Z"));
                assertThat(context.getBean(ImageSearchQueryProperties.class).minSimilarity()).isEqualTo(0);
            });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties({ImageSearchProperties.class, ImageSearchQueryProperties.class})
    static class TestConfig {
    }
}
