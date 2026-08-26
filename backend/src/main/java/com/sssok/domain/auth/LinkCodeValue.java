package com.sssok.domain.auth;

import com.sssok.domain.auth.exception.InvalidLinkCodeException;
import java.util.random.RandomGenerator;

// 다른 기기로 이어받을 때 입력하는 1회용 코드. 사람이 직접 타이핑하는 흐름을 지원하기 위해
// 6자리 숫자로 고정한다(짧은 유효시간 + 발급 시 이전 코드 무효화로 노출 위험을 상쇄한다).
public record LinkCodeValue(String value) {

    private static final int LENGTH = 6;
    private static final int UPPER_BOUND = 1_000_000;

    public LinkCodeValue {
        if (value == null || !value.matches("\\d{" + LENGTH + "}")) {
            throw new InvalidLinkCodeException(value);
        }
    }

    public static LinkCodeValue generate(RandomGenerator random) {
        int number = random.nextInt(UPPER_BOUND);
        return new LinkCodeValue("%06d".formatted(number));
    }
}
