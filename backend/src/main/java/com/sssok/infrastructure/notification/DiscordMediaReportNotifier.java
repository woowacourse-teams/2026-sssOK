package com.sssok.infrastructure.notification;

import static com.sssok.infrastructure.notification.DiscordWebhookClient.KST_FORMAT;
import static com.sssok.infrastructure.notification.DiscordWebhookClient.escapeMarkdown;
import static com.sssok.infrastructure.notification.DiscordWebhookClient.orUnknown;

import com.sssok.infrastructure.config.DiscordNotificationProperties;
import com.sssok.infrastructure.notification.DiscordWebhookClient.Embed;
import com.sssok.infrastructure.notification.DiscordWebhookClient.Field;
import com.sssok.renewal.application.report.MediaReportNotifier;
import com.sssok.renewal.domain.report.MediaReport;
import com.sssok.renewal.domain.report.MediaReportReason;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// 사진 URL 은 싣지 않는다 — 서명 URL 이 채널 기록에 남으면 만료 전까지 누구나 열 수 있다.
@Component
public class DiscordMediaReportNotifier implements MediaReportNotifier {

    private static final String ADMIN_MEDIA_REPORT_PATH = "/api/v1/admin/media-reports/";
    private static final int EMBED_COLOR = 0xED4245;

    private final DiscordWebhookClient webhookClient;

    public DiscordMediaReportNotifier(DiscordNotificationProperties properties,
                                      RestClient.Builder restClientBuilder) {
        this.webhookClient = new DiscordWebhookClient(properties, restClientBuilder);
    }

    @Override
    public void notifyReported(MediaReport report) {
        webhookClient.send(embedOf(report), "신고", "reportId=" + report.getId());
    }

    private Embed embedOf(MediaReport report) {
        return new Embed(
            "새 사진 신고 #" + report.getId(),
            labelOf(report.getReason()),
            EMBED_COLOR,
            List.of(
                new Field("방", escapeMarkdown(orUnknown(report.getRoomName())) + " (roomId=" + report.getRoomId() + ")", true),
                new Field("신고자", escapeMarkdown(orUnknown(report.getNickname())), true),
                new Field("미디어 ID", String.valueOf(report.getMediaId()), true),
                new Field("접수 시각", KST_FORMAT.format(report.getReportedAt()) + " (KST)", true),
                new Field("관리자 조회", "`GET " + ADMIN_MEDIA_REPORT_PATH + report.getId() + "`", false)
            ),
            report.getReportedAt().toString()
        );
    }

    private static String labelOf(MediaReportReason reason) {
        return switch (reason) {
            case INAPPROPRIATE_CONTENT -> "선정적·폭력적 콘텐츠";
            case PRIVACY_VIOLATION -> "개인정보·초상권 침해";
            case COPYRIGHT_VIOLATION -> "저작권 침해";
            case HARASSMENT -> "괴롭힘·혐오 표현";
        };
    }
}
