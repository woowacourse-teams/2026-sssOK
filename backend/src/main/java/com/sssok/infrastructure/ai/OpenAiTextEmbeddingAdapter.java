package com.sssok.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.sssok.application.port.out.TextEmbeddingPort;
import com.sssok.infrastructure.config.OpenAiImageSearchProperties;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class OpenAiTextEmbeddingAdapter implements TextEmbeddingPort {
    private final OpenAiSearchClient client;
    private final OpenAiImageSearchProperties properties;

    @Override
    public Embedding embed(String text, Duration timeout) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("임베딩할 텍스트가 필요합니다");
        }
        EmbeddingRequest request = new EmbeddingRequest(properties.embeddingModel(), text,
            properties.dimensions(), "float");
        JsonNode response = client.post("/embeddings", request, timeout);
        JsonNode vector = extractVector(response);
        List<Double> values = readVectorValues(vector);
        return new Embedding(List.copyOf(values), properties.embeddingModel());
    }

    private JsonNode extractVector(JsonNode response) {
        JsonNode data = response.path("data");
        if (!data.isArray() || data.size() != 1
            || !properties.embeddingModel().equals(response.path("model").asText())) {
            throw new OpenAiCallException("INVALID_EMBEDDING_RESPONSE");
        }
        JsonNode item = data.get(0);
        if (!item.path("index").isIntegralNumber() || item.path("index").asInt() != 0) {
            throw new OpenAiCallException("INVALID_EMBEDDING_RESPONSE");
        }
        JsonNode vector = item.path("embedding");
        if (!vector.isArray() || vector.size() != properties.dimensions()) {
            throw new OpenAiCallException("EMBEDDING_DIMENSION_MISMATCH");
        }
        return vector;
    }

    private List<Double> readVectorValues(JsonNode vector) {
        List<Double> values = new ArrayList<>();
        boolean hasNonZeroValue = false;
        for (JsonNode value : vector) {
            if (!value.isNumber() || !Float.isFinite(value.floatValue())) {
                throw new OpenAiCallException("INVALID_EMBEDDING_VALUE");
            }
            values.add(value.doubleValue());
            hasNonZeroValue |= value.floatValue() != 0;
        }
        if (!hasNonZeroValue) {
            throw new OpenAiCallException("ZERO_EMBEDDING");
        }
        return values;
    }

    private record EmbeddingRequest(String model, String input, int dimensions,
                                    @JsonProperty("encoding_format") String encodingFormat) {
    }
}
