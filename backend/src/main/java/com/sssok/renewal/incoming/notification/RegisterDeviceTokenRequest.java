package com.sssok.renewal.incoming.notification;

import com.sssok.renewal.application.notification.subscription.RegisterDeviceTokenCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record RegisterDeviceTokenRequest(
    @Schema(description = "FCM 등록 토큰")
    @NotBlank(message = "token 값을 보내주세요") String token,
    @Schema(description = "기기 종류", allowableValues = {"ios", "android"})
    @NotBlank(message = "platform 값을 보내주세요") String platform
) {
    public RegisterDeviceTokenCommand toCommand(Long memberId) {
        return new RegisterDeviceTokenCommand(memberId, token, platform);
    }
}
