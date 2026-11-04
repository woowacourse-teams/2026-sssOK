package com.sssok.infrastructure.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sssok.infrastructure.config.DiscordNotificationProperties;
import io.micrometer.common.KeyValue;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.client.observation.ClientRequestObservationContext;
import org.springframework.http.client.observation.DefaultClientRequestObservationConvention;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

// 알림은 부가 기능이라 실패를 예외로 올리지 않고 로그만 남긴다.
@Slf4j
class DiscordWebhookClient {

    static final ZoneId KST = ZoneId.of("Asia/Seoul");
    static final DateTimeFormatter KST_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(KST);
    private static final Pattern MARKDOWN_SPECIAL = Pattern.compile("([\\\\*_~`|>\\[\\]()#-])");

    private final DiscordNotificationProperties properties;
    private final RestClient restClient;

    DiscordWebhookClient(DiscordNotificationProperties properties, RestClient.Builder restClientBuilder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // 본문을 모아 Content-Length 를 붙여 보낸다. 그냥 두면 chunked 로 흘려보내는데, 웹훅 수신 측이
        // 길이 헤더만 보고 본문을 읽으면 빈 요청으로 받는다.
        this.restClient = restClientBuilder
            .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
            .observationConvention(new WebhookUrlMaskingConvention())
            .build();
    }

    boolean isConfigured() {
        return properties.hasFeedbackWebhook();
    }

    void send(Embed embed, String kind, String target) {
        if (!isConfigured()) {
            log.info("디스코드 웹훅이 설정되지 않아 {} 알림을 건너뜁니다. {}", kind, target);
            return;
        }
        try {
            restClient.post()
                .uri(properties.feedbackWebhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(new WebhookPayload(List.of(embed), new AllowedMentions(List.of())))
                .retrieve()
                .toBodilessEntity();
        } catch (RestClientResponseException e) {
            // 다시 보내지 않는다. 재시도하면 중복 알림을 함께 다뤄야 한다.
            log.warn("디스코드 {} 알림 전송에 실패했습니다. {}, status={}",
                kind, target, e.getStatusCode().value());
        } catch (RuntimeException e) {
            // 예외 메시지는 남기지 않는다. 연결 실패 메시지에는 요청 URL 이 그대로 들어가는데,
            // 웹훅 URL 은 그 자체로 채널에 글을 쓸 수 있는 비밀값이다. 원인은 타입으로만 가린다.
            log.warn("디스코드 {} 알림 전송에 실패했습니다. {}, error={}",
                kind, target, e.getClass().getSimpleName());
        }
    }

    // 사용자가 쓴 글이 채널에 그대로 뜨므로 링크·서식이 해석되지 않게 특수 문자를 이스케이프한다.
    static String escapeMarkdown(String text) {
        return MARKDOWN_SPECIAL.matcher(text).replaceAll("\\\\$1");
    }

    static String orUnknown(String value) {
        return value == null || value.isBlank() ? "알 수 없음" : value;
    }

    // 스프링이 남기는 HTTP 클라이언트 메트릭(http.client.requests)은 요청 경로를 uri 태그로 싣는다.
    // 웹훅 URL 은 경로 끝이 토큰이라 그대로 두면 Prometheus·Grafana 에 비밀값이 쌓인다.
    // 경로가 드러나는 두 키만 고정값으로 바꾸고, 호스트·상태 코드 같은 나머지 태그는 그대로 둔다.
    static class WebhookUrlMaskingConvention extends DefaultClientRequestObservationConvention {

        static final String MASKED_URI = "/discord-webhook";

        @Override
        protected KeyValue uri(ClientRequestObservationContext context) {
            return KeyValue.of(super.uri(context).getKey(), MASKED_URI);
        }

        @Override
        protected KeyValue requestUri(ClientRequestObservationContext context) {
            return KeyValue.of(super.requestUri(context).getKey(), MASKED_URI);
        }
    }

    // 본문에 @everyone 이 있어도 아무도 호출되지 않게 allowed_mentions 를 비워 보낸다.
    record WebhookPayload(
        List<Embed> embeds,
        @JsonProperty("allowed_mentions") AllowedMentions allowedMentions
    ) {
    }

    record Embed(String title, String description, int color, List<Field> fields, String timestamp) {
    }

    record Field(String name, String value, boolean inline) {
    }

    record AllowedMentions(List<String> parse) {
    }
}
