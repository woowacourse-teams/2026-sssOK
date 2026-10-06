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
    private final ObjectProvider<TextEmbeddingPort> embeddings;
    private final ImageSearchResultAssembler assembler;
    private final ImageSearchQueryProperties properties;
    private final boolean enabled;

    public SearchImagesService(ObjectProvider<TextEmbeddingPort> embeddings,
        ImageSearchResultAssembler assembler, ImageSearchQueryProperties properties,
        @Value("${media.search.enabled:false}") boolean enabled) {
        this.embeddings = embeddings;
        this.assembler = assembler;
        this.properties = properties;
        this.enabled = enabled;
    }

    // 임베딩 네트워크 호출을 DB 트랜잭션 밖에서 실행한다.
    public List<ImageSearchResult> search(Long roomId, String query) {
        String text = query == null ? "" : query.replaceAll("(?U)\\s+", " ").strip();
        if (text.isEmpty() || text.codePointCount(0, text.length()) > 200) {
            throw new InvalidSearchQueryException();
        }
        if (!enabled || properties.minSimilarity() == null) {
            throw new ImageSearchUnavailableException();
        }
        TextEmbeddingPort.Embedding embedding;
        try {
            var provider = embeddings.getIfAvailable();
            if (provider == null) {
                throw new ImageSearchUnavailableException();
            }
            embedding = provider.embed(text, properties.timeout());
            if (embedding == null || embedding.model() == null || embedding.model().isBlank()
                || embedding.values() == null || embedding.values().isEmpty()
                || embedding.values().stream().anyMatch(value -> value == null
                    || !Float.isFinite(value.floatValue()))
                || embedding.values().stream().allMatch(value -> value.floatValue() == 0)) {
                throw new ImageSearchUnavailableException();
            }
        } catch (RuntimeException exception) {
            throw new ImageSearchUnavailableException();
        }
        String vector = embedding.values().stream().map(String::valueOf)
            .collect(Collectors.joining(",", "[", "]"));
        return assembler.search(roomId, vector, embedding.model(), embedding.values().size(),
            properties.minSimilarity());
    }
}
