package com.sssok.renewal.incoming.auth;

public record RefreshRequest(String refreshToken) {
    @Override
    public String toString() { return "RefreshRequest[REDACTED]"; }
}
