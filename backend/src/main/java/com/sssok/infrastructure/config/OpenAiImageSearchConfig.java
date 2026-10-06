package com.sssok.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.port.out.ImageDescriptionPort;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.infrastructure.ai.OpenAiImageDescriptionAdapter;
import com.sssok.infrastructure.ai.OpenAiSearchClient;
import com.sssok.infrastructure.ai.OpenAiTextEmbeddingAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(name = "media.search.provider", havingValue = "openai")
@EnableConfigurationProperties(OpenAiImageSearchProperties.class)
public class OpenAiImageSearchConfig {
    @Bean
    @ConditionalOnProperty(name = "media.search.enabled", havingValue = "true")
    public OpenAiSearchClient openAiSearchClient(OpenAiImageSearchProperties properties) {
        return new OpenAiSearchClient(properties);
    }

    @Bean
    @ConditionalOnProperty(name = "media.search.enabled", havingValue = "true")
    public ImageDescriptionPort imageDescriptionPort(OpenAiSearchClient client,
        OpenAiImageSearchProperties properties, ObjectMapper mapper) {
        return new OpenAiImageDescriptionAdapter(client, properties, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "media.search.enabled", havingValue = "true")
    public TextEmbeddingPort textEmbeddingPort(OpenAiSearchClient client, OpenAiImageSearchProperties properties) {
        return new OpenAiTextEmbeddingAdapter(client, properties);
    }
}
