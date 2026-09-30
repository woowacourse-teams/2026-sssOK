package com.sssok.infrastructure.notification;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sssok.application.port.out.FeedbackNotifierPort;
import com.sssok.domain.feedback.Feedback;
import com.sssok.infrastructure.config.DiscordNotificationProperties;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 새 의견을 디스코드 웹훅으로 보낸다. 방 이름·닉네임·시각을 필드로 나눠 읽기 쉽게 Embed 로 싣는다.
//
// 사용자가 쓴 글이 팀 채널에 그대로 뜨므로 두 가지를 막는다.
// - 멘션: 본문에 @everyone 이 있어도 아무도 호출되지 않게 allowed_mentions 를 비운다.
// - 마크다운: 링크·서식이 해석되지 않게 특수 문자를 이스케이프한다.
// 긴 본문은 앞부분만 싣고, 전문은 관리자 조회로 보게 한다.
@Slf4j
@Component
@EnableConfigurationProperties(DiscordNotificationProperties.class)
public class DiscordFeedbackNotifier implements FeedbackNotifierPort {

    // 채널에서 한눈에 읽히는 정도. 의견 본문 상한(1000자)을 그대로 실으면 채널이 금방 밀린다.
    private static final int CONTENT_PREVIEW_LENGTH = 300;
    private static final Pattern MARKDOWN_SPECIAL = Pattern.compile("([\\\\*_~`|>\\[\\]()#-])");
    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter CREATED_AT_FORMAT =
        DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(KST);
    // 관리자 화면이 생기기 전까지는 단건 조회 API 경로를 싣는다.
    private static final String ADMIN_FEEDBACK_PATH = "/api/v1/admin/feedbacks/";
    // 디스코드 블러플(#5865F2)
    private static final int EMBED_COLOR = 0x5865F2;

    private final DiscordNotificationProperties properties;
    private final RestClient restClient;

    public DiscordFeedbackNotifier(DiscordNotificationProperties properties,
                                   RestClient.Builder restClientBuilder) {
        this.properties = properties;
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        // 본문을 모아 Content-Length 를 붙여 보낸다. 그냥 두면 chunked 로 흘려보내는데, 웹훅 수신 측이
        // 길이 헤더만 보고 본문을 읽으면 빈 요청으로 받는다.
        this.restClient = restClientBuilder
            .requestFactory(new BufferingClientHttpRequestFactory(requestFactory))
            .build();
        if (!properties.hasFeedbackWebhook()) {
            log.info("디스코드 의견 알림 웹훅이 설정되지 않아 새 의견 알림을 보내지 않습니다.");
        }
    }

    @Override
    public void notifyCreated(Feedback feedback) {
        if (!properties.hasFeedbackWebhook()) {
            log.info("디스코드 웹훅이 설정되지 않아 의견 알림을 건너뜁니다. feedbackId={}", feedback.getId());
            return;
        }
        try {
            restClient.post()
                .uri(properties.feedbackWebhookUrl())
                .contentType(MediaType.APPLICATION_JSON)
                .body(payloadOf(feedback))
                .retrieve()
                .toBodilessEntity();
        } catch (RuntimeException e) {
            // 의견은 이미 저장돼 있다. 다시 보내지 않고 로그만 남겨 관리자 조회로 확인하게 한다.
            log.warn("디스코드 의견 알림 전송에 실패했습니다. feedbackId={}, reason={}",
                feedback.getId(), e.getMessage());
        }
    }

    private WebhookPayload payloadOf(Feedback feedback) {
        Embed embed = new Embed(
            "새 의견 #" + feedback.getId(),
            escapeMarkdown(preview(feedback.getContent().value())),
            EMBED_COLOR,
            List.of(
                new Field("방", escapeMarkdown(orUnknown(feedback.getRoomName())), true),
                new Field("닉네임", escapeMarkdown(orUnknown(feedback.getNickname())), true),
                new Field("작성 시각", CREATED_AT_FORMAT.format(feedback.getCreatedAt()) + " (KST)", true),
                new Field("관리자 조회", "`GET " + ADMIN_FEEDBACK_PATH + feedback.getId() + "`", false)
            ),
            feedback.getCreatedAt().toString()
        );
        return new WebhookPayload(List.of(embed), new AllowedMentions(List.of()));
    }

    // 코드 포인트 단위로 자른다. char 단위로 자르면 이모지 같은 서로게이트 쌍이 반으로 갈린다.
    private static String preview(String content) {
        if (content.codePointCount(0, content.length()) <= CONTENT_PREVIEW_LENGTH) {
            return content;
        }
        int end = content.offsetByCodePoints(0, CONTENT_PREVIEW_LENGTH);
        return content.substring(0, end) + "…";
    }

    private static String escapeMarkdown(String text) {
        return MARKDOWN_SPECIAL.matcher(text).replaceAll("\\\\$1");
    }

    private static String orUnknown(String value) {
        return value == null || value.isBlank() ? "알 수 없음" : value;
    }

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
