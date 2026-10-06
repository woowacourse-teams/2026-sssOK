package com.sssok.infrastructure.ai;

import com.fasterxml.jackson.databind.JsonNode;
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
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(timeout);
        RestClient client = RestClient.builder().baseUrl(properties.baseUrl().toString())
            .requestFactory(factory).defaultHeader("Authorization", "Bearer " + properties.apiKey()).build();
        try {
            JsonNode response = client.post().uri(path).contentType(MediaType.APPLICATION_JSON)
                .body(body).retrieve().body(JsonNode.class);
            if (response == null || !response.isObject()) {
                throw new OpenAiCallException("INVALID_RESPONSE");
            }
            return response;
        } catch (RestClientResponseException exception) {
            throw new OpenAiCallException("HTTP_" + exception.getStatusCode().value());
        } catch (RestClientException exception) {
            throw new OpenAiCallException("NETWORK_OR_RESPONSE_ERROR");
        }
    }
}
