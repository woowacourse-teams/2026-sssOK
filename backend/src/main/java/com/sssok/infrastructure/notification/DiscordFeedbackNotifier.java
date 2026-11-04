package com.sssok.infrastructure.notification;

import static com.sssok.infrastructure.notification.DiscordWebhookClient.KST_FORMAT;
import static com.sssok.infrastructure.notification.DiscordWebhookClient.escapeMarkdown;
import static com.sssok.infrastructure.notification.DiscordWebhookClient.orUnknown;

import com.sssok.application.port.out.FeedbackNotifierPort;
import com.sssok.domain.feedback.Feedback;
import com.sssok.infrastructure.config.DiscordNotificationProperties;
import com.sssok.infrastructure.notification.DiscordWebhookClient.Embed;
import com.sssok.infrastructure.notification.DiscordWebhookClient.Field;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 새 의견을 디스코드 웹훅으로 보낸다. 방 이름·닉네임·시각을 필드로 나눠 읽기 쉽게 Embed 로 싣는다.
// 긴 본문은 앞부분만 싣고, 전문은 관리자 조회로 보게 한다.
@Slf4j
@Component
@EnableConfigurationProperties(DiscordNotificationProperties.class)
public class DiscordFeedbackNotifier implements FeedbackNotifierPort {

    // 채널에서 한눈에 읽히는 정도. 의견 본문 상한(1000자)을 그대로 실으면 채널이 금방 밀린다.
    private static final int CONTENT_PREVIEW_LENGTH = 300;
    // 관리자 화면이 생기기 전까지는 단건 조회 API 경로를 싣는다.
    private static final String ADMIN_FEEDBACK_PATH = "/api/v1/admin/feedbacks/";
    // 디스코드 블러플(#5865F2)
    private static final int EMBED_COLOR = 0x5865F2;

    private final DiscordWebhookClient webhookClient;

    public DiscordFeedbackNotifier(DiscordNotificationProperties properties,
                                   RestClient.Builder restClientBuilder) {
        this.webhookClient = new DiscordWebhookClient(properties, restClientBuilder);
        if (!webhookClient.isConfigured()) {
            log.info("디스코드 의견 알림 웹훅이 설정되지 않아 새 의견·신고 알림을 보내지 않습니다.");
        }
    }

    @Override
    public void notifyCreated(Feedback feedback) {
        webhookClient.send(embedOf(feedback), "의견", "feedbackId=" + feedback.getId());
    }

    private Embed embedOf(Feedback feedback) {
        return new Embed(
            "새 의견 #" + feedback.getId(),
            escapeMarkdown(preview(feedback.getContent().value())),
            EMBED_COLOR,
            List.of(
                new Field("방", escapeMarkdown(orUnknown(feedback.getRoomName())), true),
                new Field("닉네임", escapeMarkdown(orUnknown(feedback.getNickname())), true),
                new Field("작성 시각", KST_FORMAT.format(feedback.getCreatedAt()) + " (KST)", true),
                new Field("관리자 조회", "`GET " + ADMIN_FEEDBACK_PATH + feedback.getId() + "`", false)
            ),
            feedback.getCreatedAt().toString()
        );
    }

    // 코드 포인트 단위로 자른다. char 단위로 자르면 이모지 같은 서로게이트 쌍이 반으로 갈린다.
    private static String preview(String content) {
        if (content.codePointCount(0, content.length()) <= CONTENT_PREVIEW_LENGTH) {
            return content;
        }
        int end = content.offsetByCodePoints(0, CONTENT_PREVIEW_LENGTH);
        return content.substring(0, end) + "…";
    }
}
