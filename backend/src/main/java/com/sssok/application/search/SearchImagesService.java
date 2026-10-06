package com.sssok.application.search;

import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.application.search.exception.ImageSearchUnavailableException;
import com.sssok.application.search.exception.InvalidSearchQueryException;
import com.sssok.infrastructure.config.ImageSearchQueryProperties;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;

@Service
@EnableConfigurationProperties(ImageSearchQueryProperties.class)
public class SearchImagesService {
    private static final int MAX_QUERY_LENGTH = 200;

    private final ObjectProvider<TextEmbeddingPort> embeddingProvider;
    private final ImageSearchResultAssembler resultAssembler;
    private final ImageSearchQueryProperties properties;
    private final boolean enabled;

    public SearchImagesService(ObjectProvider<TextEmbeddingPort> embeddingProvider,
        ImageSearchResultAssembler resultAssembler, ImageSearchQueryProperties properties,
        @Value("${media.search.enabled:false}") boolean enabled) {
        this.embeddingProvider = embeddingProvider;
        this.resultAssembler = resultAssembler;
        this.properties = properties;
        this.enabled = enabled;
    }

    // 임베딩 네트워크 호출을 DB 트랜잭션 밖에서 실행한다.
    public List<ImageSearchResult> search(Long roomId, String query) {
        String text = normalizeQuery(query);
        requireSearchEnabled();
        TextEmbeddingPort.Embedding embedding = embedQuery(text);
        String vector = embedding.values().stream().map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
        return resultAssembler.search(roomId, vector, embedding.model(), embedding.values().size(),
            properties.minSimilarity());
    }

    private String normalizeQuery(String query) {
        String text = query == null ? "" : query.replaceAll("(?U)\\s+", " ").strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > MAX_QUERY_LENGTH) {
            throw new InvalidSearchQueryException();
        }
        return text;
    }

    private void requireSearchEnabled() {
        if (!enabled || properties.minSimilarity() == null) {
            throw new ImageSearchUnavailableException();
        }
    }

    private TextEmbeddingPort.Embedding embedQuery(String text) {
        try {
            TextEmbeddingPort provider = embeddingProvider.getIfAvailable();
            if (provider == null) {
                throw new ImageSearchUnavailableException();
            }
            TextEmbeddingPort.Embedding embedding = provider.embed(text, properties.timeout());
            requireValidEmbedding(embedding);
            return embedding;
        } catch (RuntimeException exception) {
            throw new ImageSearchUnavailableException();
        }
    }

    private void requireValidEmbedding(TextEmbeddingPort.Embedding embedding) {
        if (embedding == null || embedding.model() == null || embedding.model().isBlank()
            || embedding.values() == null || embedding.values().isEmpty()) {
            throw new ImageSearchUnavailableException();
        }
        boolean hasNonZeroValue = false;
        for (Double value : embedding.values()) {
            if (value == null || !Float.isFinite(value.floatValue())) {
                throw new ImageSearchUnavailableException();
            }
            hasNonZeroValue |= value.floatValue() != 0;
        }
        if (!hasNonZeroValue) {
            throw new ImageSearchUnavailableException();
        }
    }
}
