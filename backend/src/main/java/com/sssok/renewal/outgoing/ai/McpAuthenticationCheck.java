package com.sssok.renewal.outgoing.ai;

import static java.net.HttpURLConnection.HTTP_FORBIDDEN;
import static java.net.HttpURLConnection.HTTP_UNAUTHORIZED;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

// MCP SDK 0.18.3 은 401 응답을 상태 코드 없는 예외로 바꾸므로, 호출 전 단계가 실패했을 때만 직접 확인한다.
public class McpAuthenticationCheck {
    private final HttpClient http;
    private final URI url;
    private final String token;
    private final Duration timeout;

    public McpAuthenticationCheck(URI url, String token, Duration timeout) {
        this.http = HttpClient.newBuilder().connectTimeout(timeout).build();
        this.url = url;
        this.token = token;
        this.timeout = timeout;
    }

    public boolean rejectsToken() {
        try {
            int status = http.send(probeRequest(), HttpResponse.BodyHandlers.discarding()).statusCode();
            return status == HTTP_UNAUTHORIZED || status == HTTP_FORBIDDEN;
        } catch (IOException failure) {
            return false;
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private HttpRequest probeRequest() {
        return HttpRequest.newBuilder(url)
            .timeout(timeout)
            .header("Authorization", "Bearer " + token)
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    }
}
