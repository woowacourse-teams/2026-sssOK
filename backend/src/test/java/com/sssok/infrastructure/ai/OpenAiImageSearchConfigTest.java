package com.sssok.infrastructure.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.infrastructure.config.OpenAiImageSearchConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class OpenAiImageSearchConfigTest {
    private final ApplicationContextRunner context = new ApplicationContextRunner()
        .withUserConfiguration(OpenAiImageSearchConfig.class)
        .withBean(ObjectMapper.class, ObjectMapper::new)
        .withPropertyValues("media.search.provider=openai");

    @Test
    void 비활성화하면_API_키_없이_기동하고_외부_호출_빈은_없다() {
        context.withPropertyValues("media.search.enabled=false").run(result -> {
            assertThat(result).hasNotFailed().doesNotHaveBean(OpenAiSearchClient.class)
                .doesNotHaveBean(ImageDescriptionPort.class).doesNotHaveBean(TextEmbeddingPort.class);
        });
    }

    @Test
    void 활성화하고_키가_없으면_기동_시에_알린다() {
        context.withPropertyValues("media.search.enabled=true").run(result -> {
            assertThat(result).hasFailed();
            assertThat(result.getStartupFailure()).hasRootCauseMessage("OPENAI_API_KEY 설정이 필요합니다");
        });
    }

    @Test
    void 활성화하면_두_출력_포트를_OpenAI_어댑터에_연결한다() {
        context.withPropertyValues("media.search.enabled=true", "media.search.openai.api-key=test-key")
            .run(result -> {
                assertThat(result).hasNotFailed().hasSingleBean(OpenAiSearchClient.class)
                    .hasSingleBean(ImageDescriptionPort.class).hasSingleBean(TextEmbeddingPort.class);
                assertThat(result.getBean(ImageDescriptionPort.class)).isInstanceOf(OpenAiImageDescriptionAdapter.class);
                assertThat(result.getBean(TextEmbeddingPort.class)).isInstanceOf(OpenAiTextEmbeddingAdapter.class);
            });
    }
}
