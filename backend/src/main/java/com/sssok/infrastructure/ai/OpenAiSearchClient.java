package com.sssok.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.application.search.exception.AiCallException.RetryDisposition;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import com.sssok.infrastructure.config.OpenAiImageSearchProperties;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

public class OpenAiSearchClient {
    private final OpenAiImageSearchProperties properties;
    private final HttpClient http;
    private final ObjectMapper mapper = new ObjectMapper();
    private CachedClient cachedClient;

    private record CachedClient(Duration timeout, RestClient client) { }

    // 가장 최근 타임아웃 하나만 보관한다. 공유 factory의 설정을 변경하지 않는다.
    private synchronized RestClient clientFor(Duration timeout) {
        if (cachedClient == null || !cachedClient.timeout().equals(timeout)) {
            JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
            factory.setReadTimeout(timeout);
            RestClient client = RestClient.builder().baseUrl(properties.baseUrl().toString())
                .requestFactory(factory).defaultHeader("Authorization", "Bearer " + properties.apiKey()).build();
            cachedClient = new CachedClient(timeout, client);
        }
        return cachedClient.client();
    }

    public OpenAiSearchClient(OpenAiImageSearchProperties properties) {
        properties.requireApiKey();
        this.properties = properties;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    }

    public JsonNode post(String path, Object body, Duration timeout) {
        if (timeout == null || timeout.isNegative() || timeout.isZero()) {
            throw new IllegalArgumentException("외부 호출 제한 시간은 양수여야 합니다");
        }
        RestClient client = clientFor(timeout);
        try {
            JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.isObject()) {
                throw new OpenAiCallException("INVALID_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw httpFailure(exception);
        } catch (RestClientException exception) {
            throw new OpenAiCallException("NETWORK_OR_RESPONSE_ERROR", RetryDisposition.UNKNOWN_OUTCOME, null);
        }
    }

    private OpenAiCallException httpFailure(RestClientResponseException exception) {
        int status = exception.getStatusCode().value();
        RetryDisposition disposition = RetryDisposition.UNKNOWN_OUTCOME;
        if (status == 429) {
            try {
                JsonNode error = mapper.readTree(exception.getResponseBodyAsByteArray()).path("error");
                String type = error.path("type").asText();
                String code = error.path("code").asText();
                if ("insufficient_quota".equals(type) || "insufficient_quota".equals(code)) {
                    disposition = RetryDisposition.PERMANENT;
                } else if ("rate_limit_exceeded".equals(code)
                    || ("rate_limit_error".equals(type) && "slow_down".equals(code))) {
                    disposition = RetryDisposition.SAFE_TO_RETRY;
                }
            } catch (java.io.IOException | RuntimeException ignored) {
                // 판별할 수 없는 응답은 미처리가 보장되지 않으므로 재호출하지 않는다.
            }
        } else if (status >= 400 && status < 500 && status != 408) {
            disposition = RetryDisposition.PERMANENT;
        }
        Instant retryNotBefore = disposition == RetryDisposition.SAFE_TO_RETRY
            ? retryNotBefore(exception) : null;
        return new OpenAiCallException("HTTP_" + status, disposition, retryNotBefore);
    }

    private Instant retryNotBefore(RestClientResponseException exception) {
        String header = exception.getResponseHeaders() == null ? null
            : exception.getResponseHeaders().getFirst("Retry-After");
        if (header == null) {
            return null;
        }
        try {
            String value = header.trim();
            if (value.matches("[0-9]+")) {
                return Instant.now().plusSeconds(Long.parseLong(value));
            }
            return ZonedDateTime.parse(value, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
        } catch (RuntimeException ignored) {
            return null; // 잘못된 헤더는 워커의 기본 지수 대기를 사용한다.
        }
    }

}
