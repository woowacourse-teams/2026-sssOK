package com.sssok.renewal.application.notification.subscription;

public record RegisterPushSubscriptionCommand(Long memberId, String endpoint, String p256dh, String auth) {
}
