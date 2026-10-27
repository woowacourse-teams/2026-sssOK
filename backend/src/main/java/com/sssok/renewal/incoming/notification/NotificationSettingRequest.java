package com.sssok.renewal.incoming.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

public record NotificationSettingRequest(
    @Schema(description = "이 방 알림을 받을지 여부")
    @NotNull(message = "알림 설정값(enabled)을 보내주세요") Boolean enabled
) {
}
