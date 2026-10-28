package com.sssok.renewal.outgoing.push;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sssok.renewal.domain.notification.DeviceToken;
import com.sssok.renewal.domain.notification.RoomNotification;
import com.sssok.renewal.outgoing.config.FcmProperties;
import java.net.http.HttpClient;
import java.time.Clock;
import java.util.Map;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// FCM HTTP v1 API 로 네이티브 앱에 알림을 보낸다. iOS 는 FCM 이 APNs 로 넘겨준다.
// 기기 토큰은 그 기기로 알림을 보낼 수 있는 비밀이라 로그·메트릭에 남기지 않는다.
@Slf4j
@Component
public class FcmClient {
    // UNREGISTERED: 앱이 지워졌거나 토큰이 바뀌었다. SENDER_ID_MISMATCH: 다른 Firebase 프로젝트의 토큰이다
    // (예: 다른 환경용 앱 빌드가 등록한 토큰). 둘 다 이 서버에서는 다시 보내도 소용없다.
    private static final Set<String> EXPIRED_ERROR_CODES = Set.of("UNREGISTERED", "SENDER_ID_MISMATCH");
    private static final String THIRD_PARTY_AUTH_ERROR = "THIRD_PARTY_AUTH_ERROR";

    private final FcmProperties properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final RestClient restClient;
    private final FcmAccessTokenProvider tokenProvider;
    private final String sendUri;

    @Autowired
    public FcmClient(FcmProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    FcmClient(FcmProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.clock = clock;
        // HttpURLConnection 은 길이를 정해 보낸 요청이 401 을 받으면 응답 본문을 버려서
        // APNs 인증 실패(THIRD_PARTY_AUTH_ERROR)를 구분할 수 없다. JDK HttpClient 는 본문을 그대로 준다.
        HttpClient httpClient = HttpClient.newBuilder()
            .version(HttpClient.Version.HTTP_1_1)
            .connectTimeout(properties.connectTimeout())
            .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        if (properties.enabled()) {
            FcmServiceAccount account = FcmServiceAccount.parse(properties.serviceAccount(), objectMapper);
            this.tokenProvider = new FcmAccessTokenProvider(account, restClient, clock);
            this.sendUri = properties.apiBaseUrl() + "/v1/projects/" + account.projectId() + "/messages:send";
        } else {
            this.tokenProvider = null;
            this.sendUri = null;
            log.info("FCM 서비스 계정 키가 설정되지 않아 앱 알림 발송을 건너뜁니다");
        }
    }

    public boolean enabled() {
        return tokenProvider != null;
    }

    public PushSendResult send(DeviceToken deviceToken, RoomNotification notification) {
        if (tokenProvider == null) {
            return PushSendResult.SKIPPED;
        }
        // 길이를 아는 본문으로 보내야 chunked 전송 없이 Content-Length 가 붙는다.
        byte[] body = body(deviceToken.getToken(), notification);
        try {
            restClient.post()
                .uri(sendUri)
                .header("Authorization", "Bearer " + tokenProvider.accessToken())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return PushSendResult.SENT;
        } catch (FcmAuthenticationException e) {
            return PushSendResult.FAILED;
        } catch (RestClientResponseException e) {
            return failed(deviceToken, e);
        } catch (ResourceAccessException e) {
            String cause = e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName();
            log.warn("FCM 발송 실패(연결). memberId={}, cause={}", deviceToken.getMemberId(), cause);
            return PushSendResult.FAILED;
        } catch (RestClientException e) {
            log.warn("FCM 발송 실패. memberId={}, error={}", deviceToken.getMemberId(), e.getClass().getSimpleName());
            return PushSendResult.FAILED;
        }
    }

    private byte[] body(String token, RoomNotification notification) {
        try {
            return objectMapper.writeValueAsBytes(Map.of("message", message(token, notification)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("FCM 메시지를 만들지 못했습니다", e);
        }
    }

    // 알림 문구는 notification 에, 앱이 방으로 이동하는 데 쓸 값은 data 에 담는다. data 값은 문자열만 된다.
    private Map<String, Object> message(String token, RoomNotification notification) {
        long ttlSeconds = properties.ttl().toSeconds();
        return Map.of(
            "token", token,
            "notification", Map.of("title", notification.title(), "body", notification.body()),
            "data", Map.of(
                "type", notification.type().name(),
                "roomId", String.valueOf(notification.roomId()),
                "roomCode", notification.roomCode(),
                "url", notification.url()),
            "android", Map.of("ttl", ttlSeconds + "s"),
            "apns", Map.of(
                "headers", Map.of("apns-expiration",
                    String.valueOf(clock.instant().plusSeconds(ttlSeconds).getEpochSecond())),
                "payload", Map.of("aps", Map.of("sound", "default"))));
    }

    private PushSendResult failed(DeviceToken deviceToken, RestClientResponseException e) {
        int status = e.getStatusCode().value();
        String errorCode = errorCode(e.getResponseBodyAsString());
        if (errorCode != null && EXPIRED_ERROR_CODES.contains(errorCode)) {
            return PushSendResult.EXPIRED;
        }
        // APNs 인증 정보가 틀려도 401 이 오지만 Google 액세스 토큰은 멀쩡하니 버리지 않는다.
        if (status == 401 && !THIRD_PARTY_AUTH_ERROR.equals(errorCode)) {
            tokenProvider.invalidate();
        }
        log.warn("FCM 발송 실패. memberId={}, status={}, errorCode={}", deviceToken.getMemberId(), status, errorCode);
        return PushSendResult.FAILED;
    }

    private String errorCode(String body) {
        try {
            for (JsonNode detail : objectMapper.readTree(body).path("error").path("details")) {
                if (detail.hasNonNull("errorCode")) {
                    return detail.get("errorCode").asText();
                }
            }
        } catch (JsonProcessingException e) {
            // 응답이 JSON 이 아니면 상태 코드만으로 판단한다.
        }
        return null;
    }
}
