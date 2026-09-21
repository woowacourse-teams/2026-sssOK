package com.sssok.presentation.api.feedback;

import com.sssok.domain.feedback.Feedback;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;

// 단건 응답. 본문과 User-Agent 를 자르지 않고 저장된 그대로 내려준다.
public record FeedbackResponse(
    @Schema(description = "의견 ID") Long feedbackId,
    @Schema(description = "의견 전문") String content,
    @Schema(description = "남긴 방의 ID (작성 시점 값)") Long roomId,
    @Schema(description = "남긴 방의 이름 (작성 시점 값)") String roomName,
    @Schema(description = "작성한 회원의 ID (작성 시점 값)") Long memberId,
    @Schema(description = "작성한 회원의 닉네임 (작성 시점 값)") String nickname,
    @Schema(description = "User-Agent 원본. 헤더 없이 들어온 의견은 null") String userAgent,
    @Schema(description = "프론트 버전 태그. 예: fe-v0.1.0. 헤더 없으면 null") String frontendVersion,
    @Schema(description = "작성 시각") Instant createdAt
) {

    public static FeedbackResponse from(Feedback feedback) {
        return new FeedbackResponse(
            feedback.getId(),
            feedback.getContent().value(),
            feedback.getRoomId(),
            feedback.getRoomName(),
            feedback.getMemberId(),
            feedback.getNickname(),
            feedback.getUserAgent().value(),
            feedback.getFrontendVersion().value(),
            feedback.getCreatedAt()
        );
    }
}
