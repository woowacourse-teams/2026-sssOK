package com.sssok.renewal.domain.auth;

public enum RefreshTokenDelivery {
    COOKIE, BODY;

    public static RefreshTokenDelivery orDefault(RefreshTokenDelivery delivery) {
        return delivery == null ? COOKIE : delivery;
    }
}
