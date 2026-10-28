package com.sssok.renewal.application.notification.subscription;

public record RegisterDeviceTokenCommand(Long memberId, String token, String platform) {
}
