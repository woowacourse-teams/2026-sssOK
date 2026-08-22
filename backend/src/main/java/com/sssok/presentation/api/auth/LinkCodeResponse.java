package com.sssok.presentation.api.auth;

import com.sssok.application.auth.LinkCodeResult;
import java.time.Instant;

public record LinkCodeResponse(String linkCode, Instant expiresAt) {

    public static LinkCodeResponse from(LinkCodeResult result) {
        return new LinkCodeResponse(result.linkCode(), result.expiresAt());
    }
}
