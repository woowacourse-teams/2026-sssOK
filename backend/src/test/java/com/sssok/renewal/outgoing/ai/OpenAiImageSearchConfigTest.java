package com.sssok.renewal.outgoing.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.outgoing.config.OpenAiImageSearchConfig;
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
                .doesNotHaveBean(OpenAiImageDescriptionClient.class).doesNotHaveBean(OpenAiTextEmbeddingClient.class);
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
                    .hasSingleBean(OpenAiImageDescriptionClient.class).hasSingleBean(OpenAiTextEmbeddingClient.class);
                assertThat(result.getBean(OpenAiImageDescriptionClient.class)).isInstanceOf(OpenAiImageDescriptionClient.class);
                assertThat(result.getBean(OpenAiTextEmbeddingClient.class)).isInstanceOf(OpenAiTextEmbeddingClient.class);
            });
    }
}
