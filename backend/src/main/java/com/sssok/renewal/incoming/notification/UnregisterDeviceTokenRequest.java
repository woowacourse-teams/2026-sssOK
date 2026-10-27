package com.sssok.renewal.incoming.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UnregisterDeviceTokenRequest(
    @Schema(description = "해제할 FCM 등록 토큰") @NotBlank(message = "token 값을 보내주세요") String token
) {
}
