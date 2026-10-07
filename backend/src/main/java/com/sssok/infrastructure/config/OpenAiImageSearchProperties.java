package com.sssok.infrastructure.config;

import java.net.URI;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "media.search.openai")
public record OpenAiImageSearchProperties(
    String apiKey,
    URI baseUrl,
    String descriptionModel,
    String embeddingModel,
    Integer dimensions,
    Integer maxOutputTokens
) {
    public OpenAiImageSearchProperties {
        baseUrl = baseUrl == null ? URI.create("https://api.openai.com/v1") : baseUrl;
        descriptionModel = descriptionModel == null ? "gpt-6-luna" : descriptionModel;
        embeddingModel = embeddingModel == null ? "text-embedding-3-small" : embeddingModel;
        dimensions = dimensions == null ? 1536 : dimensions;
        maxOutputTokens = maxOutputTokens == null ? 384 : maxOutputTokens;
        if (descriptionModel.isBlank() || embeddingModel.isBlank()
            || dimensions < 1 || dimensions > 1536 || maxOutputTokens < 128 || maxOutputTokens > 1024) {
            throw new IllegalArgumentException("OpenAI 이미지 검색 설정을 확인해주세요");
        }
        requireValidBaseUrl(baseUrl);
    }

    private static void requireValidBaseUrl(URI baseUrl) {
        boolean isHttps = "https".equals(baseUrl.getScheme());
        boolean isLocalHttp = "http".equals(baseUrl.getScheme())
            && ("localhost".equals(baseUrl.getHost()) || "127.0.0.1".equals(baseUrl.getHost()));
        boolean hasUnexpectedComponents = baseUrl.getHost() == null || baseUrl.getUserInfo() != null
            || baseUrl.getQuery() != null || baseUrl.getFragment() != null;
        // 외부 호출은 HTTPS만 허용한다. HTTP는 로컬 어댑터 테스트용이다.
        if (hasUnexpectedComponents || (!isHttps && !isLocalHttp)) {
            throw new IllegalArgumentException("OpenAI 이미지 검색 설정을 확인해주세요");
        }
    }

    public void requireApiKey() {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalArgumentException("OPENAI_API_KEY 설정이 필요합니다");
        }
    }

    @Override
    public String toString() {
        return "OpenAiImageSearchProperties[apiKey=REDACTED, descriptionModel=" + descriptionModel
            + ", embeddingModel=" + embeddingModel + ", dimensions=" + dimensions + "]";
    }
}
