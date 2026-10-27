package com.sssok.renewal.incoming.notification;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record UnregisterPushSubscriptionRequest(
    @Schema(description = "해제할 구독 주소") @NotBlank(message = "endpoint 값을 보내주세요") String endpoint
) {
}
