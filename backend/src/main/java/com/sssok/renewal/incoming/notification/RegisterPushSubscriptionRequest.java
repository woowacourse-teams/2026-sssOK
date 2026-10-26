package com.sssok.renewal.incoming.notification;

import com.sssok.renewal.application.notification.subscription.RegisterPushSubscriptionCommand;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

// 브라우저 PushSubscription.toJSON() 결과를 그대로 받는다. expirationTime 등 다른 필드는 무시한다.
public record RegisterPushSubscriptionRequest(
    @Schema(description = "푸시 서비스 구독 주소")
    @NotBlank(message = "endpoint 값을 보내주세요") String endpoint,
    @Valid @NotNull(message = "keys 값을 보내주세요") Keys keys
) {
    public record Keys(
        @Schema(description = "브라우저 공개키(base64url)")
        @NotBlank(message = "p256dh 값을 보내주세요") String p256dh,
        @Schema(description = "인증 비밀값(base64url)")
        @NotBlank(message = "auth 값을 보내주세요") String auth
    ) {
    }

    public RegisterPushSubscriptionCommand toCommand(Long memberId) {
        return new RegisterPushSubscriptionCommand(memberId, endpoint, keys.p256dh(), keys.auth());
    }
}
