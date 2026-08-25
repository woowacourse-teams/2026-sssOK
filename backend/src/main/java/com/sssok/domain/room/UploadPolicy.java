package com.sssok.domain.room;

import com.sssok.domain.room.exception.InvalidUploadPolicyException;
import java.util.Arrays;
import java.util.Locale;

// 방에서 누가 파일을 올릴 수 있는지 나타내는 권한.
// API 로는 everyone/host 라는 문자열로 주고받는다
public enum UploadPolicy {

    ANYONE("everyone") {
        @Override
        public boolean allows(boolean requesterIsHost) {
            return true;
        }
    },
    HOST_ONLY("host") {
        @Override
        public boolean allows(boolean requesterIsHost) {
            return requesterIsHost;
        }
    };

    private final String apiValue;

    UploadPolicy(String apiValue) {
        this.apiValue = apiValue;
    }

    public static UploadPolicy from(String apiValue) {
        if (apiValue == null) {
            throw new InvalidUploadPolicyException(null);
        }
        String normalized = apiValue.trim().toLowerCase(Locale.ROOT);
        return Arrays.stream(values())
            .filter(policy -> policy.apiValue.equals(normalized))
            .findFirst()
            .orElseThrow(() -> new InvalidUploadPolicyException(apiValue));
    }

    public abstract boolean allows(boolean requesterIsHost);

    public String apiValue() {
        return apiValue;
    }
}
