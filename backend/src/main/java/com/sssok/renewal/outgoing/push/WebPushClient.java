package com.sssok.renewal.outgoing.push;

import com.sssok.renewal.domain.notification.PushSubscription;
import com.sssok.renewal.outgoing.config.WebPushProperties;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// endpoint 는 구독을 가리키는 비밀이라 로그·메트릭에 남기지 않는다.
// 그래서 URI 를 메트릭 태그로 남기는 공용 RestClient.Builder 대신 직접 만든 클라이언트를 쓴다.
@Slf4j
@Component
public class WebPushClient {
    private final WebPushProperties properties;
    private final WebPushEncryptor encryptor = new WebPushEncryptor();
    private final VapidSigner signer;
    private final RestClient restClient;

    public WebPushClient(WebPushProperties properties) {
        this.properties = properties;
        this.signer = properties.enabled()
            ? new VapidSigner(properties.vapidPublicKey(), properties.vapidPrivateKey(), properties.subject(),
                Clock.systemUTC())
            : null;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        this.restClient = RestClient.builder().requestFactory(requestFactory).build();
        if (signer == null) {
            log.info("VAPID 키가 설정되지 않아 Web Push 발송을 건너뜁니다");
        }
    }

    public boolean enabled() {
        return signer != null;
    }

    public String publicKey() {
        return properties.vapidPublicKey();
    }

    public PushSendResult send(PushSubscription subscription, String payload) {
        if (signer == null) {
            return PushSendResult.SKIPPED;
        }
        byte[] body;
        try {
            body = encryptor.encrypt(payload.getBytes(StandardCharsets.UTF_8),
                subscription.p256dhBytes(), subscription.authBytes());
        } catch (InvalidSubscriptionKeyException e) {
            return PushSendResult.EXPIRED;
        }
        try {
            restClient.post()
                .uri(URI.create(subscription.getEndpoint()))
                .header("Authorization", signer.authorization(subscription.getEndpoint()))
                .header("TTL", String.valueOf(properties.ttl().toSeconds()))
                .header("Content-Encoding", "aes128gcm")
                .header("Urgency", "normal")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(body)
                .retrieve()
                .toBodilessEntity();
            return PushSendResult.SENT;
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 404 || status == 410) {
                return PushSendResult.EXPIRED;
            }
            log.warn("Web Push 발송 실패. memberId={}, status={}", subscription.getMemberId(), status);
            return PushSendResult.FAILED;
        } catch (ResourceAccessException e) {
            // 예외 메시지에 endpoint 가 들어 있어 원인 타입만 남긴다.
            String cause = e.getCause() == null ? e.getClass().getSimpleName() : e.getCause().getClass().getSimpleName();
            log.warn("Web Push 발송 실패(연결). memberId={}, cause={}", subscription.getMemberId(), cause);
            return PushSendResult.FAILED;
        } catch (RestClientException e) {
            log.warn("Web Push 발송 실패. memberId={}, error={}", subscription.getMemberId(), e.getClass().getSimpleName());
            return PushSendResult.FAILED;
        }
    }
}
