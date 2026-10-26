package com.sssok.renewal.outgoing.oauth;

import org.springframework.http.ResponseEntity;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.application.auth.exception.SocialAuthException;
import java.io.IOException;
import java.util.function.Supplier;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 인가 코드는 일회용이므로 타임아웃·실패 시 자동 재시도하지 않는다. 응답 원문과 토큰은 예외에 담지 않는다.
public class OAuthHttpClient {
    private final RestClient restClient;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public OAuthHttpClient(RestClient restClient) {
        this.restClient = restClient;
    }

    public JsonNode postForm(String url, MultiValueMap<String, String> form) {
        return executeJsonRequest(() -> restClient.post()
            .uri(url)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED)
            .body(form)
            .retrieve()
            .body(JsonNode.class));
    }

    public JsonNode getJson(String url, String accessToken) {
        return executeJsonRequest(() -> restClient.get()
            .uri(url)
            .headers(headers -> {
                if (accessToken != null) {
                    headers.setBearerAuth(accessToken);
                }
            })
            .retrieve()
            .body(JsonNode.class));
    }

    public JsonNode postAdminUnlink(String url, String adminKey, MultiValueMap<String, String> form) {
        return executeJsonRequest(() -> {
            ResponseEntity<JsonNode> response = restClient.post().uri(url)
                .header("Authorization", "KakaoAK " + adminKey)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve().toEntity(JsonNode.class);
            JsonNode body = response.getBody();
            if (response.getStatusCode().value() != 200 || body == null || !body.isObject()) {
                throw SocialAuthException.unavailable();
            }
            return body;
        });
    }

    public void postRevocation(String url, MultiValueMap<String, String> form) {
        executeUnlinkRequest(() -> {
            ResponseEntity<Void> response = restClient.post().uri(url)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                .retrieve().toBodilessEntity();
            if (response.getStatusCode().value() != 200) {
                throw SocialAuthException.unavailable();
            }
            return null;
        });
    }

    private <T> T executeUnlinkRequest(Supplier<T> request) {
        try {
            return request.get();
        } catch (RestClientResponseException e) {
            if (isInvalidGrantResponse(e) || isInvalidAccessTokenResponse(e)) {
                throw SocialAuthException.reauthenticationRequired();
            }
            throw SocialAuthException.unavailable();
        } catch (RestClientException e) {
            throw SocialAuthException.unavailable();
        }
    }

    private boolean isInvalidAccessTokenResponse(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 401) {
            return false;
        }
        try {
            JsonNode body = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            return body != null && body.path("code").isIntegralNumber()
                && body.path("code").canConvertToInt() && body.path("code").asInt() == -401;
        } catch (IOException ignored) {
            return false;
        }
    }

    private JsonNode executeJsonRequest(Supplier<JsonNode> request) {
        try {
            JsonNode response = request.get();
            if (response == null || !response.isObject()) {
                throw SocialAuthException.unavailable();
            }
            return response;
        } catch (RestClientResponseException e) {
            if (isInvalidGrantResponse(e)) {
                throw SocialAuthException.invalid();
            }
            throw SocialAuthException.unavailable();
        } catch (RestClientException e) {
            throw SocialAuthException.unavailable();
        }
    }

    private boolean isInvalidGrantResponse(RestClientResponseException exception) {
        if (exception.getStatusCode().value() != 400) {
            return false;
        }
        try {
            JsonNode errorResponse = objectMapper.readTree(exception.getResponseBodyAsByteArray());
            return errorResponse != null && "invalid_grant".equals(errorResponse.path("error").asText());
        } catch (IOException ignored) {
            // 제공자 응답 원문을 노출하지 않는다.
            return false;
        }
    }

    public static String readRequiredText(JsonNode response, String fieldName) {
        JsonNode value = response.path(fieldName);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw SocialAuthException.unavailable();
        }
        return value.asText();
    }
}
