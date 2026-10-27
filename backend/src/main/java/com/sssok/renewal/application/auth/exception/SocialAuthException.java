package com.sssok.renewal.application.auth.exception;

import com.sssok.common.exception.ErrorCode;
import com.sssok.common.exception.SssOkException;

public class SocialAuthException extends SssOkException {
    private SocialAuthException(ErrorCode code) { super(code); }

    public static SocialAuthException invalid() {
        return new SocialAuthException(ErrorCode.INVALID_SOCIAL_AUTH);
    }

    public static SocialAuthException conflict() {
        return new SocialAuthException(ErrorCode.SOCIAL_AUTH_CONFLICT);
    }

    public static SocialAuthException reauthenticationRequired() {
        return new SocialAuthException(ErrorCode.SOCIAL_REAUTHENTICATION_REQUIRED);
    }

    public static SocialAuthException unavailable() {
        return new SocialAuthException(ErrorCode.SOCIAL_AUTH_UNAVAILABLE);
    }
}
