package com.sssok.renewal.application.auth;

import java.time.Instant;

public record LinkCodeResult(String linkCode, Instant expiresAt) {
}
