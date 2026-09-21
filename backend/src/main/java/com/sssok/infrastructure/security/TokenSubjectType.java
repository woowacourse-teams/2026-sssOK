package com.sssok.infrastructure.security;

import java.util.Arrays;
import java.util.Optional;

// 회원 토큰과 관리자 토큰은 같은 키로 서명하므로 서명 검증만으로는 구분되지 않는다. 주체를 클레임으로
// 담고, 각 파서는 자신에게 허용된 타입인지 직접 확인한다.
public enum TokenSubjectType {

    MEMBER("member"),
    ADMIN("admin");

    public static final String CLAIM_NAME = "typ";

    private final String claimValue;

    TokenSubjectType(String claimValue) {
        this.claimValue = claimValue;
    }

    // 아는 타입이 아니면 빈 값이다. 호출하는 쪽에서 거부한다.
    public static Optional<TokenSubjectType> from(String claimValue) {
        return Arrays.stream(values())
            .filter(subjectType -> subjectType.claimValue.equals(claimValue))
            .findFirst();
    }

    public String claimValue() {
        return claimValue;
    }
}
