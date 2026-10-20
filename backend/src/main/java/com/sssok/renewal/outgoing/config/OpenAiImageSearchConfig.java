package com.sssok.renewal.outgoing.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.outgoing.ai.OpenAiImageDescriptionClient;
import com.sssok.renewal.outgoing.ai.OpenAiTextEmbeddingClient;
import com.sssok.renewal.outgoing.ai.OpenAiSearchClient;
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
    public OpenAiImageDescriptionClient imageDescriptionClient(OpenAiSearchClient client,
        OpenAiImageSearchProperties properties, ObjectMapper mapper) {
        return new OpenAiImageDescriptionClient(client, properties, mapper);
    }

    @Bean
    @ConditionalOnProperty(name = "media.search.enabled", havingValue = "true")
    public OpenAiTextEmbeddingClient textEmbeddingClient(OpenAiSearchClient client, OpenAiImageSearchProperties properties) {
        return new OpenAiTextEmbeddingClient(client, properties);
    }
}
